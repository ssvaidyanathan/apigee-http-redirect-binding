package com.apigee.callout;

import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

import org.apache.commons.lang.exception.ExceptionUtils;

import com.apigee.flow.execution.Action;
import com.apigee.flow.execution.ExecutionContext;
import com.apigee.flow.execution.ExecutionResult;
import com.apigee.flow.execution.spi.Execution;
import com.apigee.flow.message.MessageContext;

public class GenerateSAMLRequest implements Execution {

	private Map<String, String> properties;
	private static final String variableReferencePatternString = "(.*?)\\{([^\\{\\} :][^\\{\\} ]*?)\\}(.*?)";
	private static final Pattern variableReferencePattern = Pattern.compile(variableReferencePatternString);

	public GenerateSAMLRequest(Map<String, String> properties) {
		this.properties = properties;
	}
	
	public static void main(String[] args) throws Exception{
		System.out.println(generateDeflatedAndBase64EncodedString("http://sp.example.com/demo1/index.php?acs",
				"http://idp.example.com/SSOService.php",
				"_809707f0030a5d00620c9d9df97f627afe9dcc24", Instant.now().toString(),
				"https://apis.example.com/auth/v1/saml", false));
	}

	/**
	 * execute method
	 */
	public ExecutionResult execute(MessageContext msgCtxt, ExecutionContext exeCtxt) {
		try {
			String assertionConsumerServiceURL = getStringProp(msgCtxt, "AssertionConsumerServiceURL");
			String destination = getStringProp(msgCtxt, "Destination");
			String id = getStringProp(msgCtxt, "ID");
			String issueInstant = Instant.now().toString();//getStringProp(msgCtxt, "IssueInstant");
			String issuer = getStringProp(msgCtxt, "Issuer");
			boolean urlEncode = getBooleanProperty(msgCtxt, "urlencode", false);
			String samlRequest = generateDeflatedAndBase64EncodedString(assertionConsumerServiceURL,
					destination, id, issueInstant, issuer, urlEncode);
			msgCtxt.setVariable("samlRequest", samlRequest);
			return ExecutionResult.SUCCESS;

		} catch (Exception ex) {
			ExecutionResult executionResult = new ExecutionResult(false, Action.ABORT);
			// --Returns custom error message and header
			executionResult.setErrorResponse(ex.getMessage());
			executionResult.addErrorResponseHeader("ExceptionClass", ex.getClass().getName());
			// --Sets a flow variable -- may be useful for debugging.
			msgCtxt.setVariable("JAVA_ERROR", ex.getMessage());
			msgCtxt.setVariable("JAVA_STACKTRACE", ExceptionUtils.getStackTrace(ex));
			return executionResult;
		}
	}

	/**
	 * To generate the payload
	 * 
	 * @param assertionConsumerServiceURL
	 * @param destination
	 * @param id
	 * @param issueInstant
	 * @param issuer
	 * @param urlEncode
	 * @return
	 * @throws Exception
	 */
	private static String generateDeflatedAndBase64EncodedString(String assertionConsumerServiceURL, String destination,
			String id, String issueInstant, String issuer, boolean urlEncode) throws Exception {
		StringBuilder xmlStringFormat = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
				+ "<saml2p:AuthnRequest xmlns:saml2p=\"urn:oasis:names:tc:SAML:2.0:protocol\" "
				+ "AssertionConsumerServiceURL=\"%s\" " + "Destination=\"%s\" " + "ForceAuthn=\"false\" ID=\"%s\" "
				+ "IsPassive=\"false\" IssueInstant=\"%s\" "
				+ "ProtocolBinding=\"urn:oasis:names:tc:SAML:2.0:bindings:HTTP-POST\" " + "Version=\"2.0\">\n"
				+ "   <saml2:Issuer xmlns:saml2=\"urn:oasis:names:tc:SAML:2.0:assertion\" Format=\"urn:oasis:names:tc:SAML:2.0:nameid-format:entity\">%s</saml2:Issuer>\n"
				+ "</saml2p:AuthnRequest>");
		String xmlString = String.format(xmlStringFormat.toString(), assertionConsumerServiceURL, destination, id,
				issueInstant, issuer);
		return compressAndEncode(xmlString, urlEncode);
	}

	/**
	 * To compress and Encode
	 * 
	 * @param data
	 * @param urlEncode
	 * @return
	 * @throws Exception
	 */
	private static String compressAndEncode(String data, boolean urlEncode) throws Exception {
		try (ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
			try (DeflaterOutputStream stream = new DeflaterOutputStream(buffer,
					new Deflater(Deflater.DEFLATED, true))) {
				stream.write(data.getBytes(StandardCharsets.UTF_8));
			}
			byte[] compressed = buffer.toByteArray();
			String base64EncodedMessage = Base64.getEncoder().encodeToString(compressed);
			if (!urlEncode)
				return base64EncodedMessage;
			else
				return URLEncoder.encode(base64EncodedMessage, "UTF-8");
		} catch (Exception e) {
			e.printStackTrace();
			throw e;
		}
	}
	
	/**
	 * If the value of a property contains any pairs of curlies, eg,
	 * {apiproxy.name}, then "resolve" the value by de-referencing the context
	 * variables whose names appear between curlies.
	 * 
	 * @param spec
	 * @param msgCtxt
	 * @return
	 */
	private String resolveVariableReferences(String spec, MessageContext msgCtxt) {
		Matcher matcher = variableReferencePattern.matcher(spec);
		StringBuffer sb = new StringBuffer();
		while (matcher.find()) {
			matcher.appendReplacement(sb, "");
			sb.append(matcher.group(1));
			String ref = matcher.group(2);
			String[] parts = ref.split(":", 2);
			Object v = msgCtxt.getVariable(parts[0]);
			if (v != null) {
				sb.append((String) v);
			} else if (parts.length > 1) {
				sb.append(parts[1]);
			}
			sb.append(matcher.group(3));
		}
		matcher.appendTail(sb);
		return sb.toString();
	}

	/**
	 * 
	 * @param msgCtxt
	 * @param name
	 * @return
	 * @throws Exception
	 */
	private String getStringProp(MessageContext msgCtxt, String name) throws Exception {
		String value = this.properties.get(name);
		if (value != null)
			value = value.trim();
		if (value == null || value.equals("")) {
			throw new IllegalStateException(name + " resolves to null or empty.");
		}
		value = resolveVariableReferences(value, msgCtxt);
		if (value == null || value.equals("")) {
			throw new IllegalStateException(name + " resolves to null or empty.");
		}
		return value;
	}

	
	/**
	 * Return boolean from properties
	 * 
	 * @param msgCtxt
	 * @param propName
	 * @param defaultValue
	 * @return
	 * @throws Exception
	 */
	private boolean getBooleanProperty(MessageContext msgCtxt, String propName, boolean defaultValue) throws Exception {
		String flag = this.properties.get(propName);
		if (flag != null)
			flag = flag.trim();
		if (flag == null || flag.equals("")) {
			return defaultValue;
		}
		flag = resolveVariableReferences(flag, msgCtxt);
		if (flag == null || flag.equals("")) {
			return defaultValue;
		}
		return flag.equalsIgnoreCase("true");
	}

}

# apigee-http-redirect-binding
This is a sample Apigee proxy to generate the [HTTP Redirect binding](https://en.wikipedia.org/wiki/SAML_2.0#HTTP_Redirect_Binding)

## Pre-Requisites

- Java 8 or later
- Maven 3.5 or later

## Steps

### Building the Jar

You do not need to build the Jar in order to use the custom policy. The custom policy is
ready to use, with policy configuration. You need to re-build the jar only if you want
to modify the behavior of the custom policy. Before you do that, be sure you understand
all the configuration options - the policy may be usable for you without modification.

If you do wish to build the jar, you can use
[maven](https://maven.apache.org/download.cgi) to do so. The build requires
JDK8. Before you run the build the first time, you need to download the Apigee
Edge dependencies into your local maven repo.

Preparation, first time only: `./buildsetup.sh`

#### To build: 
- Go to the `callout` directory
- Execute `mvn clean package`. This should create a jar and also create a copy in `../bundle/apiproxy/resources/java`


### Deploy Apigee proxy
- Go to the `bundle` directory
- Execute `mvn clean install -P{profile} -Dorg={org} -Dusername={username} -Dpassword={password}`
- The above command should deploy the proxy as `http-redirect-binding`. 
- The proxy is pointing to httpbin.org/get for now. Please update it as per your needs
- To test, run the following curl
	```
		curl --location --request GET 'https://{org}-{env}.apigee.net/saml/redirect_binding'
	```
- In the response you will see the `SAMLRequest` under `args`


## License

This code is released under the Apache Source License v2.0. For information see the [LICENSE](LICENSE) file.

## Disclaimer

This example is not an official Google product, nor is it part of an official Google product.


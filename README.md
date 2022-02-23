# Lambda on Java enabling mutual TLS

This project provides sample code for various ways, how to implement mutual TLS within your Java based AWS Lambda function.

We will build the following architecture:

![Architecture](doc/Java_Lambda_mTLS_Architecture.png)

## Getting started

Download or clone this repository.

To follow hands-on, install the following prerequisite tools/software:

1. Install Java 11 or higher
2. Install [AWS CLI](https://docs.aws.amazon.com/cli/latest/userguide/getting-started-install.html)
3. Install [AWS CDK v2](https://docs.aws.amazon.com/cdk/latest/guide/getting_started.html)
4. Install [Docker](https://docs.docker.com/get-docker/)

NOTE!

> Run all the following Shell commands in the root directory of this project!


## Create the root CA, client and server certificates

```bash
./scripts/1-create-certificates.sh
```

Above script does the following in an orderly manner:
 - Generates two different root CA private keys for two different backend services 
 - Creates and self signs the root CA public keys
 - Creates the two backend services certificates
 - Creates TrustStore for a Lambda function, copies signed backend services public key to the Lambda functions’ TrustStore
 - Generates Lambda functions’ KeyStores per backend service, signs them, and imports the signed client public key into the Lambda functions’ resources and Lambda layers’ resources. More about it is explained in each approach.
 - Finally, copies the backend and Lambda function certificates to respective modules


## Build and package the examples

```bash
./scripts/2-build_and_package-functions.sh
```


## Provision the AWS infrastructure

```bash
./scripts/3-provision-infrastructure.sh
```


## Verify all examples

```bash
export API_ENDPOINT=$(cat infrastructure/target/outputs.json | jq -r '.LambdaMutualTLS.apiendpoint')

curl -i $API_ENDPOINT/lambda-no-mtls

curl -i $API_ENDPOINT/lambda-only

curl -i $API_ENDPOINT/lambda-layer

curl -i $API_ENDPOINT/lambda-parameter-store

curl -i $API_ENDPOINT/lambda-multiple-certificates
```


## Delete all generated certificates

```bash
./scripts/4-delete-certificates.sh
```


## Delete all provisioned AWS resources

```bash
./scripts/5-deprovision-infrastructure.sh
```

#!/bin/bash
set -euo pipefail

BASEDIR=$(dirname "$0")

#######################################################
# GENERATE ROOT CA CERTIFICATES for Backend Service 1 #
#######################################################

# Generate the root CA private key
openssl genrsa -out $BASEDIR/certs/rootCA-service-1.key 2048

# Create and self sign the root CA public key
openssl req -x509 -new -nodes \
  -key $BASEDIR/certs/rootCA-service-1.key \
  -sha256 \
  -days 365 \
  -subj "/C=US/ST=Washington/L=Seattle/O=Root CA Inc./OU=CA" \
  -out $BASEDIR/certs/rootCA-service-1.crt

#######################################################
# GENERATE ROOT CA CERTIFICATES for Backend Service 2 #
#######################################################

# Generate the root CA private key
openssl genrsa -out $BASEDIR/certs/rootCA-service-2.key 2048

# Create and self sign the root CA public key
openssl req -x509 -new -nodes \
  -key $BASEDIR/certs/rootCA-service-2.key \
  -sha256 \
  -days 365 \
  -subj "/C=US/ST=Ohio/L=Columbus/O=Root CA Inc./OU=CA" \
  -out $BASEDIR/certs/rootCA-service-2.crt

###########################################
# GENERATE BACKEND SERVICE 1 CERTIFICATES #
###########################################

# Create the backend service 1 private key
openssl genrsa -out $BASEDIR/certs/backend_service_1.key 2048

# Create the backend service 1 signing request
openssl req -new -sha256 \
  -key $BASEDIR/certs/backend_service_1.key \
  -subj "/C=US/ST=Washington/L=Seattle/O=The Cloud Company/OU=Backend Service 1/CN=backend-service-1.com" \
  -out $BASEDIR/certs/backend_service_1.csr

# Create the backend service 1 sign public key, by signing the backend service 1 signing request with the root CA private and public key
openssl x509 -req \
  -in $BASEDIR/certs/backend_service_1.csr \
  -days 365 -sha256 \
  -CA $BASEDIR/certs/rootCA-service-1.crt \
  -CAkey $BASEDIR/certs/rootCA-service-1.key \
  -CAserial $BASEDIR/certs/rootCA-service-1.srl \
  -CAcreateserial \
  -out $BASEDIR/certs/backend_service_1.crt


###########################################
# GENERATE BACKEND SERVICE 2 CERTIFICATES #
###########################################

# Create the backend service 2 private key
openssl genrsa -out $BASEDIR/certs/backend_service_2.key 2048

# Create the backend service 2 signing request
openssl req -new -sha256 \
  -key $BASEDIR/certs/backend_service_2.key \
  -subj "/C=US/ST=Ohio/L=Columbus/O=The Cloud Company/OU=Backend Service 2/CN=backend-service-2.com" \
  -out $BASEDIR/certs/backend_service_2.csr

# Create the backend service 2 sign public key, by signing the backend service 2 signing request with the root CA private and public key
openssl x509 -req \
  -in $BASEDIR/certs/backend_service_2.csr \
  -days 365 -sha256 \
  -CA $BASEDIR/certs/rootCA-service-2.crt \
  -CAkey $BASEDIR/certs/rootCA-service-2.key \
  -CAserial $BASEDIR/certs/rootCA-service-2.srl \
  -CAcreateserial \
  -out $BASEDIR/certs/backend_service_2.crt

################################
# GENERATE LAMBDA CERTIFICATES #
################################

# Generate the lambda truststore and import the rootCA-service-1 public key
keytool -importcert \
  -keystore $BASEDIR/certs/client_truststore.jks \
  -storetype JKS \
  -file $BASEDIR/certs/rootCA-service-1.crt \
  -keypass secret \
  -storepass secret \
  -alias rootCA-service-1 \
  -noprompt

keytool -importcert \
  -keystore $BASEDIR/certs/client_truststore.jks \
  -storetype JKS \
  -file $BASEDIR/certs/rootCA-service-2.crt \
  -keypass secret \
  -storepass secret \
  -alias rootCA-service-2 \
  -noprompt

# Import the signed backend service 1 public key into the lambda truststore
keytool -importcert \
  -keystore $BASEDIR/certs/client_truststore.jks \
  -alias backend_service_1 \
  -file $BASEDIR/certs/backend_service_1.crt \
  -keypass secret \
  -storepass secret \
  -noprompt

# Import the signed backend service 2 public key into the lambda truststore
keytool -importcert \
  -keystore $BASEDIR/certs/client_truststore.jks \
  -alias backend_service_2 \
  -file $BASEDIR/certs/backend_service_2.crt \
  -keypass secret \
  -storepass secret \
  -noprompt

# verify the lambda truststore
#keytool -list \
#  -keystore $BASEDIR/certs/client_truststore.jks \
#  -storepass secret \
#  -v

# Generate the lambda keystore for backend service 1 which contains the rootCA-service-1 certificate
keytool -genkeypair \
  -keyalg RSA \
  -sigalg SHA256withRSA \
  -alias lambda \
  -keystore $BASEDIR/certs/client_keystore_1.jks \
  -storepass secret \
  -keypass secret \
  -validity 365 \
  -keysize 2048 \
  -dname "CN=Lambda for Backend Service 1, OU=S-Team, O=The Cloud Company, L=Seattle, S=Washington, C=US"

# Create a signing request for the client keystore backend service 1
keytool -certreq \
  -keystore $BASEDIR/certs/client_keystore_1.jks \
  -sigalg SHA256withRSA \
  -alias lambda \
  -file $BASEDIR/certs/client_1.csr \
  -keypass secret \
  -storepass secret

# Check the certificate request
#openssl req -text -noout \
#  -in $BASEDIR/certs/client_1.csr \
#  -verify

# Sign the signing request with the root CA keys
openssl x509 -req \
  -CA $BASEDIR/certs/rootCA-service-1.crt \
  -CAkey $BASEDIR/certs/rootCA-service-1.key \
  -in $BASEDIR/certs/client_1.csr \
  -out $BASEDIR/certs/client_1.crt \
  -sha256 \
  -days 365 \
  -CAcreateserial \
  -CAserial $BASEDIR/certs/client_1.srl \

# Check the public key
#openssl x509 -noout -text \
#  -in client_1.crt

# Import the rootCA-service-1 public key to the client keystore for backend service 1
keytool -importcert \
  -keystore $BASEDIR/certs/client_keystore_1.jks \
  -storetype JKS \
  -file $BASEDIR/certs/rootCA-service-1.crt \
  -keypass secret \
  -storepass secret \
  -alias rootCA-service-1 \
  -noprompt

# Import the signed client public key into the lambda keystore for backend service 1
keytool -importcert \
  -keystore $BASEDIR/certs/client_keystore_1.jks \
  -alias lambda \
  -file $BASEDIR/certs/client_1.crt \
  -keypass secret \
  -storepass secret \
  -trustcacerts \
  -noprompt

# verify the client keystore for backend service 1
#keytool -list \
#  -keystore $BASEDIR/certs/client_keystore_1.jks \
#  -storepass secret \
#  -v

# Generate the lambda keystore for backend service 2 which contains the rootCA-service-2 certificate
keytool -genkeypair \
  -keyalg RSA \
  -sigalg SHA256withRSA \
  -alias lambda \
  -keystore $BASEDIR/certs/client_keystore_2.jks \
  -storepass secret \
  -keypass secret \
  -validity 365 \
  -keysize 2048 \
  -dname "CN=Lambda for Backend Service 2, OU=S-Team, O=The Cloud Company, L=Columbus, S=Ohio, C=US"

# Create s signing request for the client keystore backend service 2
keytool -certreq \
  -keystore $BASEDIR/certs/client_keystore_2.jks \
  -sigalg SHA256withRSA \
  -alias lambda \
  -file $BASEDIR/certs/client_2.csr \
  -keypass secret \
  -storepass secret

# Check the certificate request
#openssl req -text -noout \
#  -in $BASEDIR/certs/client_2.csr \
#  -verify

# Sign the signing request with the root CA keys
openssl x509 -req \
  -CA $BASEDIR/certs/rootCA-service-2.crt \
  -CAkey $BASEDIR/certs/rootCA-service-2.key \
  -in $BASEDIR/certs/client_2.csr \
  -out $BASEDIR/certs/client_2.crt \
  -sha256 \
  -days 365 \
  -CAcreateserial \
  -CAserial $BASEDIR/certs/client_2.srl \

# Check the public key
#openssl x509 -noout -text \
#  -in client_2.crt

# Import the rootCA-service-2 public key to the client keystore for backend service 2
keytool -importcert \
  -keystore $BASEDIR/certs/client_keystore_2.jks \
  -storetype JKS \
  -file $BASEDIR/certs/rootCA-service-2.crt \
  -keypass secret \
  -storepass secret \
  -alias rootCA-service-2 \
  -noprompt

# Import the signed client public key into the lambda keystore for backend service 2
keytool -importcert \
  -keystore $BASEDIR/certs/client_keystore_2.jks \
  -alias lambda \
  -file $BASEDIR/certs/client_2.crt \
  -keypass secret \
  -storepass secret \
  -trustcacerts \
  -noprompt

# verify the client keystore for backend service 2
#keytool -list \
#  -keystore $BASEDIR/certs/client_keystore_2.jks \
#  -storepass secret \
#  -v

# Copy the backend_service_1.crt, backend_service_1.key and rootCA-service-1.crt into the backend service 1 module
cp $BASEDIR/certs/backend_service_1.crt $BASEDIR/../software/backend-service-1/conf.d/certs/
cp $BASEDIR/certs/backend_service_1.key $BASEDIR/../software/backend-service-1/conf.d/certs/
cp $BASEDIR/certs/rootCA-service-1.crt $BASEDIR/../software/backend-service-1/conf.d/certs/

# Copy the backend_service_2.crt, backend_service_2.key and rootCA-service-2.crt into the backend service 2 module
cp $BASEDIR/certs/backend_service_2.crt $BASEDIR/../software/backend-service-2/conf.d/certs/
cp $BASEDIR/certs/backend_service_2.key $BASEDIR/../software/backend-service-2/conf.d/certs/
cp $BASEDIR/certs/rootCA-service-2.crt $BASEDIR/../software/backend-service-2/conf.d/certs/

# Copy the client_keystore_1.jks and client_truststore.jks into the lambda-only module
cp $BASEDIR/certs/client_keystore_1.jks $BASEDIR/../software/1-lambda-only/src/main/resources/
cp $BASEDIR/certs/client_truststore.jks $BASEDIR/../software/1-lambda-only/src/main/resources/

# Copy the client_keystore_1.jks and client_truststore.jks into the lambda-layer-service-1-cert module
cp $BASEDIR/certs/client_keystore_1.jks $BASEDIR/../software/lambda-layer-service-1-cert/src/main/resources/
cp $BASEDIR/certs/client_truststore.jks $BASEDIR/../software/lambda-layer-service-1-cert/src/main/resources/

# Copy the client_keystore_2.jks and client_truststore.jks into the lambda-layer-service-2-cert module
cp $BASEDIR/certs/client_keystore_2.jks $BASEDIR/../software/lambda-layer-service-2-cert/src/main/resources/
cp $BASEDIR/certs/client_truststore.jks $BASEDIR/../software/lambda-layer-service-2-cert/src/main/resources/

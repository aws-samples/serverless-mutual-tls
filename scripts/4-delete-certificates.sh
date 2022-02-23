#!/bin/bash

BASEDIR=$(dirname "$0")

rm $BASEDIR/certs/*

# Delete the backend_service_1.crt, backend_service_1.key and rootCA.crt in the backend service 1 module
rm $BASEDIR/../software/backend-service-1/conf.d/certs/backend_service_1.crt
rm $BASEDIR/../software/backend-service-1/conf.d/certs/backend_service_1.key
rm $BASEDIR/../software/backend-service-1/conf.d/certs/rootCA-*.crt

# Delete the backend_service_2.crt, backend_service_2.key and rootCA.crt in the backend service 2 module
rm $BASEDIR/../software/backend-service-2/conf.d/certs/backend_service_2.crt
rm $BASEDIR/../software/backend-service-2/conf.d/certs/backend_service_2.key
rm $BASEDIR/../software/backend-service-2/conf.d/certs/rootCA-*.crt

# Delete the client_keystore_1.jks and client_truststore.jks in the lambda-only/function module
rm $BASEDIR/../software/1-lambda-only/src/main/resources/client_keystore_1.jks
rm $BASEDIR/../software/1-lambda-only/src/main/resources/client_truststore.jks

# Delete the client_keystore_1.jks and client_truststore.jks in the lambda-layer-service-1-cert
rm $BASEDIR/../software/lambda-layer-service-1-cert/src/main/resources/client_keystore_1.jks
rm $BASEDIR/../software/lambda-layer-service-1-cert/src/main/resources/client_truststore.jks

# Delete the client_keystore_2.jks and client_truststore.jks in the lambda-layer-service-2-cert
rm $BASEDIR/../software/lambda-layer-service-2-cert/src/main/resources/client_keystore_2.jks
rm $BASEDIR/../software/lambda-layer-service-2-cert/src/main/resources/client_truststore.jks

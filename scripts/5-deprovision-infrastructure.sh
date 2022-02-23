#!/bin/sh

aws ssm delete-parameter \
  --name '/DEV/APP/CLIENT/KEYSTORE/PASSWORD'

aws ssm delete-parameter \
  --name '/DEV/APP/CLIENT/TRUSTSTORE/PASSWORD'

cd infrastructure && cdk destroy

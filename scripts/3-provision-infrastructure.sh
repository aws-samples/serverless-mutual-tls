#!/bin/sh
set -euo pipefail

aws ssm put-parameter \
  --type SecureString \
  --name '/DEV/APP/CLIENT/KEYSTORE/PASSWORD' \
  --value 'secret' \
  --overwrite

aws ssm put-parameter \
  --type SecureString \
  --name '/DEV/APP/CLIENT/TRUSTSTORE/PASSWORD' \
  --value 'secret' \
  --overwrite

cd infrastructure
cdk synth
cdk deploy --outputs-file target/outputs.json

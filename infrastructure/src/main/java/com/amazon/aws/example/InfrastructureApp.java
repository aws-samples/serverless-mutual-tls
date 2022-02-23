// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package com.amazon.aws.example;

import software.amazon.awscdk.App;

public class InfrastructureApp {
  public static void main(final String[] args) {
    App app = new App();
    new InfrastructureStack(app, "LambdaMutualTLS");

    app.synth();
  }
}

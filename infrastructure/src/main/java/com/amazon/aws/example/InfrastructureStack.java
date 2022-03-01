// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package com.amazon.aws.example;

import software.amazon.awscdk.*;
import software.amazon.awscdk.services.apigateway.EndpointType;
import software.amazon.awscdk.services.apigateway.LambdaIntegration;
import software.amazon.awscdk.services.apigateway.RestApi;
import software.amazon.awscdk.services.apigateway.RestApiProps;
import software.amazon.awscdk.services.ec2.*;
import software.amazon.awscdk.services.ecs.*;
import software.amazon.awscdk.services.elasticloadbalancingv2.Protocol;
import software.amazon.awscdk.services.elasticloadbalancingv2.*;
import software.amazon.awscdk.services.iam.Effect;
import software.amazon.awscdk.services.iam.PolicyStatement;
import software.amazon.awscdk.services.lambda.Runtime;
import software.amazon.awscdk.services.lambda.*;
import software.amazon.awscdk.services.logs.RetentionDays;
import software.amazon.awscdk.services.route53.ARecord;
import software.amazon.awscdk.services.route53.PrivateHostedZone;
import software.amazon.awscdk.services.route53.RecordTarget;
import software.amazon.awscdk.services.route53.targets.LoadBalancerTarget;
import software.constructs.Construct;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static java.util.Collections.singletonList;
import static software.amazon.awscdk.services.apigatewayv2.alpha.HttpMethod.GET;
import static software.amazon.awscdk.services.lambda.Architecture.ARM_64;
import static software.amazon.awscdk.services.lambda.Architecture.X86_64;

public class InfrastructureStack extends Stack {
  public InfrastructureStack(final Construct scope, final String id) {
    this(scope, id, null);
  }

  private static final String BACKEND_SERVICE_1_HOST_NAME = "backend-service-1.com";
  private static final String BACKEND_SERVICE_2_HOST_NAME = "backend-service-2.com";

  public InfrastructureStack(final Construct scope, final String id, final StackProps props) {
    super(scope, id, props);

    // Create a new VPC and subnets, spanning 2 availability zones
    Vpc vpc = new Vpc(this, "LambdaMutualTLSVpc", VpcProps.builder()
      .maxAzs(2)
      .build());

    NetworkLoadBalancer nlb = NetworkLoadBalancer.Builder.create(this, "BackendServiceNLB")
      .loadBalancerName("BackendServiceNLB")
      .vpc(vpc)
      .crossZoneEnabled(true)
      .internetFacing(false)
      .build();

    // backend service 1 http listener port
    NetworkListener httpListenerPort80 = nlb.addListener("httpListenerPort80", BaseNetworkListenerProps.builder()
      .port(80)
      .build());

    // backend service 2 http listener port
    NetworkListener httpListenerPort81 = nlb.addListener("httpListenerPort81", BaseNetworkListenerProps.builder()
      .port(81)
      .build());

    // backend service 1 https listener port
    NetworkListener httpsListenerPort443 = nlb.addListener("httpsListenerPort443", BaseNetworkListenerProps.builder()
      .port(443)
      .build());

    // backend service 2 https listener port
    NetworkListener httpsListenerPort444 = nlb.addListener("httpsListenerPort444", BaseNetworkListenerProps.builder()
      .port(444)
      .build());

    // Create the Fargate cluster, based on ECS
    Cluster cluster = new Cluster(this, "BackendServiceCluster", ClusterProps.builder()
      .clusterName("BackendServiceCluster")
      .vpc(vpc)
      .build());

    FargateTaskDefinition taskDefinitionBackendService1 = FargateTaskDefinition.Builder
      .create(this, "BackendService1TaskDefinition")
      .memoryLimitMiB(512)
      .cpu(256)
      .build();
    taskDefinitionBackendService1.addContainer("BackendService1Container", ContainerDefinitionOptions
      .builder()
      .containerName("BackendService1Container")
      .image(ContainerImage.fromAsset("../software/backend-service-1"))
      .memoryLimitMiB(512)
      .cpu(256)
      .portMappings(List.of(
        PortMapping.builder().containerPort(80).build(),
        PortMapping.builder().containerPort(443).build()))
      .logging(LogDriver.awsLogs(AwsLogDriverProps.builder()
        .logRetention(RetentionDays.ONE_WEEK)
        .streamPrefix("ecs/backend-service-1")
        .build()))
      .build());

    FargateTaskDefinition taskDefinitionBackendService2 = FargateTaskDefinition.Builder
      .create(this, "BackendService2TaskDefinition")
      .memoryLimitMiB(512)
      .cpu(256)
      .build();
    taskDefinitionBackendService2.addContainer("BackendService2Container", ContainerDefinitionOptions
      .builder()
      .containerName("BackendService2Container")
      .image(ContainerImage.fromAsset("../software/backend-service-2"))
      .memoryLimitMiB(512)
      .cpu(256)
      .portMappings(List.of(
        PortMapping.builder().containerPort(80).build(),
        PortMapping.builder().containerPort(443).build()))
      .logging(LogDriver.awsLogs(AwsLogDriverProps.builder()
        .logRetention(RetentionDays.ONE_WEEK)
        .streamPrefix("ecs/backend-service-2")
        .build()))
      .build());

    SecurityGroup fargateSecurityGroup = SecurityGroup.Builder.create(this, "FargateSecurityGroup")
      .securityGroupName("FargateSecurityGroup")
      .vpc(vpc)
      .allowAllOutbound(true)
      .build();
    fargateSecurityGroup.addIngressRule(
      Peer.ipv4(vpc.getVpcCidrBlock()),
      Port.tcp(80), "allow HTTP access from within the VPC");
    fargateSecurityGroup.addIngressRule(
      Peer.ipv4(vpc.getVpcCidrBlock()),
      Port.tcp(81), "allow HTTP access from within the VPC");
    fargateSecurityGroup.addIngressRule(
      Peer.ipv4(vpc.getVpcCidrBlock()),
      Port.tcp(443), "allow HTTPS access from within the VPC");
    fargateSecurityGroup.addIngressRule(
      Peer.ipv4(vpc.getVpcCidrBlock()),
      Port.tcp(444), "allow HTTPS access from within the VPC");

    FargateService serviceBackendService1 = FargateService.Builder.create(this, "BackendService1")
      .serviceName("BackendService1")
      .cluster(cluster)
      .taskDefinition(taskDefinitionBackendService1)
      .securityGroups(List.of(fargateSecurityGroup))
      .build();

    FargateService serviceBackendService2 = FargateService.Builder.create(this, "BackendService2")
      .serviceName("BackendService2")
      .cluster(cluster)
      .taskDefinition(taskDefinitionBackendService2)
      .securityGroups(List.of(fargateSecurityGroup))
      .build();

    serviceBackendService1.registerLoadBalancerTargets(EcsTarget.builder()
      .containerName("BackendService1Container")
      .containerPort(80)
      .newTargetGroupId("BackendService1HTTPTargetGroup")
      .listener(ListenerConfig.networkListener(httpListenerPort80, AddNetworkTargetsProps.builder()
        .protocol(Protocol.TCP)
        .port(80)
        .build()))
      .build());
    serviceBackendService1.registerLoadBalancerTargets(EcsTarget.builder()
      .containerName("BackendService1Container")
      .containerPort(443)
      .newTargetGroupId("BackendService1HTTPSTargetGroup")
      .listener(ListenerConfig.networkListener(httpsListenerPort443, AddNetworkTargetsProps.builder()
        .protocol(Protocol.TCP)
        .port(443)
        .build()))
      .build());

    serviceBackendService2.registerLoadBalancerTargets(EcsTarget.builder()
      .containerName("BackendService2Container")
      .containerPort(80)
      .newTargetGroupId("BackendService2HTTPTargetGroup")
      .listener(ListenerConfig.networkListener(httpListenerPort81, AddNetworkTargetsProps.builder()
        .protocol(Protocol.TCP)
        .port(81)
        .build()))
      .build());
    serviceBackendService2.registerLoadBalancerTargets(EcsTarget.builder()
      .containerName("BackendService2Container")
      .containerPort(443)
      .newTargetGroupId("BackendService2HTTPSTargetGroup")
      .listener(ListenerConfig.networkListener(httpsListenerPort444, AddNetworkTargetsProps.builder()
        .protocol(Protocol.TCP)
        .port(444)
        .build()))
      .build());

    // Add VPC endpoint for SSM
    vpc.addInterfaceEndpoint("SSMVPCEndpoint",
      InterfaceVpcEndpointOptions.builder()
        .service(InterfaceVpcEndpointAwsService.SSM)
        .build()
    );

    List<PolicyStatement> ssmPermissions = List.of(
      PolicyStatement.Builder.create()
        .effect(Effect.ALLOW)
        .actions(List.of("ssm:DescribeParameters"))
        .resources(List.of("*"))
        .build(),
      PolicyStatement.Builder.create()
        .effect(Effect.ALLOW)
        .actions(List.of("ssm:GetParameters", "ssm:GetParameter", "ssm:GetParametersByPath"))
        .resources(List.of(String.format("arn:%s:ssm:%s:%s:parameter/%s", getPartition(), getRegion(), getAccount(), "DEV/APP/CLIENT")))
        .build()
    );

    Function lambdaNoMTLSFunction = new Function(this, "LambdaNoMTLSFunction", FunctionProps.builder()
      .functionName("lambda-no-mtls")
      .handler("com.amazon.aws.example.AppClient::handleRequest")
      .runtime(Runtime.JAVA_11)
      .architecture(ARM_64)
      .vpc(vpc)
      .code(Code.fromAsset("../software/0-lambda-no-mtls/target/lambda-no-mtls.jar"))
      .memorySize(1024)
      .environment(Map.of("BACKEND_SERVICE_1_HOST_NAME", BACKEND_SERVICE_1_HOST_NAME))
      .timeout(Duration.seconds(10))
      .logRetention(RetentionDays.ONE_WEEK)
      .build());

    Function lambdaOnlyFunction = new Function(this, "LambdaOnlyFunction", FunctionProps.builder()
      .functionName("lambda-only")
      .handler("com.amazon.aws.example.AppClient::handleRequest")
      .runtime(Runtime.JAVA_11)
      .architecture(ARM_64)
      .vpc(vpc)
      .code(Code.fromAsset("../software/1-lambda-only/target/lambda-only.jar"))
      .memorySize(1024)
      .environment(Map.of(
        "BACKEND_SERVICE_1_HOST_NAME", BACKEND_SERVICE_1_HOST_NAME,
        // add option -Djavax.net.debug=all to troubleshoot issues on the client side
        "JAVA_TOOL_OPTIONS", "-Djavax.net.ssl.keyStore=./client_keystore_1.jks -Djavax.net.ssl.keyStorePassword=secret -Djavax.net.ssl.trustStore=./client_truststore.jks -Djavax.net.ssl.trustStorePassword=secret"
      ))
      .timeout(Duration.seconds(10))
      .logRetention(RetentionDays.ONE_WEEK)
      .build());

    LayerVersion lambdaLayerForService1cert = new LayerVersion(this, "LambdaLayerForService1Cert", LayerVersionProps.builder()
      .layerVersionName("LambdaLayerForService1Cert")
      .compatibleArchitectures(List.of(X86_64, ARM_64))
      .compatibleRuntimes(Arrays.asList(Runtime.JAVA_11, Runtime.JAVA_8_CORRETTO, Runtime.JAVA_8, Runtime.PROVIDED_AL2))
      .code(Code.fromAsset("../software/lambda-layer-service-1-cert/target/service-1-cert-layer.zip"))
      .build());

    LayerVersion lambdaLayerForService2cert = new LayerVersion(this, "LambdaLayerForService2Cert", LayerVersionProps.builder()
      .layerVersionName("LambdaLayerForService2Cert")
      .compatibleArchitectures(List.of(X86_64, ARM_64))
      .compatibleRuntimes(Arrays.asList(Runtime.JAVA_11, Runtime.JAVA_8_CORRETTO, Runtime.JAVA_8, Runtime.PROVIDED_AL2))
      .code(Code.fromAsset("../software/lambda-layer-service-2-cert/target/service-2-cert-layer.zip"))
      .build());

    LayerVersion lambdaLayerForSSLUtility = new LayerVersion(this, "LambdaLayerForSSLUtility", LayerVersionProps.builder()
      .layerVersionName("LambdaLayerForSSLUtility")
      .compatibleArchitectures(List.of(X86_64, ARM_64))
      .compatibleRuntimes(Arrays.asList(Runtime.JAVA_11, Runtime.JAVA_8_CORRETTO, Runtime.JAVA_8, Runtime.PROVIDED_AL2))
      .code(Code.fromAsset("../software/lambda-ssl-utility-layer/target/ssl-utility-layer.zip"))
      .build());

    Function lambdaLayerFunction = new Function(this, "LambdaLayerFunction", FunctionProps.builder()
      .functionName("lambda-layer")
      .handler("com.amazon.aws.example.AppClient::handleRequest")
      .runtime(Runtime.JAVA_11)
      .architecture(ARM_64)
      .layers(singletonList(lambdaLayerForService1cert))
      .vpc(vpc)
      .code(Code.fromAsset("../software/2-lambda-using-separate-layer/target/lambda-using-separate-layer.jar"))
      .memorySize(1024)
      .environment(Map.of(
        "BACKEND_SERVICE_1_HOST_NAME", BACKEND_SERVICE_1_HOST_NAME,
        // add option -Djavax.net.debug=all to troubleshoot issues on the client side
        "JAVA_TOOL_OPTIONS", "-Djavax.net.ssl.keyStore=/opt/client_keystore_1.jks -Djavax.net.ssl.keyStorePassword=secret -Djavax.net.ssl.trustStore=/opt/client_truststore.jks -Djavax.net.ssl.trustStorePassword=secret"
      ))
      .timeout(Duration.seconds(10))
      .logRetention(RetentionDays.ONE_WEEK)
      .build());

    Function lambdaParameterStoreFunction = new Function(this, "LambdaParameterStoreFunction", FunctionProps.builder()
      .functionName("lambda-parameter-store")
      .handler("com.amazon.aws.example.AppClient::handleRequest")
      .runtime(Runtime.JAVA_11)
      .architecture(ARM_64)
      .layers(singletonList(lambdaLayerForService1cert))
      .vpc(vpc)
      .code(Code.fromAsset("../software/3-lambda-using-parameter-store/target/lambda-using-parameter-store.jar"))
      .memorySize(1024)
      .environment(Map.of(
        "BACKEND_SERVICE_1_HOST_NAME", BACKEND_SERVICE_1_HOST_NAME
      ))
      .timeout(Duration.seconds(10))
      .logRetention(RetentionDays.ONE_WEEK)
      .initialPolicy(ssmPermissions)
      .build());

    Function lambdaMultipleCertificatesFunction = new Function(this, "LambdaMultipleCertificatesFunction", FunctionProps.builder()
      .functionName("lambda-multiple-certificates")
      .handler("com.amazon.aws.example.AppClient::handleRequest")
      .runtime(Runtime.JAVA_11)
      .architecture(ARM_64)
      .layers(List.of(lambdaLayerForService1cert, lambdaLayerForService2cert, lambdaLayerForSSLUtility))
      .vpc(vpc)
      .code(Code.fromAsset("../software/4-lambda-using-multiple-certificates/target/lambda-using-multiple-certificates.jar"))
      .memorySize(1024)
      .environment(Map.of(
        "BACKEND_SERVICE_1_HOST_NAME", BACKEND_SERVICE_1_HOST_NAME,
        "BACKEND_SERVICE_2_HOST_NAME", BACKEND_SERVICE_2_HOST_NAME
      ))
      .timeout(Duration.seconds(10))
      .logRetention(RetentionDays.ONE_WEEK)
      .initialPolicy(ssmPermissions)
      .build());

    RestApi restApi = new RestApi(this, "JavaLambdaMutualTLSApi", RestApiProps.builder()
      .restApiName("JavaLambdaMutualTLSApi")
      .endpointTypes(List.of(EndpointType.REGIONAL))
      .build());

    restApi.getRoot()
      .addResource("lambda-no-mtls")
      .addMethod(GET.toString(), LambdaIntegration.Builder.create(lambdaNoMTLSFunction).build());

    restApi.getRoot()
      .addResource("lambda-only")
      .addMethod(GET.toString(), LambdaIntegration.Builder.create(lambdaOnlyFunction).build());

    restApi.getRoot()
      .addResource("lambda-layer")
      .addMethod(GET.toString(), LambdaIntegration.Builder.create(lambdaLayerFunction).build());

    restApi.getRoot()
      .addResource("lambda-parameter-store")
      .addMethod(GET.toString(), LambdaIntegration.Builder.create(lambdaParameterStoreFunction).build());

    restApi.getRoot()
      .addResource("lambda-multiple-certificates")
      .addMethod(GET.toString(), LambdaIntegration.Builder.create(lambdaMultipleCertificatesFunction).build());

    PrivateHostedZone zoneBackendService1 = PrivateHostedZone.Builder.create(this, "PrivateHostedZoneBackendService1")
      .zoneName(BACKEND_SERVICE_1_HOST_NAME)
      .vpc(vpc)
      .build();

    PrivateHostedZone zoneBackendService2 = PrivateHostedZone.Builder.create(this, "PrivateHostedZoneBackendService2")
      .zoneName(BACKEND_SERVICE_2_HOST_NAME)
      .vpc(vpc)
      .build();

    ARecord.Builder.create(this, "AliasRecordBackendService1")
      .zone(zoneBackendService1)
      .target(RecordTarget.fromAlias(new LoadBalancerTarget(nlb)))
      .build();

    ARecord.Builder.create(this, "AliasRecordBackendService2")
      .zone(zoneBackendService2)
      .target(RecordTarget.fromAlias(new LoadBalancerTarget(nlb)))
      .build();

    new CfnOutput(this, "api-endpoint", CfnOutputProps.builder()
      .value(restApi.getUrl())
      .build());
  }
}

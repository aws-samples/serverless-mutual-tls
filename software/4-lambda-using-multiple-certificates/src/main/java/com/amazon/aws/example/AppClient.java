// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package com.amazon.aws.example;

import com.amazon.aws.lambda.layer.TrustAndKeyStore;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.lambda.powertools.parameters.ParamManager;
import software.amazon.lambda.powertools.parameters.SSMProvider;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class AppClient implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent>, TrustAndKeyStore {
  private static final String BACKEND_SERVICE_1_HOST_NAME = System.getenv("BACKEND_SERVICE_1_HOST_NAME");
  private static final String BACKEND_SERVICE_2_HOST_NAME = System.getenv("BACKEND_SERVICE_2_HOST_NAME");
  private final HttpClient clientBackendService1, clientBackendService2;
  private static final SsmClient ssmClient = SsmClient.builder()
    .region(Region.of(System.getenv("AWS_REGION")))
    .build();
  private static final SSMProvider ssmProvider = ParamManager.getSsmProvider(ssmClient);

  public AppClient() throws NoSuchAlgorithmException, UnrecoverableKeyException, KeyStoreException,
    CertificateException, IOException, KeyManagementException {

    Map<String, String> values = ssmProvider.recursive().withDecryption().getMultiple("/DEV/APP/CLIENT");
    String keyStorePassword = values.get("KEYSTORE/PASSWORD");
    String trustStorePassword = values.get("TRUSTSTORE/PASSWORD");

    SSLContext sslContextBackendService1 = getSSLContext(
      "/opt/client_keystore_1.jks",
      keyStorePassword,
      "/opt/client_truststore.jks",
      trustStorePassword
    );

    SSLContext sslContextBackendService2 = getSSLContext(
      "/opt/client_keystore_2.jks",
      keyStorePassword,
      "/opt/client_truststore.jks",
      trustStorePassword
    );

    clientBackendService1 = HttpClient.newBuilder()
      .version(HttpClient.Version.HTTP_2)
      .connectTimeout(Duration.ofSeconds(5))
      .sslContext(sslContextBackendService1)
      .build();

    clientBackendService2 = HttpClient.newBuilder()
      .version(HttpClient.Version.HTTP_2)
      .connectTimeout(Duration.ofSeconds(5))
      .sslContext(sslContextBackendService2)
      .build();
  }

  public APIGatewayProxyResponseEvent handleRequest(final APIGatewayProxyRequestEvent input, final Context context) {
    context.getLogger().log(input.toString());

    HttpRequest httpRequestBackendService1 = HttpRequest.newBuilder()
      .uri(URI.create(String.format("https://%s:443", BACKEND_SERVICE_1_HOST_NAME)))
      .timeout(Duration.ofSeconds(5))
      .GET()
      .build();

    HttpRequest httpRequestBackendService2 = HttpRequest.newBuilder()
      .uri(URI.create(String.format("https://%s:444", BACKEND_SERVICE_2_HOST_NAME)))
      .timeout(Duration.ofSeconds(5))
      .GET()
      .build();

    try {
      CompletableFuture<HttpResponse<String>> httpResponseBackendService1 = clientBackendService1.sendAsync(
        httpRequestBackendService1,
        HttpResponse.BodyHandlers.ofString());
      CompletableFuture<HttpResponse<String>> httpResponseBackendService2 = clientBackendService2.sendAsync(
        httpRequestBackendService2,
        HttpResponse.BodyHandlers.ofString());

      return new APIGatewayProxyResponseEvent()
        .withStatusCode(200)
        .withHeaders(Map.of("Content-Type", "application/json"))
        .withBody(String.format("[%s,%s]", httpResponseBackendService1.get().body(), httpResponseBackendService2.get().body()));
    } catch (Exception e) {
      context.getLogger().log(e.getMessage());
      return new APIGatewayProxyResponseEvent()
        .withStatusCode(500)
        .withHeaders(Map.of("Content-Type", "text/plain"))
        .withBody("error");
    }
  }
}

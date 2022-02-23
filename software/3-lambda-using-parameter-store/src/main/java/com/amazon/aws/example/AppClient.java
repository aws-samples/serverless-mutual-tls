// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package com.amazon.aws.example;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.lambda.powertools.parameters.ParamManager;
import software.amazon.lambda.powertools.parameters.SSMProvider;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Paths;
import java.security.*;
import java.security.cert.CertificateException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class AppClient implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
  private static final String BACKEND_SERVICE_1_HOST_NAME = System.getenv("BACKEND_SERVICE_1_HOST_NAME");
  private final HttpClient httpClient;
  private static final SsmClient ssmClient = SsmClient.builder()
    .region(Region.of(System.getenv("AWS_REGION")))
    .build();
  private static final SSMProvider ssmProvider = ParamManager.getSsmProvider(ssmClient);

  public AppClient() throws NoSuchAlgorithmException, UnrecoverableKeyException, KeyStoreException,
    CertificateException, IOException, KeyManagementException {

    Map<String, String> values = ssmProvider.recursive().withDecryption().getMultiple("/DEV/APP/CLIENT");
    String keyStorePassword = values.get("KEYSTORE/PASSWORD");
    String trustStorePassword = values.get("TRUSTSTORE/PASSWORD");

    KeyStore keyStore = KeyStore.getInstance(
      Paths.get("/opt/client_keystore_1.jks").toFile(),
      keyStorePassword.toCharArray()
    );

    KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
    kmf.init(keyStore, keyStorePassword.toCharArray());

    KeyStore trustStore = KeyStore.getInstance(
      Paths.get("/opt/client_truststore.jks").toFile(),
      trustStorePassword.toCharArray()
    );

    TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
    tmf.init(trustStore);

    SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
    sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

    httpClient = HttpClient.newBuilder()
      .version(HttpClient.Version.HTTP_2)
      .connectTimeout(Duration.ofSeconds(5))
      .sslContext(sslContext)
      .build();
  }

  public APIGatewayProxyResponseEvent handleRequest(final APIGatewayProxyRequestEvent input, final Context context) {
    context.getLogger().log(input.toString());

    HttpRequest httpRequestBackendService1 = HttpRequest.newBuilder()
      .uri(URI.create(String.format("https://%s:443", BACKEND_SERVICE_1_HOST_NAME)))
      .timeout(Duration.ofSeconds(5))
      .GET()
      .build();

    try {
      CompletableFuture<HttpResponse<String>> httpResponseBackendService1 = httpClient.sendAsync(
        httpRequestBackendService1,
        HttpResponse.BodyHandlers.ofString());

      return new APIGatewayProxyResponseEvent()
        .withStatusCode(200)
        .withHeaders(Map.of("Content-Type", "application/json"))
        .withBody(httpResponseBackendService1.get().body());
    } catch (Exception e) {
      context.getLogger().log(e.getMessage());
      return new APIGatewayProxyResponseEvent()
        .withStatusCode(500)
        .withHeaders(Map.of("Content-Type", "text/plain"))
        .withBody("error");
    }
  }
}

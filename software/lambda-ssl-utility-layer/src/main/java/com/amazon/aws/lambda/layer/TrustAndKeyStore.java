// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package com.amazon.aws.lambda.layer;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.nio.file.Paths;
import java.security.*;
import java.security.cert.CertificateException;

public interface TrustAndKeyStore {

  /**
   * @param pathToKeystoreJKS   Provide the path to keystore JKS file in the layer zip (/opt/xyz.jks)
   * @param keyStorePassword    KeyStore Password
   * @param pathToTruststoreJKS Provide the path to truststore JKS file in the layer zip (/opt/abc.jks)
   * @param trustStorePassword  TrustStore Password
   * @return SSLContext which can be used in HttpClient
   */
  default SSLContext getSSLContext(
    final String pathToKeystoreJKS,
    final String keyStorePassword,
    final String pathToTruststoreJKS,
    final String trustStorePassword
  ) throws
    CertificateException,
    KeyStoreException,
    IOException,
    NoSuchAlgorithmException,
    UnrecoverableKeyException,
    KeyManagementException {

    KeyStore keyStore = KeyStore.getInstance(
      Paths.get(pathToKeystoreJKS).toFile(),
      keyStorePassword.toCharArray()
    );

    KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
    kmf.init(keyStore, keyStorePassword.toCharArray());

    KeyStore trustStore = KeyStore.getInstance(
      Paths.get(pathToTruststoreJKS).toFile(),
      trustStorePassword.toCharArray()
    );

    TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
    tmf.init(trustStore);

    SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
    sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

    return sslContext;
  }
}

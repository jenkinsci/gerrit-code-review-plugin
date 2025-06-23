// Copyright (C) 2019 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.gerrit.plugins.checks.client;

import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.net.ssl.SSLContext;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.ssl.SSLContextBuilder;
import org.eclipse.jgit.transport.URIish;

public class GerritChecksApiBuilder {
  public static final Logger LOGGER = Logger.getLogger(GerritChecksApiBuilder.class.getName());

  private URIish gerritBaseURL;
  private HttpClientBuilder clientBuilder;
  private boolean isAuthenticated = false;

  public GerritChecksApiBuilder(URIish gerritBaseURL) {
    this.gerritBaseURL = gerritBaseURL;
    clientBuilder = HttpClientBuilder.create();
  }

  public GerritChecksApiBuilder allowInsecureHttps() {
    try {
      SSLContext sslContext =
          new SSLContextBuilder()
              .loadTrustMaterial(
                  null,
                  (chain, authType) -> true)
              .build();
      SSLConnectionSocketFactory sslsf =
          new SSLConnectionSocketFactory(sslContext, new NoopHostnameVerifier());
      clientBuilder.setSSLSocketFactory(sslsf);
    } catch (KeyStoreException | KeyManagementException | NoSuchAlgorithmException e) {
      LOGGER.log(Level.WARNING, "Could not disable SSL verification.", e);
    }
    return this;
  }

  public GerritChecksApiBuilder setBasicAuthCredentials(String username, String password) {
    CredentialsProvider provider = new BasicCredentialsProvider();
    UsernamePasswordCredentials auth = new UsernamePasswordCredentials(username, password);
    provider.setCredentials(AuthScope.ANY, auth);
    clientBuilder.setDefaultCredentialsProvider(provider);
    isAuthenticated = true;
    return this;
  }

  public GerritChecksApi build() {
    return new GerritChecksApi(gerritBaseURL, clientBuilder.build(), isAuthenticated);
  }
}

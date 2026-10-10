// Copyright (C) 2026 NVIDIA Corporation
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

package jenkins.plugins.gerrit;

import com.urswolfer.gerrit.client.rest.GerritAuthData;
import com.urswolfer.gerrit.client.rest.http.HttpClientBuilderExtension;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.HttpClientBuilder;

/**
 * Applies bounded, system-property-overridable timeouts to both Gerrit HTTP clients.
 *
 * <ul>
 *   <li>{@value #CONNECTION_REQUEST_TIMEOUT_PROPERTY}: pool acquisition, default 30000 ms
 *   <li>{@value #CONNECT_TIMEOUT_PROPERTY}: TCP connect, default 10000 ms
 *   <li>{@value #SOCKET_TIMEOUT_PROPERTY}: socket inactivity, default 90000 ms
 * </ul>
 *
 * <p>The socket timeout is not an overall request deadline.
 */
public final class GerritHttpClientConfig extends HttpClientBuilderExtension {
  public static final String CONNECTION_REQUEST_TIMEOUT_PROPERTY =
      "jenkins.plugins.gerrit.http.connectionRequestTimeoutMillis";
  public static final String CONNECT_TIMEOUT_PROPERTY =
      "jenkins.plugins.gerrit.http.connectTimeoutMillis";
  public static final String SOCKET_TIMEOUT_PROPERTY =
      "jenkins.plugins.gerrit.http.socketTimeoutMillis";

  public static final int DEFAULT_CONNECTION_REQUEST_TIMEOUT_MILLIS = 30_000;
  public static final int DEFAULT_CONNECT_TIMEOUT_MILLIS = 10_000;
  public static final int DEFAULT_SOCKET_TIMEOUT_MILLIS = 90_000;

  static final HttpClientBuilderExtension INSTANCE = new GerritHttpClientConfig();

  private GerritHttpClientConfig() {}

  @Override
  public HttpClientBuilder extend(HttpClientBuilder httpClientBuilder, GerritAuthData authData) {
    return configure(super.extend(httpClientBuilder, authData));
  }

  /**
   * Configures the pool-acquisition, TCP-connect, and socket-inactivity timeouts.
   *
   * <p>The socket timeout limits inactivity between packets; it is not an overall request deadline.
   * Invalid or non-positive property values fall back to the documented defaults.
   */
  public static HttpClientBuilder configure(HttpClientBuilder httpClientBuilder) {
    return httpClientBuilder.setDefaultRequestConfig(requestConfig());
  }

  static RequestConfig requestConfig() {
    return RequestConfig.custom()
        .setConnectionRequestTimeout(
            positiveSystemProperty(
                CONNECTION_REQUEST_TIMEOUT_PROPERTY, DEFAULT_CONNECTION_REQUEST_TIMEOUT_MILLIS))
        .setConnectTimeout(
            positiveSystemProperty(CONNECT_TIMEOUT_PROPERTY, DEFAULT_CONNECT_TIMEOUT_MILLIS))
        .setSocketTimeout(
            positiveSystemProperty(SOCKET_TIMEOUT_PROPERTY, DEFAULT_SOCKET_TIMEOUT_MILLIS))
        .build();
  }

  private static int positiveSystemProperty(String name, int defaultValue) {
    String value = System.getProperty(name);
    if (value != null) {
      try {
        int parsed = Integer.parseInt(value);
        if (parsed > 0) {
          return parsed;
        }
      } catch (NumberFormatException ignored) {
        // Fall through to the safe default.
      }
    }
    return defaultValue;
  }
}

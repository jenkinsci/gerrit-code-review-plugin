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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import java.lang.reflect.Field;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.HttpClientBuilder;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class GerritHttpClientConfigTest {
  private String originalConnectionRequestTimeout;
  private String originalConnectTimeout;
  private String originalSocketTimeout;

  @Before
  public void saveAndClearProperties() {
    originalConnectionRequestTimeout =
        System.getProperty(GerritHttpClientConfig.CONNECTION_REQUEST_TIMEOUT_PROPERTY);
    originalConnectTimeout = System.getProperty(GerritHttpClientConfig.CONNECT_TIMEOUT_PROPERTY);
    originalSocketTimeout = System.getProperty(GerritHttpClientConfig.SOCKET_TIMEOUT_PROPERTY);
    clearProperties();
  }

  @After
  public void restoreProperties() {
    restoreProperty(
        GerritHttpClientConfig.CONNECTION_REQUEST_TIMEOUT_PROPERTY,
        originalConnectionRequestTimeout);
    restoreProperty(GerritHttpClientConfig.CONNECT_TIMEOUT_PROPERTY, originalConnectTimeout);
    restoreProperty(GerritHttpClientConfig.SOCKET_TIMEOUT_PROPERTY, originalSocketTimeout);
  }

  private static void clearProperties() {
    System.clearProperty(GerritHttpClientConfig.CONNECTION_REQUEST_TIMEOUT_PROPERTY);
    System.clearProperty(GerritHttpClientConfig.CONNECT_TIMEOUT_PROPERTY);
    System.clearProperty(GerritHttpClientConfig.SOCKET_TIMEOUT_PROPERTY);
  }

  @Test
  public void usesBoundedDefaults() {
    RequestConfig config = GerritHttpClientConfig.requestConfig();

    assertEquals(
        GerritHttpClientConfig.DEFAULT_CONNECTION_REQUEST_TIMEOUT_MILLIS,
        config.getConnectionRequestTimeout());
    assertEquals(GerritHttpClientConfig.DEFAULT_CONNECT_TIMEOUT_MILLIS, config.getConnectTimeout());
    assertEquals(GerritHttpClientConfig.DEFAULT_SOCKET_TIMEOUT_MILLIS, config.getSocketTimeout());
  }

  @Test
  public void readsPositiveSystemPropertyOverrides() {
    System.setProperty(GerritHttpClientConfig.CONNECTION_REQUEST_TIMEOUT_PROPERTY, "101");
    System.setProperty(GerritHttpClientConfig.CONNECT_TIMEOUT_PROPERTY, "202");
    System.setProperty(GerritHttpClientConfig.SOCKET_TIMEOUT_PROPERTY, "303");

    RequestConfig config = GerritHttpClientConfig.requestConfig();

    assertEquals(101, config.getConnectionRequestTimeout());
    assertEquals(202, config.getConnectTimeout());
    assertEquals(303, config.getSocketTimeout());
  }

  @Test
  public void rejectsInvalidOrUnboundedOverrides() {
    System.setProperty(GerritHttpClientConfig.CONNECTION_REQUEST_TIMEOUT_PROPERTY, "0");
    System.setProperty(GerritHttpClientConfig.CONNECT_TIMEOUT_PROPERTY, "not-a-number");
    System.setProperty(GerritHttpClientConfig.SOCKET_TIMEOUT_PROPERTY, "-1");

    RequestConfig config = GerritHttpClientConfig.requestConfig();

    assertEquals(
        GerritHttpClientConfig.DEFAULT_CONNECTION_REQUEST_TIMEOUT_MILLIS,
        config.getConnectionRequestTimeout());
    assertEquals(GerritHttpClientConfig.DEFAULT_CONNECT_TIMEOUT_MILLIS, config.getConnectTimeout());
    assertEquals(GerritHttpClientConfig.DEFAULT_SOCKET_TIMEOUT_MILLIS, config.getSocketTimeout());
  }

  @Test
  public void restClientExtensionAppliesSharedRequestConfig() throws Exception {
    HttpClientBuilder builder = HttpClientBuilder.create();

    assertSame(builder, GerritHttpClientConfig.INSTANCE.extend(builder, null));
    RequestConfig requestConfig = configuredRequestConfig(builder);
    assertEquals(
        GerritHttpClientConfig.DEFAULT_CONNECTION_REQUEST_TIMEOUT_MILLIS,
        requestConfig.getConnectionRequestTimeout());
    assertEquals(
        GerritHttpClientConfig.DEFAULT_CONNECT_TIMEOUT_MILLIS, requestConfig.getConnectTimeout());
    assertEquals(
        GerritHttpClientConfig.DEFAULT_SOCKET_TIMEOUT_MILLIS, requestConfig.getSocketTimeout());
  }

  private static RequestConfig configuredRequestConfig(HttpClientBuilder builder) throws Exception {
    Field field = HttpClientBuilder.class.getDeclaredField("defaultRequestConfig");
    field.setAccessible(true);
    return (RequestConfig) field.get(builder);
  }

  private static void restoreProperty(String name, String value) {
    if (value == null) {
      System.clearProperty(name);
    } else {
      System.setProperty(name, value);
    }
  }
}

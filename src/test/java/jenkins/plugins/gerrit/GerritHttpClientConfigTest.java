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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.lang.reflect.Field;
import org.apache.http.client.HttpRequestRetryHandler;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.impl.client.DefaultHttpRequestRetryHandler;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.protocol.HttpCoreContext;
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

  @Test
  public void configuresMethodAwareRetryAndDisablesRedirects() throws Exception {
    HttpClientBuilder builder = HttpClientBuilder.create();

    assertSame(builder, GerritHttpClientConfig.configure(builder));
    DefaultHttpRequestRetryHandler retryHandler =
        (DefaultHttpRequestRetryHandler) configuredRetryHandler(builder);
    assertEquals(1, retryHandler.getRetryCount());
    assertFalse(retryHandler.isRequestSentRetryEnabled());
    assertTrue(redirectHandlingDisabled(builder));
  }

  @Test
  public void retriesOnlySafeRequestsAndOnlyOnce() {
    HttpRequestRetryHandler retryHandler = GerritHttpClientConfig.retryHandler();
    IOException transportFailure = new IOException("connection reset");

    assertTrue(retryHandler.retryRequest(transportFailure, 1, sentRequest(new HttpGet("/"))));
    assertFalse(retryHandler.retryRequest(transportFailure, 2, sentRequest(new HttpGet("/"))));
    assertFalse(retryHandler.retryRequest(transportFailure, 1, sentRequest(new HttpPost("/"))));
    assertTrue(retryHandler.retryRequest(transportFailure, 1, unsentRequest(new HttpPost("/"))));
    assertFalse(
        retryHandler.retryRequest(
            new InterruptedIOException("interrupted"), 1, sentRequest(new HttpGet("/"))));
  }

  private static RequestConfig configuredRequestConfig(HttpClientBuilder builder) throws Exception {
    Field field = HttpClientBuilder.class.getDeclaredField("defaultRequestConfig");
    field.setAccessible(true);
    return (RequestConfig) field.get(builder);
  }

  private static HttpRequestRetryHandler configuredRetryHandler(HttpClientBuilder builder)
      throws Exception {
    Field field = HttpClientBuilder.class.getDeclaredField("retryHandler");
    field.setAccessible(true);
    return (HttpRequestRetryHandler) field.get(builder);
  }

  private static boolean redirectHandlingDisabled(HttpClientBuilder builder) throws Exception {
    Field field = HttpClientBuilder.class.getDeclaredField("redirectHandlingDisabled");
    field.setAccessible(true);
    return field.getBoolean(builder);
  }

  private static HttpClientContext sentRequest(Object request) {
    return requestContext(request, true);
  }

  private static HttpClientContext unsentRequest(Object request) {
    return requestContext(request, false);
  }

  private static HttpClientContext requestContext(Object request, boolean sent) {
    HttpClientContext context = HttpClientContext.create();
    context.setAttribute(HttpCoreContext.HTTP_REQUEST, request);
    context.setAttribute(HttpCoreContext.HTTP_REQ_SENT, sent);
    return context;
  }

  private static void restoreProperty(String name, String value) {
    if (value == null) {
      System.clearProperty(name);
    } else {
      System.setProperty(name, value);
    }
  }
}

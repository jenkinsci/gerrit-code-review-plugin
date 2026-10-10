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

package com.google.gerrit.plugins.checks.client;

import static org.junit.Assert.assertEquals;

import java.lang.reflect.Field;
import jenkins.plugins.gerrit.GerritHttpClientConfig;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.HttpClientBuilder;
import org.eclipse.jgit.transport.URIish;
import org.junit.Test;

public class GerritChecksApiBuilderTest {

  @Test
  public void checksClientUsesSharedTimeoutConfiguration() throws Exception {
    HttpClientBuilder builder = HttpClientBuilder.create();

    new GerritChecksApiBuilder(new URIish("https://gerrit.example"), builder);

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
}

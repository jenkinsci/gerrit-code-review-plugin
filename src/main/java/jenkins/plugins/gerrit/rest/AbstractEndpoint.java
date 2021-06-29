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

package jenkins.plugins.gerrit.rest;

import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URISyntaxException;
import java.sql.Timestamp;

import org.apache.commons.httpclient.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.eclipse.jgit.transport.URIish;

abstract public class AbstractEndpoint {

  private static final String AUTH_PREFIX = "/a/";

  private final URIish gerritBaseUrl;
  private final boolean isAuthentcated;
  protected final CloseableHttpClient client;
  protected final URIBuilder uriBuilder;

  protected AbstractEndpoint(
      URIish gerritBaseUrl, CloseableHttpClient client, boolean isAuthenticated)
      throws URISyntaxException {
    this.isAuthentcated = isAuthenticated;
    this.gerritBaseUrl = gerritBaseUrl;
    this.client = client;
    this.uriBuilder = new URIBuilder(gerritBaseUrl.toASCIIString());
  }

  protected String getPrefix() {
    return isAuthentcated ? AUTH_PREFIX : "/";
  }

  protected URIish getGerritBaseUrl() {
    return gerritBaseUrl;
  }

  protected <T> T getJsonResponse(HttpUriRequest request) throws IOException, RestApiException {
    try (CloseableHttpResponse response = client.execute(request)) {
      if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
        return JsonBodyParser.parseResponse(EntityUtils.toString(response.getEntity()),
            new TypeToken<T>() {}.getType());
      }
      throw new RestApiException(
          String.format("Request failed with status: %d", response.getStatusLine().getStatusCode()));
    }
  }

  public static class JsonBodyParser {
    private static final String JSON_PREFIX = ")]}'";

    public static <T> T parseResponse(String json, Type type) {
      json = removeJsonPrefix(json);
      return newGson().fromJson(json, type);
    }

    public static String createRequestBody(Object object, Type type) {
      return newGson().toJson(object, type);
    }

    private static Gson newGson() {
      return new GsonBuilder()
          .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
          .setDateFormat("yyyy-MM-dd HH:mm:ss.SSS")
          .registerTypeAdapter(Timestamp.class, new UTCTimestampTypeAdapter())
          .create();
    }

    private static String removeJsonPrefix(String json) {
      if (json.startsWith(JSON_PREFIX)) {
        return json.split("\n", 2)[1];
      }
      return json;
    }
  }
}

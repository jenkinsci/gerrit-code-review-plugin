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

package jenkins.plugins.gerrit.checks.client;

import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gson.reflect.TypeToken;
import java.net.URI;
import java.net.URISyntaxException;
import jenkins.plugins.gerrit.checks.api.CheckerInfo;
import jenkins.plugins.gerrit.checks.api.CheckerInput;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.eclipse.jgit.transport.URIish;

public class Checkers extends AbstractEndpoint {

  protected Checkers(URIish gerritBaseUrl, CloseableHttpClient client, boolean isAuthenticated)
      throws URISyntaxException {
    super(gerritBaseUrl, client, isAuthenticated);
  }

  public CheckerInfo create(CheckerInput input) throws RestApiException {
    try {
      HttpPost request = new HttpPost(buildRequestUrl());
      String inputString =
          JsonBodyParser.createRequestBody(input, new TypeToken<CheckerInput>() {}.getType());
      request.setEntity(new StringEntity(inputString));
      request.setHeader("Content-type", "application/json");
      try (CloseableHttpResponse response = client.execute(request)) {
        if (response.getStatusLine().getStatusCode() == HttpStatus.SC_CREATED) {
          return JsonBodyParser.parseResponse(
              EntityUtils.toString(response.getEntity()),
              new TypeToken<CheckerInfo>() {}.getType());
        }
        throw new RestApiException(
            String.format("Request returned status %s", response.getStatusLine().getStatusCode()));
      }
    } catch (Exception e) {
      throw new RestApiException("Could not create checker", e);
    }
  }

  public CheckerInfo get(String checkerUuid) throws RestApiException {
    try {
      HttpGet request = new HttpGet(buildRequestUrl(checkerUuid));
      try (CloseableHttpResponse response = client.execute(request)) {
        if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
          return JsonBodyParser.parseResponse(
              EntityUtils.toString(response.getEntity()),
              new TypeToken<CheckerInfo>() {}.getType());
        }
        throw new RestApiException(
            String.format(
                "Request failed with status: %d", response.getStatusLine().getStatusCode()));
      }
    } catch (Exception e) {
      throw new RestApiException("Failed to get checker info: ", e);
    }
  }

  public CheckerInfo update(CheckerInput input) throws RestApiException {
    try {
      HttpPost request = new HttpPost(buildRequestUrl(input.uuid));
      String inputString =
          JsonBodyParser.createRequestBody(input, new TypeToken<CheckerInput>() {}.getType());
      request.setEntity(new StringEntity(inputString));
      request.setHeader("Content-type", "application/json");

      try (CloseableHttpResponse response = client.execute(request)) {
        if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
          return JsonBodyParser.parseResponse(
              EntityUtils.toString(response.getEntity()),
              new TypeToken<CheckerInfo>() {}.getType());
        }
        throw new RestApiException(
            String.format("Request returned status %s", response.getStatusLine().getStatusCode()));
      }
    } catch (Exception e) {
      throw new RestApiException("Could not update checker", e);
    }
  }

  private URI buildRequestUrl() throws URISyntaxException {
    return buildRequestUrl("");
  }

  private URI buildRequestUrl(String suffixPath) throws URISyntaxException {
    return uriBuilder
        .setPath(String.format("%splugins/checks/checkers/%s", getPrefix(), suffixPath))
        .build();
  }
}

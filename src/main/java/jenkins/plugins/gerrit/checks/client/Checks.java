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
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import jenkins.plugins.gerrit.checks.api.CheckInfo;
import jenkins.plugins.gerrit.checks.api.CheckInput;
import jenkins.plugins.gerrit.checks.api.RerunInput;
import org.apache.http.HttpStatus;
import org.apache.http.ParseException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.eclipse.jgit.transport.URIish;

public class Checks extends AbstractEndpoint {

  private int changeNumber;
  private int patchSetNumber;

  public Checks(URIish gerritBaseUrl, CloseableHttpClient client, boolean isAuthenticated)
      throws URISyntaxException {
    super(gerritBaseUrl, client, isAuthenticated);
  }

  public Checks change(int changeNumber) {
    this.changeNumber = changeNumber;
    return this;
  }

  public Checks patchSet(int patchSetNumber) {
    this.patchSetNumber = patchSetNumber;
    return this;
  }

  public CheckInfo create(CheckInput input) throws RestApiException {
    try {
      return performCreateOrUpdate(input);
    } catch (Exception e) {
      throw new RestApiException("Could not create check", e);
    }
  }

  public CheckInfo get(String checkerUuid) throws RestApiException {
    try {
      HttpGet request = new HttpGet(buildRequestUrl(checkerUuid));
      try (CloseableHttpResponse response = client.execute(request)) {
        if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
          return JsonBodyParser.parseResponse(
              EntityUtils.toString(response.getEntity()), new TypeToken<CheckInfo>() {}.getType());
        }
        throw new RestApiException(
            String.format(
                "Request failed with status: %d", response.getStatusLine().getStatusCode()));
      }
    } catch (Exception e) {
      throw new RestApiException("Failed to get check info: ", e);
    }
  }

  public List<CheckInfo> list() throws RestApiException {
    try {
      HttpGet request = new HttpGet(buildRequestUrl());
      try (CloseableHttpResponse response = client.execute(request)) {
        if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
          return JsonBodyParser.parseResponse(
              EntityUtils.toString(response.getEntity()),
              new TypeToken<List<CheckInfo>>() {}.getType());
        }
        throw new RestApiException(
            String.format(
                "Request failed with status: %d", response.getStatusLine().getStatusCode()));
      }
    } catch (Exception e) {
      throw new RestApiException("Failed to list checks: ", e);
    }
  }

  public CheckInfo rerun(String checkerUuid) throws RestApiException {
    return rerun(checkerUuid, new RerunInput());
  }

  public CheckInfo rerun(String checkerUuid, RerunInput input) throws RestApiException {
    try {
      HttpPost request = new HttpPost(buildRequestUrl(checkerUuid + "/rerun"));
      String inputString =
          JsonBodyParser.createRequestBody(input, new TypeToken<RerunInput>() {}.getType());
      request.setEntity(new StringEntity(inputString));
      request.setHeader("Content-type", "application/json");
      try (CloseableHttpResponse response = client.execute(request)) {
        if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
          return JsonBodyParser.parseResponse(
              EntityUtils.toString(response.getEntity()), new TypeToken<CheckInfo>() {}.getType());
        }
        throw new RestApiException(
            String.format(
                "Request failed with status: %d", response.getStatusLine().getStatusCode()));
      }
    } catch (Exception e) {
      throw new RestApiException("Could not rerun check", e);
    }
  }

  public CheckInfo update(CheckInput input) throws RestApiException {
    try {
      return performCreateOrUpdate(input);
    } catch (Exception e) {
      throw new RestApiException("Could not update check", e);
    }
  }

  private CheckInfo performCreateOrUpdate(CheckInput input)
      throws RestApiException, URISyntaxException, ParseException, IOException {
    HttpPost request = new HttpPost(buildRequestUrl());
    String inputString =
        JsonBodyParser.createRequestBody(input, new TypeToken<CheckInput>() {}.getType());
    request.setEntity(new StringEntity(inputString));
    request.setHeader("Content-type", "application/json");

    try (CloseableHttpResponse response = client.execute(request)) {
      if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
        return JsonBodyParser.parseResponse(
            EntityUtils.toString(response.getEntity()), new TypeToken<CheckInfo>() {}.getType());
      }
      throw new RestApiException(
          String.format("Request returned status %s", response.getStatusLine().getStatusCode()));
    }
  }

  private URI buildRequestUrl() throws URISyntaxException {
    return buildRequestUrl("");
  }

  private URI buildRequestUrl(String suffixPath) throws URISyntaxException {
    return uriBuilder
        .setPath(
            String.format(
                "%schanges/%d/revisions/%d/checks/%s",
                getPrefix(), changeNumber, patchSetNumber, suffixPath))
        .build();
  }
}

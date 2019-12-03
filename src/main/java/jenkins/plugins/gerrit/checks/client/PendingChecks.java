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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import jenkins.plugins.gerrit.checks.api.CheckState;
import jenkins.plugins.gerrit.checks.api.PendingChecksInfo;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.eclipse.jgit.transport.URIish;

public class PendingChecks extends AbstractEndpoint {
  private static final String PENDING_CHECKS_PATH = "plugins/checks/checks.pending/";

  private HashMap<String, String> queries = new HashMap<String, String>();

  public PendingChecks(URIish gerritBaseUrl, CloseableHttpClient client, boolean isAuthenticated)
      throws URISyntaxException {
    super(gerritBaseUrl, client, isAuthenticated);
  }

  public PendingChecks checker(String uuid) {
    queries.put("checker", uuid);
    return this;
  }

  public PendingChecks scheme(String scheme) {
    queries.put("scheme", scheme);
    return this;
  }

  public PendingChecks state(CheckState state) {
    queries.put("state", state.name());
    return this;
  }

  public List<PendingChecksInfo> list() throws RestApiException {
    try {
      HttpGet request = new HttpGet(buildRequestUrl());
      try (CloseableHttpResponse response = client.execute(request)) {
        if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
          return JsonBodyParser.parseResponse(
              EntityUtils.toString(response.getEntity()),
              new TypeToken<List<PendingChecksInfo>>() {}.getType());
        }
        throw new RestApiException(
            String.format(
                "Request failed with status: %d", response.getStatusLine().getStatusCode()));
      }
    } catch (Exception e) {
      throw new RestApiException("Failed to list pending checks: ", e);
    }
  }

  private URI buildRequestUrl() throws URISyntaxException {
    if (!queries.isEmpty()) {
      StringBuffer queryString = new StringBuffer();
      for (Map.Entry<String, String> query : queries.entrySet()) {
        String connector = queryString.length() > 0 ? "+" : "";
        queryString.append(String.format("%s%s:%s", connector, query.getKey(), query.getValue()));
      }
      uriBuilder.setParameter("query", queryString.toString());
    }
    return uriBuilder.setPath(getPrefix() + PENDING_CHECKS_PATH).build();
  }
}

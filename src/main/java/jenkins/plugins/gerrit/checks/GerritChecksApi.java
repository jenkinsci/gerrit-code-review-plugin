// Copyright (C) 2019 GerritForge Ltd
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

package jenkins.plugins.gerrit.checks;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import org.apache.http.HttpStatus;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.eclipse.jgit.transport.URIish;

public class GerritChecksApi {
  private static final String PENDING_CHECKS_PATH = "plugins/checks/checks.pending/";

  private URIish gerritURL;

  public GerritChecksApi(URIish gerritURL) {
    this.gerritURL = gerritURL;
  }

  public List<PendingChecksInfo> getChangesWithPendingChecksByCheckerId(String checkerUUID)
      throws ClientProtocolException, IOException {
    URI queryUrl =
        URI.create(
            String.format("%s%s?query=checker:%s", gerritURL, PENDING_CHECKS_PATH, checkerUUID));
    return requestPendingChecks(queryUrl);
  }

  public List<PendingChecksInfo> getChangesWithPendingChecksByCheckerScheme(String checkerScheme)
      throws ClientProtocolException, IOException {
    URI queryUrl =
        URI.create(
            String.format("%s%s?query=scheme:%s", gerritURL, PENDING_CHECKS_PATH, checkerScheme));
    return requestPendingChecks(queryUrl);
  }

  private List<PendingChecksInfo> requestPendingChecks(URI queryUrl) throws IOException {
    CloseableHttpClient httpClient = HttpClients.createDefault();

    try {
      HttpGet request = new HttpGet(queryUrl);
      CloseableHttpResponse response = httpClient.execute(request);
      if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
        return newGson()
            .fromJson(
                EntityUtils.toString(response.getEntity()).split("\n", 2)[1],
                new TypeToken<List<PendingChecksInfo>>() {}.getType());
      }
      return Collections.emptyList();
    } finally {
      httpClient.close();
    }
  }

  private Gson newGson() {
    return new GsonBuilder()
        .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
        .create();
  }
}

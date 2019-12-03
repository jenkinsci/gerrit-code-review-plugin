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

import java.io.IOException;
import java.net.URISyntaxException;
import org.apache.http.impl.client.CloseableHttpClient;
import org.eclipse.jgit.transport.URIish;

public class GerritChecksApi {

  private URIish gerritBaseUrl;
  private CloseableHttpClient client;
  private final boolean isAuthenticated;

  public GerritChecksApi(
      URIish gerritBaseUrl, CloseableHttpClient client, boolean isAuthenticated) {
    this.gerritBaseUrl = gerritBaseUrl;
    this.client = client;
    this.isAuthenticated = isAuthenticated;
  }

  public Checkers checkers() throws URISyntaxException {
    return new Checkers(gerritBaseUrl, client, isAuthenticated);
  }

  public Checks checks() throws URISyntaxException {
    return new Checks(gerritBaseUrl, client, isAuthenticated);
  }

  public PendingChecks pendingChecks() throws URISyntaxException {
    return new PendingChecks(gerritBaseUrl, client, isAuthenticated);
  }

  public void close() throws IOException {
    client.close();
  }
}

// Copyright (C) 2019 SAP SE
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

import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.plugins.checks.api.PendingChecksInfo;
import com.google.gerrit.plugins.checks.client.GerritChecksApi;
import hudson.model.TaskListener;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import jenkins.plugins.git.GitSCMSourceRequest;
import org.eclipse.jgit.transport.URIish;

public class GerritSCMSourceRequest extends GitSCMSourceRequest {

  private final boolean filterForPendingChecks;

  private final Map<String, HashSet<PendingChecksInfo>> patchsetWithPendingChecks;

  public GerritSCMSourceRequest(
      GerritSCMSource source, GerritSCMSourceContext context, TaskListener listener) {
    super(source, context, listener);
    this.filterForPendingChecks = context.filterForPendingChecks();
    this.patchsetWithPendingChecks =
        filterForPendingChecks
            ? getChangesWithPendingChecks(source, context, listener)
            : new HashMap<>();
  }

  public Map<String, HashSet<PendingChecksInfo>> getPatchsetWithPendingChecks() {
    return patchsetWithPendingChecks;
  }

  private GerritChecksApi getGerritChecksApi(GerritSCMSource source, TaskListener listener)
      throws IOException {
    try {
      return source.createGerritChecksApi(listener, new GerritURI(new URIish(source.getRemote())));
    } catch (URISyntaxException | IOException e) {
      throw new IOException(e);
    }
  }

  private HashMap<String, HashSet<PendingChecksInfo>> getChangesWithPendingChecks(
      GerritSCMSource source, GerritSCMSourceContext context, TaskListener listener) {
    HashMap<String, HashSet<PendingChecksInfo>> patchsetWithPendingChecks =
        new HashMap<>();
    List<PendingChecksInfo> pendingChecks = new ArrayList<>();

    try {
      GerritChecksApi gerritChecksApi = getGerritChecksApi(source, listener);
      pendingChecks = switch (context.checksQueryOperator()) {
        case ID -> gerritChecksApi.pendingChecks().checker(context.checksQueryString()).list();
        case SCHEME -> gerritChecksApi.pendingChecks().scheme(context.checksQueryString()).list();
      };
    } catch (URISyntaxException | IOException | RestApiException e) {
      listener.getLogger().println("Unable to query for pending checks: " + e);
    }

    for (PendingChecksInfo check : pendingChecks) {
      if (check.patchSet == null) {
        continue;
      }
      String ref = String.format("%d/%d", check.patchSet.changeNumber, check.patchSet.patchSetId);
      HashSet<PendingChecksInfo> checks = new HashSet<>();
      if (patchsetWithPendingChecks.containsKey(ref)) {
        checks = patchsetWithPendingChecks.get(ref);
        checks.add(check);
      } else {
        checks.add(check);
      }
      patchsetWithPendingChecks.put(ref, checks);
    }

    return patchsetWithPendingChecks;
  }
}

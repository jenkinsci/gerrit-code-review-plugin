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
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.model.TaskListener;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import jenkins.plugins.gerrit.checks.api.PendingChecksInfo;
import jenkins.plugins.gerrit.checks.client.GerritChecksApi;
import jenkins.plugins.gerrit.traits.FilterChecksTrait.ChecksQueryOperator;
import jenkins.plugins.git.GitSCMSourceRequest;
import org.eclipse.jgit.transport.URIish;

public class GerritSCMSourceRequest extends GitSCMSourceRequest {

  private final boolean filterForPendingChecks;

  private HashMap<String, HashSet<PendingChecksInfo>> patchsetWithPendingChecks =
      new HashMap<String, HashSet<PendingChecksInfo>>();

  public GerritSCMSourceRequest(
      GerritSCMSource source, GerritSCMSourceContext context, TaskListener listener) {
    super(source, context, listener);
    this.filterForPendingChecks = context.filterForPendingChecks();
    if (filterForPendingChecks) {
      this.patchsetWithPendingChecks = getChangesWithPendingChecks(source, context, listener);
    }
  }

  public HashMap<String, HashSet<PendingChecksInfo>> getPatchsetWithPendingChecks() {
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

  @SuppressFBWarnings(value = "NP_UNWRITTEN_PUBLIC_OR_PROTECTED_FIELD")
  private HashMap<String, HashSet<PendingChecksInfo>> getChangesWithPendingChecks(
      GerritSCMSource source, GerritSCMSourceContext context, TaskListener listener) {
    HashMap<String, HashSet<PendingChecksInfo>> patchsetWithPendingChecks =
        new HashMap<String, HashSet<PendingChecksInfo>>();
    Optional<List<PendingChecksInfo>> pendingChecks = Optional.empty();

    try {
      GerritChecksApi gerritChecksApi = getGerritChecksApi(source, listener);
      if (context.checksQueryOperator() == ChecksQueryOperator.ID) {
        pendingChecks =
            Optional.of(
                gerritChecksApi.pendingChecks().checker(context.checksQueryString()).list());
      } else if (context.checksQueryOperator() == ChecksQueryOperator.SCHEME) {
        pendingChecks =
            Optional.of(gerritChecksApi.pendingChecks().scheme(context.checksQueryString()).list());
      } else {
        throw new IOException("Unknown query operator for querying pending checks.");
      }
    } catch (URISyntaxException | IOException | RestApiException e) {
      listener.getLogger().println("Unable to query for pending checks: " + e);
    }

    if (pendingChecks.isPresent()) {
      for (PendingChecksInfo check : pendingChecks.get()) {
        String ref = String.format("%d/%d", check.patchSet.changeNumber, check.patchSet.patchSetId);
        HashSet<PendingChecksInfo> checks = new HashSet<PendingChecksInfo>();
        if (patchsetWithPendingChecks.containsKey(ref)) {
          checks = patchsetWithPendingChecks.get(ref);
          checks.add(check);
        } else {
          checks.add(check);
        }
        patchsetWithPendingChecks.put(ref, checks);
      }
    }

    return patchsetWithPendingChecks;
  }
}

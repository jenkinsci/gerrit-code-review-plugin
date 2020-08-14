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

import com.google.gerrit.extensions.client.ListChangesOption;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.RevisionInfo;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.plugins.checks.api.PendingChecksInfo;
import com.google.gerrit.plugins.checks.client.GerritChecksApi;
import hudson.model.TaskListener;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import jenkins.plugins.git.GitSCMSourceRequest;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.transport.URIish;

import javax.annotation.Nullable;

public class GerritSCMSourceRequest extends GitSCMSourceRequest {

  private final boolean filterForPendingChecks;

  private final Map<String, HashSet<PendingChecksInfo>> patchsetWithPendingChecks;

  private final ProjectChanges projectChanges;

  public GerritSCMSourceRequest(
      GerritSCMSource source, GerritSCMSourceContext context, TaskListener listener) {
    super(source, context, listener);
    this.projectChanges = createProjectChanges(source, listener);
    this.filterForPendingChecks = context.filterForPendingChecks();
    this.patchsetWithPendingChecks =
        filterForPendingChecks
            ? getChangesWithPendingChecks(source, context, listener)
            : new HashMap<String, HashSet<PendingChecksInfo>>();
  }

  private ProjectChanges createProjectChanges(GerritSCMSource source, TaskListener listener) {
    try {
      return source.getProjectChanges();
    } catch (IOException e) {
      listener.error(e.getMessage());
      return null;
    }
  }

  public Map<String, HashSet<PendingChecksInfo>> getPatchsetWithPendingChecks() {
    return patchsetWithPendingChecks;
  }

  @Nullable
  public Integer getPatchSetForChangeByRevision(int changeNum, ObjectId gitRef) {
    RevisionInfo revision = projectChanges.get(changeNum)
        .map(info -> info.revisions.get(gitRef.name()))
        .orElse(null);
    return revision == null ? null : revision._number;
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
        new HashMap<String, HashSet<PendingChecksInfo>>();
    List<PendingChecksInfo> pendingChecks = new ArrayList<PendingChecksInfo>();

    try {
      GerritChecksApi gerritChecksApi = getGerritChecksApi(source, listener);
      switch (context.checksQueryOperator()) {
        case ID:
          pendingChecks =
              gerritChecksApi.pendingChecks().checker(context.checksQueryString()).list();
          break;
        case SCHEME:
          pendingChecks =
              gerritChecksApi.pendingChecks().scheme(context.checksQueryString()).list();
          break;
        default:
          throw new IOException("Unknown query operator for querying pending checks.");
      }
    } catch (URISyntaxException | IOException | RestApiException e) {
      listener.getLogger().println("Unable to query for pending checks: " + e);
    }

    for (PendingChecksInfo check : pendingChecks) {
      if (check.patchSet == null) {
        continue;
      }
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

    return patchsetWithPendingChecks;
  }
}

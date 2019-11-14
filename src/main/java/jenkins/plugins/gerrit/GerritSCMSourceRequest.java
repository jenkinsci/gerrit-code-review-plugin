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

package jenkins.plugins.gerrit;

import com.google.gerrit.plugins.checks.api.PendingChecksInfo;
import com.google.gerrit.plugins.checks.client.GerritChecksApi;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.model.TaskListener;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import jenkins.plugins.gerrit.traits.FilterChecksTrait.ChecksQueryOperator;
import jenkins.plugins.git.GitSCMSourceRequest;
import org.eclipse.jgit.transport.URIish;

public class GerritSCMSourceRequest extends GitSCMSourceRequest {

  private final boolean filterForPendingChecks;

  private HashMap<String, PendingChecksInfo> patchsetWithPendingChecks =
      new HashMap<String, PendingChecksInfo>();

  public GerritSCMSourceRequest(
      GerritSCMSource source, GerritSCMSourceContext context, TaskListener listener) {
    super(source, context, listener);
    this.filterForPendingChecks = context.filterForPendingChecks();
    if (filterForPendingChecks) {
      try {
        this.patchsetWithPendingChecks = getChangesWithPendingChecks(source, context, listener);
      } catch (IOException e) {
        listener.getLogger().println("Unable to query for pending checks");
      }
    }
  }

  public HashMap<String, PendingChecksInfo> getPatchsetWithPendingChecks() {
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
  private HashMap<String, PendingChecksInfo> getChangesWithPendingChecks(
      GerritSCMSource source, GerritSCMSourceContext context, TaskListener listener)
      throws IOException {
    HashMap<String, PendingChecksInfo> patchsetWithPendingChecks =
        new HashMap<String, PendingChecksInfo>();

    GerritChecksApi gerritChecksApi = getGerritChecksApi(source, listener);
    Optional<List<PendingChecksInfo>> pendingChecks;
    if (context.checksQueryOperator() == ChecksQueryOperator.ID) {
      pendingChecks =
          Optional.of(
              gerritChecksApi.getChangesWithPendingChecksByCheckerId(context.checksQueryString()));
    } else if (context.checksQueryOperator() == ChecksQueryOperator.SCHEME) {
      pendingChecks =
          Optional.of(
              gerritChecksApi.getChangesWithPendingChecksByCheckerScheme(
                  context.checksQueryString()));
    } else {
      throw new IOException("Unknown query operator for querying pending checks.");
    }

    if (pendingChecks.isPresent()) {
      for (PendingChecksInfo check : pendingChecks.get()) {
        patchsetWithPendingChecks.put(
            String.format("%d/%d", check.patchSet.changeNumber, check.patchSet.patchSetId), check);
      }
    }

    return patchsetWithPendingChecks;
  }
}

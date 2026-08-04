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
import java.io.PrintStream;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.plugins.git.GitSCMSourceRequest;
import org.eclipse.jgit.transport.URIish;

public class GerritSCMSourceRequest extends GitSCMSourceRequest {
  private static final Logger LOGGER = Logger.getLogger(GerritSCMSourceRequest.class.getName());

  private final boolean filterForPendingChecks;

  private final Map<String, HashSet<PendingChecksInfo>> patchsetWithPendingChecks;

  public GerritSCMSourceRequest(
      GerritSCMSource source, GerritSCMSourceContext context, TaskListener listener) {
    super(source, context, normalizeListener(listener));
    TaskListener normalizedListener = normalizeListener(listener);
    this.filterForPendingChecks = context.filterForPendingChecks();
    this.patchsetWithPendingChecks =
        filterForPendingChecks
            ? getChangesWithPendingChecks(source, context, normalizedListener)
            : new HashMap<String, HashSet<PendingChecksInfo>>();
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
        new HashMap<String, HashSet<PendingChecksInfo>>();
    List<PendingChecksInfo> pendingChecks = new ArrayList<PendingChecksInfo>();

    try {
      pendingChecks = queryPendingChecks(getGerritChecksApi(source, listener), context);
    } catch (URISyntaxException | IOException | RestApiException e) {
      logPendingChecksFailure(listener, e);
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

  static TaskListener normalizeListener(TaskListener listener) {
    return listener == null ? TaskListener.NULL : listener;
  }

  static void logPendingChecksFailure(TaskListener listener, Exception failure) {
    PrintStream logger = normalizeListener(listener).getLogger();
    logger.println("Unable to query for pending checks: " + failure);
    failure.printStackTrace(logger);
  }

  static List<PendingChecksInfo> queryPendingChecks(
      GerritChecksApi gerritChecksApi, GerritSCMSourceContext context)
      throws IOException, RestApiException, URISyntaxException {
    Exception operationFailure = null;
    try {
      switch (context.checksQueryOperator()) {
        case ID:
          return gerritChecksApi.pendingChecks().checker(context.checksQueryString()).list();
        case SCHEME:
          return gerritChecksApi.pendingChecks().scheme(context.checksQueryString()).list();
        default:
          throw new IOException("Unknown query operator for querying pending checks.");
      }
    } catch (IOException | RestApiException | URISyntaxException | RuntimeException e) {
      operationFailure = e;
      throw e;
    } finally {
      try {
        gerritChecksApi.close();
      } catch (IOException | RuntimeException closeFailure) {
        if (operationFailure != null) {
          operationFailure.addSuppressed(closeFailure);
        } else {
          LOGGER.log(Level.WARNING, "Could not close Gerrit Checks HTTP client", closeFailure);
        }
      }
    }
  }
}

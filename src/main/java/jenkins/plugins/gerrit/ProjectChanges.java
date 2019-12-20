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

import com.google.common.annotations.VisibleForTesting;
import com.google.gerrit.extensions.api.GerritApi;
import com.google.gerrit.extensions.client.ListChangesOption;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.restapi.RestApiException;
import java.util.EnumSet;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

class ProjectChanges {
  private static final Logger LOGGER = Logger.getLogger(ProjectChanges.class.getName());

  private final GerritApi gerritApi;

  ProjectChanges(GerritApi gerritApi) {
    this.gerritApi = gerritApi;
  }

  public Optional<ChangeInfo> get(int changeNumber) {
    try {
      EnumSet<ListChangesOption> options = EnumSet.allOf(ListChangesOption.class);
      options.remove(ListChangesOption.CHECK);
      if (isVersionBelow215(gerritApi.config().server().getVersion())) {
        options.remove(ListChangesOption.TRACKING_IDS);
        options.remove(ListChangesOption.SKIP_MERGEABLE);
      }
      return Optional.ofNullable(gerritApi.changes().id(changeNumber).get(options));
    } catch (RestApiException e) {
      LOGGER.severe(String.format("Unable to retrieve change %d", changeNumber));
      LOGGER.throwing(ProjectChanges.class.getName(), "get", e);
      return Optional.empty();
    }
  }

  @VisibleForTesting
  boolean isVersionBelow215(String version) {
    if (version == null) {
      return false;
    }

    if (version.equals("<2.8")) {
      return true;
    }

    String[] versionSplit = version.split("\\.");
    if (versionSplit.length == 0) {
      return false;
    }
    try {
      int majorVersion = Integer.parseInt(versionSplit[0]);
      if (versionSplit.length >= 1 && majorVersion < 2) {
        return true;
      }
      if (versionSplit.length >= 1 && majorVersion > 2) {
        return false;
      }
      int minorVersion = Integer.parseInt(versionSplit[1]);
      if (versionSplit.length == 1 || minorVersion < 15) {
        return true;
      }
    } catch (NumberFormatException e) {
      LOGGER.log(Level.SEVERE, "Unable to part Gerrit version " + version, e);
      return false;
    }

    return false;
  }
}

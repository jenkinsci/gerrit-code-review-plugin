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

import com.google.gerrit.extensions.api.GerritApi;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.restapi.RestApiException;
import java.util.Optional;
import java.util.logging.Logger;

class ProjectChanges {
  private static final Logger LOGGER = Logger.getLogger(ChangeSCMHead.class.getName());

  private final GerritApi gerritApi;

  ProjectChanges(GerritApi gerritApi) {
    this.gerritApi = gerritApi;
  }

  public Optional<ChangeInfo> get(int changeNumber) {
    try {
      return Optional.ofNullable(gerritApi.changes().id(changeNumber).get());
    } catch (RestApiException e) {
      LOGGER.severe(String.format("Unable to retrieve change %d", changeNumber));
      LOGGER.throwing(ProjectChanges.class.getName(), "get", e);
      return Optional.empty();
    }
  }
}

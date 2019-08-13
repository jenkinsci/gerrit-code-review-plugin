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

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.gerrit.extensions.api.GerritApi;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.restapi.RestApiException;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

class ProjectOpenChanges {
  private static final Logger LOGGER = Logger.getLogger(ChangeSCMHead.class.getName());

  private final LoadingCache<Integer, ChangeInfo> openChanges;
  private final GerritApi gerritApi;

  ProjectOpenChanges(GerritApi gerritApi) {
    this.gerritApi = gerritApi;
    openChanges =
        CacheBuilder.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(24, TimeUnit.HOURS)
            .build(
                new CacheLoader<Integer, ChangeInfo>() {
                  public ChangeInfo load(Integer changeNum) throws RestApiException {
                    return gerritApi.changes().id(changeNum).get();
                  }
                });
  }

  public void add(ChangeInfo changeInfo) {
    openChanges.put(changeInfo._number, changeInfo);
  }

  public Optional<ChangeInfo> get(int changeNumber) {
    try {
      return Optional.ofNullable(openChanges.get(changeNumber));
    } catch (ExecutionException e) {
      LOGGER.severe(String.format("Unable to retrieve change %d", changeNumber));
      LOGGER.throwing(ProjectOpenChanges.class.getName(), "get", e);
      return Optional.empty();
    }
  }

  /*
   * TODO: Which one is the appropriate hook for running this method?
   */
  public void remove(int changeNumber) {
    openChanges.invalidate(changeNumber);
  }
}

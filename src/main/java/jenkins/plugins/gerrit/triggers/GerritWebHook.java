// Copyright (C) 2018 GerritForge Ltd
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

package jenkins.plugins.gerrit.triggers;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Sets;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import hudson.Extension;
import hudson.model.RootAction;
import hudson.model.UnprotectedRootAction;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Set;
import javax.servlet.http.HttpServletRequest;
import jenkins.model.Jenkins;
import jenkins.plugins.gerrit.GerritProjectEvent;
import org.kohsuke.stapler.Stapler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Extension
public class GerritWebHook implements UnprotectedRootAction, Trigger {
  private static final Logger log = LoggerFactory.getLogger(GerritWebHook.class);
  private static final Gson gson = new Gson();

  public static final String URLNAME = "gerrit-webhook";
  private static final Set<String> ALLOWED_TYPES =
      Sets.newHashSet(
          "ref-updated",
          "change-deleted",
          "change-abandoned",
          "change-merged",
          "change-restored",
          "patchset-created",
          "private-state-changed",
          "wip-state-changed");

  @Override
  public String getIconFileName() {
    return null;
  }

  @Override
  public String getDisplayName() {
    return null;
  }

  @Override
  public String getUrlName() {
    return URLNAME;
  }

  @SuppressWarnings({"unused", "deprecation"})
  public void doIndex() throws IOException {
    HttpServletRequest req = Stapler.getCurrentRequest();
    getBody(req).ifPresent(this::processEvent);
  }

  @VisibleForTesting
  Optional<GerritProjectEvent> getBody(HttpServletRequest req) throws IOException {
    try (InputStreamReader is =
        new InputStreamReader(req.getInputStream(), StandardCharsets.UTF_8)) {
      JsonObject eventJson = gson.fromJson(is, JsonObject.class);
      JsonPrimitive eventType = eventJson.getAsJsonPrimitive("type");
      if (eventType != null && ALLOWED_TYPES.contains(eventType.getAsString())) {
        return Optional.of(gson.fromJson(eventJson, GerritProjectEvent.class));
      }

      return Optional.empty();
    }
  }

  public static GerritWebHook get() {
    return Jenkins.getInstance().getExtensionList(RootAction.class).get(GerritWebHook.class);
  }
}

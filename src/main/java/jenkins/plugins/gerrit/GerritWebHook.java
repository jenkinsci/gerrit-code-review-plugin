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

package jenkins.plugins.gerrit;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Sets;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import hudson.Extension;
import hudson.model.RootAction;
import hudson.model.UnprotectedRootAction;
import hudson.security.ACL;
import hudson.security.ACLContext;
import hudson.util.Secret;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.servlet.http.HttpServletRequest;
import jenkins.model.Jenkins;
import jenkins.scm.api.SCMSourceOwner;
import org.acegisecurity.Authentication;
import org.apache.commons.codec.digest.DigestUtils;
import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject;
import org.kohsuke.stapler.Stapler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Extension
public class GerritWebHook implements UnprotectedRootAction {
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
    String jobName = req.getParameter("jobName");
    String apiKeyParam = req.getParameter("apiKey");

    getBody(req)
        .ifPresent(
            projectEvent -> {
              String username = "anonymous";
              Authentication authentication = getJenkinsInstance().getAuthentication();
              if (authentication != null) {
                username = authentication.getName();
              }

              log.info("GerritWebHook invoked by user '{}' for event: {}", username, projectEvent);

              try (ACLContext acl = ACL.as(ACL.SYSTEM)) {
                List<WorkflowMultiBranchProject> jenkinsItems =
                    getJenkinsInstance()
                        .getAllItems(WorkflowMultiBranchProject.class)
                        .stream()
                        .filter(job -> jobName == null || job.getName().equals(jobName))
                        .collect(Collectors.toList());
                if (jobName != null) {
                  if (jenkinsItems.isEmpty()) {
                    log.error("Job '{}' not found or not a multi-branch pipeline", jobName);
                    return;
                  }

                  if (jenkinsItems.size() > 1) {
                    log.error(
                        "Search for job '{}' is ambiguous and returned {} entries",
                        jobName,
                        jenkinsItems.size());
                    return;
                  }
                }

                log.info(
                    "Scanning {} Jenkins items {}",
                    jenkinsItems.size(),
                    jobName == null ? "" : "matching " + jobName);

                jenkinsItems.forEach(
                    scmJob ->
                        scmJob
                            .getSCMSources()
                            .stream()
                            .filter(GerritSCMSource.class::isInstance)
                            .map(GerritSCMSource.class::cast)
                            .forEach(
                                scmSource ->
                                    triggerScmSourceOnJob(
                                        apiKeyParam, projectEvent, scmJob, scmSource)));
              }
            });
  }

  private void triggerScmSourceOnJob(
      String apiKeyParam,
      GerritProjectEvent projectEvent,
      SCMSourceOwner scmJob,
      GerritSCMSource gerritSCMSource) {
    Secret gerritSCMSourceApiKey = gerritSCMSource.getApiKey();
    log.debug("Checking match for SCM source: " + gerritSCMSource.getRemote());
    if (!projectEvent.matches(gerritSCMSource.getRemote())) {
      log.warn(
          "Not triggering job {}: SCM source remote does not match the one specified in the project event",
          scmJob.getName());
      return;
    }
    if (gerritSCMSourceApiKey == null || gerritSCMSourceApiKey.getPlainText().isEmpty()) {
      log.warn("The apiKey secret was not configured in the SCM source or empty");
    } else if (apiKeyParam == null
        || !sameApiKeyMessageDigest(apiKeyParam, gerritSCMSourceApiKey.getPlainText())) {
      log.error(
          "Unable to trigger the SCM source because of the ApiKey provided in gerrit web-hook does not match the one configured in the source");
      return;
    }

    log.info("Triggering SCM event for source {} on job {}", gerritSCMSource, scmJob);
    scmJob.onSCMSourceUpdated(gerritSCMSource);
  }

  private boolean sameApiKeyMessageDigest(String apiKeyParam, String gerritSCMSourceApiKey) {
    return MessageDigest.isEqual(messageDigest(gerritSCMSourceApiKey), messageDigest(apiKeyParam));
  }

  private byte[] messageDigest(String plaintext) {
    return DigestUtils.sha256Hex(plaintext).getBytes(StandardCharsets.UTF_8);
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

  @Nonnull
  public static Jenkins getJenkinsInstance() throws IllegalStateException {
    return Jenkins.getInstance();
  }
}

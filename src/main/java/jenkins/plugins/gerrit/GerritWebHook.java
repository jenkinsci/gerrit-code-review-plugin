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
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.servlet.http.HttpServletRequest;
import jenkins.model.Jenkins;
import jenkins.scm.api.SCMSourceOwner;
import org.acegisecurity.Authentication;
import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject;
import org.kohsuke.stapler.Stapler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Extension
public class GerritWebHook implements UnprotectedRootAction {
  public static final String API_KEY_MD_ALGO = "SHA-256";
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
                if (jobName != null && jenkinsItems.isEmpty()) {
                  throw new IllegalStateException(
                      String.format("Job '%s' not found or not a multi-branch pipeline", jobName));
                }
                if (jobName != null && jenkinsItems.size() > 1) {
                  throw new IllegalStateException(
                      String.format(
                          "Search for job '%s' is ambiguous and returned %d entries",
                          jobName, jenkinsItems.size()));
                }
                log.info(
                    "Scanning {} Jenkins items {}",
                    jenkinsItems.size(),
                    jobName == null ? "" : "matching " + jobName);
                List<WorkflowMultiBranchProject> machingJobs =
                    jenkinsItems
                        .stream()
                        .filter(job -> jobName == null || job.getName().equals(jobName))
                        .collect(Collectors.toList());

                log.info("Found {} Jenkins mathcing jobs", machingJobs.size());
                for (SCMSourceOwner scmJob : machingJobs) {
                  List<GerritSCMSource> gerritScmSources =
                      scmJob
                          .getSCMSources()
                          .stream()
                          .filter(GerritSCMSource.class::isInstance)
                          .map(GerritSCMSource.class::cast)
                          .collect(Collectors.toList());
                  log.info(
                      "Scanning job {} with {} Gerrit SCM sources",
                      scmJob.getName(),
                      gerritScmSources.size());
                  for (GerritSCMSource scmSource : gerritScmSources) {
                    triggerScmSourceOnJob(apiKeyParam, projectEvent, scmJob, scmSource);
                  }
                }
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
      log.warn(
          "Not triggering job {}: apiKey was not configured in the SCM source or empty",
          scmJob.getName());
      return;
    }

    if (!sameApiKeyMessageDigest(apiKeyParam, gerritSCMSourceApiKey)) {
      throw new IllegalStateException(
          "Unable to trigger the SCM source because of the ApiKey provided in gerrit web-hook does not match the one configured in the source");
    }

    log.info("Triggering SCM event for source {} on job {}", gerritSCMSource, scmJob);
    scmJob.onSCMSourceUpdated(gerritSCMSource);
  }

  private boolean sameApiKeyMessageDigest(String apiKeyParam, Secret gerritSCMSourceApiKey) {
    return MessageDigest.isEqual(messageDigest(gerritSCMSourceApiKey), messageDigest(apiKeyParam));
  }

  private byte[] messageDigest(Secret secret) {
    return messageDigest(secret.getPlainText());
  }

  private byte[] messageDigest(String plaintext) {
    try {
      MessageDigest digest = MessageDigest.getInstance(API_KEY_MD_ALGO);
      return digest.digest(plaintext.getBytes(StandardCharsets.UTF_8));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
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

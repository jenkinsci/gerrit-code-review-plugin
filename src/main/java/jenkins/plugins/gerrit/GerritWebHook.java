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

import static hudson.model.Computer.threadPoolForRemoting;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import hudson.Extension;
import hudson.model.RootAction;
import hudson.model.UnprotectedRootAction;
import hudson.util.SequentialExecutionQueue;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.annotation.Nonnull;
import javax.servlet.http.HttpServletRequest;
import jenkins.model.Jenkins;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.SCMSourceOwner;
import org.acegisecurity.Authentication;
import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject;
import org.kohsuke.stapler.Stapler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Extension
public class GerritWebHook implements UnprotectedRootAction {
  private static final Logger log = LoggerFactory.getLogger(GerritWebHook.class);
  private static final Gson gson = new Gson();

  public static final String URLNAME = "gerrit-webhook";

  private final transient SequentialExecutionQueue queue =
      new SequentialExecutionQueue(threadPoolForRemoting);

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

  @SuppressWarnings("unused")
  public void doIndex() throws IOException {
    HttpServletRequest req = Stapler.getCurrentRequest();
    GerritProjectEvent projectEvent = getBody(req);

    if (projectEvent == null) {
      return;
    }

    String username = "anonymous";
    Authentication authentication = getJenkinsInstance().getAuthentication();
    if (authentication != null) {
      username = authentication.getName();
    }

    log.info("GerritWebHook invoked by user '{}' for event: {}", username, projectEvent);

    List<WorkflowMultiBranchProject> jenkinsItems =
        getJenkinsInstance().getAllItems(WorkflowMultiBranchProject.class);
    log.info("Scanning {} Jenkins items", jenkinsItems.size());
    for (SCMSourceOwner scmJob : jenkinsItems) {
      log.info("Scanning job " + scmJob);
      List<SCMSource> scmSources = scmJob.getSCMSources();
      for (SCMSource scmSource : scmSources) {
        if (scmSource instanceof GerritSCMSource) {
          GerritSCMSource gerritSCMSource = (GerritSCMSource) scmSource;
          log.debug("Checking match for SCM source: " + gerritSCMSource.getRemote());
          if (projectEvent.matches(gerritSCMSource.getRemote())) {
            log.info("Triggering SCM event for source " + scmSources.get(0) + " on job " + scmJob);
            scmJob.onSCMSourceUpdated(scmSource);
          }
        }
      }
    }
  }

  private GerritProjectEvent getBody(HttpServletRequest req) throws IOException {
    try (InputStreamReader is =
        new InputStreamReader(req.getInputStream(), StandardCharsets.UTF_8)) {
      StringBuilder stringBuilder = new StringBuilder();
      String line = null;
      try (BufferedReader br = new BufferedReader(is)) {
        while ((line = br.readLine()) != null) {
          stringBuilder.append(line);
        }
        String bodyString = stringBuilder.toString();
        log.info("Received body: " + bodyString);
        try {
          return gson.fromJson(bodyString, GerritProjectEvent.class);
        } catch (JsonSyntaxException e) {
          log.debug("Not a Gerrit 'Project' Event, ignoring: " + bodyString);
          return null;
        }
      }
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

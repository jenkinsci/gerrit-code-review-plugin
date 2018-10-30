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
import hudson.Extension;
import hudson.model.RootAction;
import hudson.model.UnprotectedRootAction;
import hudson.util.SequentialExecutionQueue;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.annotation.Nonnull;
import javax.servlet.http.HttpServletRequest;
import jenkins.model.Jenkins;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.SCMSourceOwner;
import org.apache.commons.io.IOUtils;
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

  @SuppressWarnings({"unused", "deprecation"})
  public void doIndex() throws IOException {
    HttpServletRequest req = Stapler.getCurrentRequest();
    GerritProjectEvent projectEvent = getBody(req);

    log.info("GerritWebHook invoked for event " + projectEvent);

    List<WorkflowMultiBranchProject> jenkinsItems =
        getJenkinsInstance().getItems(WorkflowMultiBranchProject.class);
    log.info("Scanning {} Jenkins items", jenkinsItems.size());
    for (SCMSourceOwner scmJob : jenkinsItems) {
      log.info("Scanning job " + scmJob);
      List<SCMSource> scmSources = scmJob.getSCMSources();
      for (SCMSource scmSource : scmSources) {
        if (scmSource instanceof GerritSCMSource) {
          GerritSCMSource gerritSCMSource = (GerritSCMSource) scmSource;
          if (projectEvent.matches(gerritSCMSource.getRemote())) {
            log.info("Triggering SCM event for source " + scmSources.get(0) + " on job " + scmJob);
            scmJob.onSCMSourceUpdated(scmSource);
          }
        }
      }
    }
  }

  private GerritProjectEvent getBody(HttpServletRequest req) throws IOException {
    char[] body = new char[req.getContentLength()];
    try (InputStreamReader is =
        new InputStreamReader(req.getInputStream(), StandardCharsets.UTF_8)) {
      IOUtils.readFully(is, body);
      String bodyString = new String(body);
      log.info("Received body: " + bodyString);
      return gson.fromJson(bodyString, GerritProjectEvent.class);
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

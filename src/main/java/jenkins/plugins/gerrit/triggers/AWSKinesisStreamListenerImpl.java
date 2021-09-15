// Copyright (C) 2021 GerritForge Ltd
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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import hudson.Extension;
import hudson.security.ACL;
import hudson.security.ACLContext;
import io.jenkins.plugins.aws.kinesisconsumer.extensions.AWSKinesisStreamListener;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import jenkins.plugins.gerrit.GerritProjectEvent;
import jenkins.plugins.gerrit.GerritProjectName;
import jenkins.plugins.gerrit.GerritSCMSource;
import jenkins.plugins.gerrit.RefUpdateProjectName;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.SCMSourceOwner;
import org.acegisecurity.Authentication;
import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject;

@Extension(optional = true)
public class AWSKinesisStreamListenerImpl extends AWSKinesisStreamListener implements Trigger {

  @Override
  public void onReceive(String streamName, byte[] bytes) {
    getGerritProjectEvent(bytes)
        .ifPresent(
            projectEvent -> {
              String username = "anonymous";
              Authentication authentication = Trigger.getJenkinsInstance().getAuthentication();
              if (authentication != null) {
                username = authentication.getName();
              }

              log.info("GerritWebHook invoked by user '{}' for event: {}", username, projectEvent);
              try (ACLContext acl = ACL.as(ACL.SYSTEM)) {
                List<WorkflowMultiBranchProject> jenkinsItems =
                    Trigger.getJenkinsInstance().getAllItems(WorkflowMultiBranchProject.class);
                log.info("Scanning {} Jenkins items", jenkinsItems.size());
                for (SCMSourceOwner scmJob : jenkinsItems) {
                  log.info("Scanning job " + scmJob);
                  List<SCMSource> scmSources = scmJob.getSCMSources();
                  for (SCMSource scmSource : scmSources) {
                    if (scmSource instanceof GerritSCMSource) {
                      GerritSCMSource gerritSCMSource = (GerritSCMSource) scmSource;
                      if (gerritSCMSource.getKinesisEnabled()
                          && gerritSCMSource.getStreamName().equalsIgnoreCase(streamName)) {
                        triggerSCMScan(projectEvent, gerritSCMSource, scmJob);
                      }
                    }
                  }
                }
              }
            });
  }

  Optional<GerritProjectEvent> getGerritProjectEvent(byte[] bytes) {
    ObjectMapper jsonMapper = new ObjectMapper();
    Map<String, String> stringStringMap;
    try {
      stringStringMap =
          jsonMapper.readValue(new String(bytes), new TypeReference<Map<String, String>>() {});
    } catch (Exception e) {
      return Optional.empty();
    }
    GerritProjectName gerritProjectName = new GerritProjectName(stringStringMap.get("project"));

    RefUpdateProjectName refUpdateProjectName =
        new RefUpdateProjectName(stringStringMap.get("ref"));

    String type = stringStringMap.get("type");

    // XXX Control that all the params are populated
    return Optional.of(new GerritProjectEvent(gerritProjectName, refUpdateProjectName, type));
  }
}

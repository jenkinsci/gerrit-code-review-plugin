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

import com.google.gerrit.extensions.client.ChangeKind;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.RevisionInfo;
import hudson.EnvVars;
import hudson.Extension;
import hudson.model.EnvironmentContributor;
import hudson.model.InvisibleAction;
import hudson.model.ItemGroup;
import hudson.model.Run;
import hudson.model.TaskListener;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nonnull;
import jenkins.branch.BranchSource;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject;

@Extension
public class GerritEnvironmentContributor extends EnvironmentContributor {

  private static final Pattern REF_PATTERN =
      Pattern.compile("^\\d+\\/(?<changeNum>\\d+)\\/(?<patchSet>\\d+)$");

  public static class ChangeInfoInvisibleAction extends InvisibleAction {
    Optional<ChangeInfo> maybeChangeInfo;

    ChangeInfoInvisibleAction(Optional<ChangeInfo> maybeChangeInfo) {
      this.maybeChangeInfo = maybeChangeInfo;
    }
  }

  @Override
  public void buildEnvironmentFor(
      @Nonnull Run r, @Nonnull EnvVars envs, @Nonnull TaskListener listener)
      throws IOException, InterruptedException {
    ItemGroup jobParent = r.getParent().getParent();
    if (!(jobParent instanceof WorkflowMultiBranchProject)) {
      return;
    }

    WorkflowMultiBranchProject multiBranchProject = (WorkflowMultiBranchProject) jobParent;
    List<BranchSource> sources = multiBranchProject.getSources();
    if (sources.isEmpty() || !(sources.get(0).getSource() instanceof GerritSCMSource)) {
      return;
    }

    WorkflowJob workflowJob = (WorkflowJob) r.getParent();

    GerritSCMSource gerritSCMSource =
        (GerritSCMSource) multiBranchProject.getSources().get(0).getSource();
    GerritURI gerritURI = gerritSCMSource.getGerritURI();

    envs.put("GERRIT_CREDENTIALS_ID", gerritSCMSource.getCredentialsId());
    envs.put("GERRIT_PROJECT", gerritURI.getProject());
    try {
      envs.put("GERRIT_API_URL", gerritURI.getApiURI().toString());
    } catch (URISyntaxException e) {
      throw new IOException("Unable to get Gerrit API URL from " + gerritURI, e);
    }
    if (Boolean.TRUE.equals(gerritSCMSource.getInsecureHttps())) {
      envs.put("GERRIT_API_INSECURE_HTTPS", "true");
    }

    String displayName = workflowJob.getDisplayName();
    Matcher matcher = REF_PATTERN.matcher(displayName);
    if (matcher.find()) {
      int patchSetNum = Integer.parseInt(matcher.group("patchSet"));

      Optional<ChangeInfo> changeInfo = Optional.empty();
      List<ChangeInfoInvisibleAction> changeInfos = r.getActions(ChangeInfoInvisibleAction.class);
      if (changeInfos.isEmpty()) {
        changeInfo = gerritSCMSource.getChangeInfo(Integer.parseInt(matcher.group("changeNum")));
        r.addAction(new ChangeInfoInvisibleAction(changeInfo));
      } else {
        changeInfo = changeInfos.get(0).maybeChangeInfo;
      }
      changeInfo.ifPresent(
          (change) -> {
            publishChangeDetails(
                envs,
                matcher.group("changeNum"),
                matcher.group("patchSet"),
                patchSetNum,
                change,
                gerritURI);
          });
    }
  }

  private void publishChangeDetails(
      @Nonnull EnvVars envs,
      String changeNum,
      String patchSet,
      int patchSetNum,
      ChangeInfo change,
      GerritURI gerritURI) {
    envs.put("GERRIT_CHANGE_NUMBER", changeNum);
    envs.put("GERRIT_PATCHSET_NUMBER", patchSet);
    envs.put("GERRIT_CHANGE_PRIVATE_STATE", booleanString(change.isPrivate));
    envs.put("GERRIT_CHANGE_WIP_STATE", booleanString(change.workInProgress));
    envs.put("GERRIT_CHANGE_SUBJECT", change.subject);
    envs.put("GERRIT_CHANGE_URL", gerritURI.setPath("" + change._number).toASCIIString());
    envs.put("GERRIT_BRANCH", change.branch);
    envs.put("GERRIT_TOPIC", nullToEmpty(change.topic));
    envs.put("GERRIT_CHANGE_ID", change.id);

    Map.Entry<String, RevisionInfo> patchSetInfo =
        change
            .revisions
            .entrySet()
            .stream()
            .filter(entry -> entry.getValue()._number == patchSetNum)
            .findFirst()
            .get();

    envs.put("GERRIT_REFNAME", patchSetInfo.getValue().ref);
    envs.put("GERRIT_REFSPEC", patchSetInfo.getValue().ref);
    envs.put(
        "GERRIT_PATCHSET_NO_CODE_CHANGE",
        booleanString(patchSetInfo.getValue().kind == ChangeKind.NO_CODE_CHANGE));
    envs.put("GERRIT_PATCHSET_REVISION", patchSetInfo.getKey());
    envs.put("GERRIT_CHANGE_OWNER", change.owner.name + " <" + change.owner.email + ">");
    envs.put("GERRIT_CHANGE_OWNER_NAME", change.owner.name);
    envs.put("GERRIT_CHANGE_OWNER_EMAIL", change.owner.email);

    AccountInfo uploader = patchSetInfo.getValue().uploader;
    envs.put("GERRIT_PATCHSET_UPLOADER", uploader.name + " <" + uploader.email + ">");
    envs.put("GERRIT_PATCHSET_UPLOADER_NAME", uploader.name);
    envs.put("GERRIT_PATCHSET_UPLOADER_EMAIL", uploader.email);
  }

  private String booleanString(Boolean booleanValue) {
    return Optional.ofNullable(booleanValue).orElse(Boolean.FALSE).toString();
  }

  private String nullToEmpty(String value) {
    return Optional.ofNullable(value).orElse("");
  }
}

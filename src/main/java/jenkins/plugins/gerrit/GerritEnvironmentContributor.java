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

import com.google.common.base.Strings;
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
import java.util.HashMap;
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
    private final Map<String, String> changeEnvs;

    ChangeInfoInvisibleAction(
        Optional<ChangeInfo> maybeChangeInfo, int patchSetNum, GerritURI gerritURI) {
      changeEnvs = new HashMap<>();

      maybeChangeInfo.ifPresent(
          (change) -> {
            changeEnvs.put("GERRIT_CHANGE_NUMBER", Integer.toString(change._number));
            changeEnvs.put("GERRIT_PATCHSET_NUMBER", Integer.toString(patchSetNum));
            changeEnvs.put(
                "GERRIT_CHANGE_PRIVATE_STATE",
                change.isPrivate != null ? Boolean.toString(change.isPrivate) : "false");
            changeEnvs.put(
                "GERRIT_CHANGE_WIP_STATE",
                change.workInProgress != null ? Boolean.toString(change.workInProgress) : "false");
            changeEnvs.put("GERRIT_CHANGE_SUBJECT", change.subject);
            changeEnvs.put(
                "GERRIT_CHANGE_URL", gerritURI.setPath("" + change._number).toASCIIString());
            changeEnvs.put("GERRIT_BRANCH", change.branch);
            changeEnvs.put("GERRIT_TOPIC", Strings.nullToEmpty(change.topic));
            changeEnvs.put("GERRIT_CHANGE_ID", change.id);

            Map.Entry<String, RevisionInfo> patchSetInfo =
                change
                    .revisions
                    .entrySet()
                    .stream()
                    .filter(entry -> entry.getValue()._number == patchSetNum)
                    .findFirst()
                    .get();

            changeEnvs.put("GERRIT_REFNAME", patchSetInfo.getValue().ref);
            changeEnvs.put("GERRIT_REFSPEC", patchSetInfo.getValue().ref);
            changeEnvs.put("GERRIT_PATCHSET_REVISION", patchSetInfo.getKey());
            changeEnvs.put(
                "GERRIT_CHANGE_OWNER", change.owner.name + " <" + change.owner.email + ">");
            changeEnvs.put("GERRIT_CHANGE_OWNER_NAME", change.owner.name);
            changeEnvs.put("GERRIT_CHANGE_OWNER_EMAIL", change.owner.email);

            AccountInfo uploader = patchSetInfo.getValue().uploader;
            changeEnvs.put("GERRIT_PATCHSET_UPLOADER", uploader.name + " <" + uploader.email + ">");
            changeEnvs.put("GERRIT_PATCHSET_UPLOADER_NAME", uploader.name);
            changeEnvs.put("GERRIT_PATCHSET_UPLOADER_EMAIL", uploader.email);
          });
    }

    public Map<String, String> getChangeEnvs() {
      return changeEnvs;
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

      Map<String, String> changeEnvs;
      List<ChangeInfoInvisibleAction> changeInfos = r.getActions(ChangeInfoInvisibleAction.class);
      if (changeInfos.isEmpty()) {
        int changeNumber = Integer.parseInt(matcher.group("changeNum"));
        Optional<ChangeInfo> changeInfo = gerritSCMSource.getChangeInfo(changeNumber, gerritURI.getProject());
        ChangeInfoInvisibleAction changeInfoAction =
            new ChangeInfoInvisibleAction(changeInfo, patchSetNum, gerritURI);
        r.addAction(changeInfoAction);
        changeEnvs = changeInfoAction.getChangeEnvs();
      } else {
        changeEnvs = changeInfos.get(0).getChangeEnvs();
      }

      envs.putAll(changeEnvs);
    }
  }

  private String booleanString(Boolean booleanValue) {
    return Optional.ofNullable(booleanValue).orElse(Boolean.FALSE).toString();
  }

  private String nullToEmpty(String value) {
    return Optional.ofNullable(value).orElse("");
  }
}

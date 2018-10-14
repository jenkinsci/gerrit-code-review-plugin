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

import hudson.EnvVars;
import hudson.Extension;
import hudson.model.*;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;
import javax.annotation.Nonnull;
import jenkins.branch.BranchSource;
import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject;

@Extension
public class GerritEnvironmentContributor extends EnvironmentContributor {

  @Override
  public void buildEnvironmentFor(
      @Nonnull Job j, @Nonnull EnvVars envs, @Nonnull TaskListener listener)
      throws IOException, InterruptedException {
    ItemGroup jobParent = j.getParent();
    if (!(jobParent instanceof WorkflowMultiBranchProject)) {
      return;
    }

    WorkflowMultiBranchProject multiBranchProject = (WorkflowMultiBranchProject) jobParent;
    List<BranchSource> sources = multiBranchProject.getSources();
    if (sources.isEmpty() || !(sources.get(0).getSource() instanceof GerritSCMSource)) {
      return;
    }

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
  }
}

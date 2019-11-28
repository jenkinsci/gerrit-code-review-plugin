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

import hudson.model.TaskListener;
import hudson.plugins.git.GitException;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.UserRemoteConfig;
import hudson.plugins.git.extensions.GitSCMExtension;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Arrays;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.URIish;
import org.jenkinsci.plugins.gitclient.FetchCommand;
import org.jenkinsci.plugins.gitclient.GitClient;

public class GerritFetchChangeSCMExtension extends GitSCMExtension {

  private final UserRemoteConfig urc;

  GerritFetchChangeSCMExtension(UserRemoteConfig changeRemoteConfig) {
    this.urc = changeRemoteConfig;
  }

  @Override
  public void decorateFetchCommand(
      GitSCM scm, GitClient git, TaskListener listener, FetchCommand cmd)
      throws IOException, InterruptedException, GitException {
    try {
      cmd.from(new URIish(urc.getUrl()), Arrays.asList(new RefSpec(urc.getRefspec())));
    } catch (URISyntaxException e) {
      throw new IOException(e);
    }
  }
}

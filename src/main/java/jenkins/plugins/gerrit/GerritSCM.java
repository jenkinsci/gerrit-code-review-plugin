package jenkins.plugins.gerrit;

import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.plugins.git.BranchSpec;
import hudson.plugins.git.GitException;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.SubmoduleConfig;
import hudson.plugins.git.UserRemoteConfig;
import hudson.plugins.git.browser.GitRepositoryBrowser;
import hudson.plugins.git.extensions.GitSCMExtension;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import jenkins.scm.api.SCMHead;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.URIish;
import org.jenkinsci.plugins.gitclient.GitClient;

public class GerritSCM extends GitSCM {

  static class FetchChangeExtension extends GitSCMExtension {
    private final SCMHead head;

    FetchChangeExtension(SCMHead head) {
      this.head = head;
    }

    @Override
    public void beforeCheckout(GitSCM scm, Run<?, ?> build, GitClient git, TaskListener listener)
        throws IOException, InterruptedException, GitException {

      for (UserRemoteConfig uc : scm.getUserRemoteConfigs()) {
        String remoteUrl = uc.getUrl();
        String remoteName = uc.getName();
        try {
          RefSpec changeRefSpec = new RefSpec("refs/changes/" + head.getName() + ":refs/remotes/" + remoteName + "/" + head.getName());
          git.fetch_().from(new URIish(remoteUrl), Arrays.asList(changeRefSpec)).execute();
        } catch (URISyntaxException e) {
          throw new IOException(e);
        }
      }
    }
  }

  public GerritSCM(
      SCMHead scmHead,
      List<UserRemoteConfig> userRemoteConfigs,
      List<BranchSpec> branches,
      Boolean doGenerateSubmoduleConfigurations,
      Collection<SubmoduleConfig> submoduleCfg,
      GitRepositoryBrowser browser, String gitTool) {
    super(userRemoteConfigs, branches, doGenerateSubmoduleConfigurations, submoduleCfg, browser,
        gitTool, Arrays.asList(new FetchChangeExtension(scmHead)));
  }
}

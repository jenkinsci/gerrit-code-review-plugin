package jenkins.plugins.gerrit;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import javax.annotation.CheckForNull;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.trait.SCMNavigatorRequest;

public class GerritSCMSourceFactory implements SCMNavigatorRequest.SourceLambda {

  private final GerritURI gerritURI;
  private final boolean insecureHttps;
  @CheckForNull private final String credentialsId;

  public GerritSCMSourceFactory(
      GerritURI gerritURI, boolean insecureHttps, @CheckForNull String credentialsId) {
    this.gerritURI = gerritURI;
    this.insecureHttps = insecureHttps;
    this.credentialsId = credentialsId;
  }

  @NonNull
  @Override
  public SCMSource create(@NonNull String projectName) throws IOException, InterruptedException {
    GerritSCMSource scmSource = new GerritSCMSource(gerritURI.getPrefix() + "/" + projectName);
    scmSource.setCredentialsId(credentialsId);
    scmSource.setInsecureHttps(insecureHttps);
    return scmSource;
  }
}

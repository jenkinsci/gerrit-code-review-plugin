package jenkins.plugins.gerrit;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.net.URISyntaxException;
import javax.annotation.CheckForNull;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.trait.SCMNavigatorRequest;
import org.eclipse.jgit.transport.URIish;

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
    URIish projectUri;
    try {
      projectUri = gerritURI.setProject(projectName);
    } catch (URISyntaxException e) {
      throw new IOException(e);
    }

    GerritSCMSource scmSource = new GerritSCMSource(projectUri.toString());
    scmSource.setCredentialsId(credentialsId);
    scmSource.setInsecureHttps(insecureHttps);
    return scmSource;
  }
}

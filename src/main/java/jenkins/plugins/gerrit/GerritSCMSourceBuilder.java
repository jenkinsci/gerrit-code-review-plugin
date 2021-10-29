package jenkins.plugins.gerrit;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.net.URISyntaxException;
import javax.annotation.CheckForNull;
import jenkins.scm.api.trait.SCMSourceBuilder;
import org.eclipse.jgit.transport.URIish;

/** @author Réda Housni Alaoui */
public class GerritSCMSourceBuilder
    extends SCMSourceBuilder<GerritSCMSourceBuilder, GerritSCMSource> {

  private final String id;
  private final GerritURI gerritURI;
  private final boolean insecureHttps;
  @CheckForNull private final String credentialsId;

  public GerritSCMSourceBuilder(
      String id,
      @NonNull String projectName,
      GerritURI gerritURI,
      boolean insecureHttps,
      @CheckForNull String credentialsId) {
    super(GerritSCMSource.class, projectName);
    this.id = id;
    this.gerritURI = gerritURI;
    this.insecureHttps = insecureHttps;
    this.credentialsId = credentialsId;
  }

  @NonNull
  @Override
  public GerritSCMSource build() {
    URIish projectUri;
    try {
      projectUri = gerritURI.setProject(projectName());
    } catch (URISyntaxException e) {
      throw new IllegalStateException(e);
    }

    GerritSCMSource scmSource = new GerritSCMSource(projectUri.toString());
    scmSource.setId(id);
    scmSource.setCredentialsId(credentialsId);
    scmSource.setInsecureHttps(insecureHttps);
    scmSource.setTraits(traits());
    return scmSource;
  }
}

package jenkins.plugins.gerrit;

import static java.util.Optional.ofNullable;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;
import com.google.gerrit.extensions.api.GerritApi;
import com.google.gerrit.extensions.common.ProjectInfo;
import hudson.Extension;
import hudson.model.Item;
import hudson.model.Queue;
import hudson.model.queue.Tasks;
import hudson.security.ACL;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import jenkins.model.Jenkins;
import jenkins.scm.api.SCMNavigator;
import jenkins.scm.api.SCMNavigatorDescriptor;
import jenkins.scm.api.SCMSourceObserver;
import jenkins.scm.api.trait.SCMNavigatorRequest;
import jenkins.scm.api.trait.SCMNavigatorTrait;
import org.apache.commons.lang.StringUtils;
import org.eclipse.jgit.transport.URIish;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

public class GerritSCMNavigator extends SCMNavigator {

  @CheckForNull private final String serverUrl;
  private final boolean insecureHttps;
  @CheckForNull private final String credentialsId;
  @Nonnull private final List<SCMNavigatorTrait> traits;

  @DataBoundConstructor
  public GerritSCMNavigator(
      String serverUrl,
      boolean insecureHttps,
      String credentialsId,
      List<SCMNavigatorTrait> traits) {
    this.serverUrl = StringUtils.trim(serverUrl);
    this.insecureHttps = insecureHttps;
    this.credentialsId = credentialsId;
    this.traits =
        ofNullable(traits).map(Collections::unmodifiableList).orElseGet(Collections::emptyList);
  }

  @Nonnull
  @Override
  protected String id() {
    Map<String, String> attributes = new HashMap<>();
    attributes.put("server-url", serverUrl);
    attributes.put("credentials-id", credentialsId);

    return attributes.entrySet().stream()
        .map(attribute -> attribute.getKey() + "=" + attribute.getValue())
        .collect(Collectors.joining("::"));
  }

  @Override
  public void visitSources(@Nonnull SCMSourceObserver observer)
      throws IOException, InterruptedException {
    GerritURI gerritURI;
    GerritApi gerritApi;
    try {
      gerritURI = getGerritURI();
      gerritApi = createGerritApiBuilder(observer).build();
    } catch (URISyntaxException e) {
      throw new IOException(e);
    }

    try (GerritSCMNavigatorRequest request =
        new GerritSCMNavigatorRequest(
            this, new GerritSCMNavigatorContext().withTraits(traits), observer)) {

      SCMNavigatorRequest.SourceLambda sourceLambda =
          projectName ->
              new GerritSCMSourceBuilder(
                      getId() + "::" + projectName,
                      projectName,
                      gerritURI,
                      insecureHttps,
                      credentialsId)
                  .withRequest(request)
                  .build();

      PagedCodeProjectsRequest projectsRequest = new PagedCodeProjectsRequest(gerritApi);
      for (ProjectInfo projectInfo : projectsRequest) {
        boolean stop = request.process(projectInfo.name, sourceLambda, null);
        if (stop) {
          break;
        }
        checkInterrupt();
      }
    }
  }

  private GerritApiBuilder createGerritApiBuilder(@Nonnull SCMSourceObserver observer)
      throws URISyntaxException {
    return new GerritApiBuilder()
        .logger(observer.getListener().getLogger())
        .gerritApiUrl(getGerritApiURI())
        .insecureHttps(insecureHttps)
        .credentials(lookupCredentials(observer.getContext()));
  }

  @CheckForNull
  private StandardUsernamePasswordCredentials lookupCredentials(Item context) {
    if (credentialsId == null) {
      return null;
    }
    return CredentialsMatchers.firstOrNull(
        CredentialsProvider.lookupCredentials(
            StandardUsernamePasswordCredentials.class,
            context,
            ACL.SYSTEM,
            URIRequirementBuilder.fromUri(serverUrl).build()),
        CredentialsMatchers.allOf(CredentialsMatchers.withId(credentialsId)));
  }

  private URIish getGerritApiURI() throws URISyntaxException {
    return getGerritURI().getApiURI();
  }

  private GerritURI getGerritURI() throws URISyntaxException {
    return new GerritURI(new URIish(serverUrl));
  }

  @Extension
  public static final class DescriptorImpl extends SCMNavigatorDescriptor {

    @Override
    public String getDisplayName() {
      return "Gerrit Server";
    }

    @Override
    public SCMNavigator newInstance(String name) {
      return null;
    }

    public FormValidation doCheckServerUrl(@QueryParameter String value) {
      try {
        new GerritURI(new URIish(value)).getApiURI();
      } catch (URISyntaxException e) {
        return FormValidation.error(e.getMessage());
      }
      return FormValidation.ok();
    }

    public ListBoxModel doFillCredentialsIdItems(
        @AncestorInPath Item context,
        @QueryParameter String serverUrl,
        @QueryParameter String credentialsId) {
      if (context == null && !Jenkins.getInstance().hasPermission(Jenkins.ADMINISTER)
          || context != null && !context.hasPermission(Item.EXTENDED_READ)) {
        return new StandardListBoxModel().includeCurrentValue(credentialsId);
      }
      return new StandardListBoxModel()
          .includeEmptyValue()
          .includeMatchingAs(
              context instanceof Queue.Task
                  ? Tasks.getAuthenticationOf((Queue.Task) context)
                  : ACL.SYSTEM,
              context,
              StandardUsernameCredentials.class,
              URIRequirementBuilder.fromUri(serverUrl).build(),
              GitClient.CREDENTIALS_MATCHER)
          .includeCurrentValue(credentialsId);
    }
  }

  @CheckForNull
  public String getServerUrl() {
    return serverUrl;
  }

  public boolean isInsecureHttps() {
    return insecureHttps;
  }

  @CheckForNull
  public String getCredentialsId() {
    return credentialsId;
  }

  @Nonnull
  public List<SCMNavigatorTrait> getTraits() {
    return traits;
  }
}

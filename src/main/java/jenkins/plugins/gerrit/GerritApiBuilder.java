package jenkins.plugins.gerrit;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.google.gerrit.extensions.api.GerritApi;
import com.urswolfer.gerrit.client.rest.GerritRestApiFactory;
import com.urswolfer.gerrit.client.rest.GerritAuthData;
import com.urswolfer.gerrit.client.rest.http.HttpClientBuilderExtension;
import hudson.EnvVars;
import hudson.model.Run;
import hudson.model.TaskListener;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.jgit.transport.URIish;
import org.jenkinsci.plugins.workflow.steps.StepContext;

/**
 * A wrapper on top of GerritApi.
 * Enables common functionality.
 */
public class GerritApiBuilder {

  private PrintStream logger;
  private URIish gerritApiUrl;
  private Boolean insecureHttps;
  private String username;
  private String password;

  public GerritApiBuilder logger(PrintStream logger) {
    this.logger = logger;
    return this;
  }

  public GerritApiBuilder gerritApiUrl(URIish gerritApiUrl) {
    this.gerritApiUrl = gerritApiUrl;
    return this;
  }

  public GerritApiBuilder gerritApiUrl(String gerritApiUrl) throws URISyntaxException {
    if (gerritApiUrl == null) {
      this.gerritApiUrl = null;
    }
    else {
      gerritApiUrl(new URIish(gerritApiUrl));
    }
    return this;
  }

  public GerritApiBuilder insecureHttps(Boolean insecureHttps) {
    this.insecureHttps = insecureHttps;
    return this;
  }

  public GerritApiBuilder credentials(String username, String password) {
    this.username = username;
    this.password = password;
    return this;
  }

  public GerritApiBuilder credentials(StandardUsernamePasswordCredentials credentials) {
    if (credentials != null) {
      username = credentials.getUsername();
      password = credentials.getPassword().getPlainText();
    }
    return this;
  }

  public GerritApiBuilder stepContext(StepContext context) throws URISyntaxException, IOException, InterruptedException {
    EnvVars envVars = context.get(EnvVars.class);
    logger(context.get(TaskListener.class).getLogger());
    if (envVars.containsKey("GERRIT_API_URL")) {
      gerritApiUrl(envVars.get("GERRIT_API_URL"));
    }
    else if (envVars.containsKey("GERRIT_CHANGE_URL")) {
      gerritApiUrl(new GerritURI(new URIish(envVars.get("GERRIT_CHANGE_URL"))).getApiURI());
    }
    insecureHttps(Boolean.parseBoolean(envVars.get("GERRIT_API_INSECURE_HTTPS")));
    String credentialsId = envVars.get("GERRIT_CREDENTIALS_ID");
    if (credentialsId != null) {
      credentials(CredentialsProvider.findCredentialById(credentialsId,
        StandardUsernamePasswordCredentials.class, context.get(Run.class)));
    }
    return this;
  }

  public GerritApi build() {
    GerritApi gerritApi = null;
    if (gerritApiUrl == null) {
      logger.println("Gerrit Review is disabled no API URL");
    }
    else if (username == null) {
      logger.println("Gerrit Review is disabled no credentials");
    }
    else {
      GerritAuthData.Basic authData =
          new GerritAuthData.Basic(gerritApiUrl.toString(), username, password);
      List<HttpClientBuilderExtension> extensions = new ArrayList<>();
      if (Boolean.TRUE.equals(insecureHttps)) {
        extensions.add(SSLNoVerifyCertificateManagerClientBuilderExtension.INSTANCE);
      }
      gerritApi = new GerritRestApiFactory()
              .create(authData, extensions.toArray(new HttpClientBuilderExtension[0]));
    }
    return gerritApi;
  }
}

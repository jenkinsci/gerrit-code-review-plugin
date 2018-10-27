package jenkins.plugins.gerrit;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.urswolfer.gerrit.client.rest.GerritAuthData;
import com.urswolfer.gerrit.client.rest.GerritRestApi;
import com.urswolfer.gerrit.client.rest.GerritRestApiFactory;
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
 * A wrapper on top of GerritRestApi.
 * Enables common functionality.
 */
public class GerritRestApiBuilder {

  private PrintStream logger;
  private URIish gerritApiUrl;
  private Boolean insecureHttps;
  private String username;
  private String password;

  public GerritRestApiBuilder logger(PrintStream logger) {
    this.logger = logger;
    return this;
  }

  public GerritRestApiBuilder gerritApiUrl(URIish gerritApiUrl) {
    this.gerritApiUrl = gerritApiUrl;
    return this;
  }

  public GerritRestApiBuilder gerritApiUrl(String gerritApiUrl) throws URISyntaxException {
    if (gerritApiUrl == null) {
      this.gerritApiUrl = null;
    }
    else {
      gerritApiUrl(new URIish(gerritApiUrl));
    }
    return this;
  }

  public GerritRestApiBuilder insecureHttps(Boolean insecureHttps) {
    this.insecureHttps = insecureHttps;
    return this;
  }

  public GerritRestApiBuilder credentials(String username, String password) {
    this.username = username;
    this.password = password;
    return this;
  }

  public GerritRestApiBuilder credentials(StandardUsernamePasswordCredentials credentials) {
    if (credentials != null) {
      username = credentials.getUsername();
      password = credentials.getPassword().getPlainText();
    }
    return this;
  }

  public GerritRestApiBuilder stepContext(StepContext context) throws URISyntaxException, IOException, InterruptedException {
    EnvVars envVars = context.get(EnvVars.class);
    logger(context.get(TaskListener.class).getLogger());
    gerritApiUrl(envVars.get("GERRIT_API_URL"));
    insecureHttps(Boolean.parseBoolean(envVars.get("GERRIT_API_INSECURE_HTTPS")));
    String credentialsId = envVars.get("GERRIT_CREDENTIALS_ID");
    if (credentialsId != null) {
      credentials(CredentialsProvider.findCredentialById(credentialsId,
        StandardUsernamePasswordCredentials.class, context.get(Run.class)));
    }
    return this;
  }

  public GerritRestApi build() {
    GerritRestApi gerritRestApi = null;
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
      gerritRestApi = new GerritRestApiFactory()
              .create(authData, extensions.toArray(new HttpClientBuilderExtension[0]));
    }
    return gerritRestApi;
  }
}

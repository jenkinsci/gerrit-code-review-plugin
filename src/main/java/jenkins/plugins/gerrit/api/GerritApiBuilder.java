package jenkins.plugins.gerrit.api;

import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.google.gerrit.extensions.api.GerritApi;
import com.urswolfer.gerrit.client.rest.GerritAuthData;
import com.urswolfer.gerrit.client.rest.GerritRestApiFactory;
import com.urswolfer.gerrit.client.rest.http.HttpClientBuilderExtension;
import hudson.EnvVars;
import hudson.model.Run;
import hudson.model.TaskListener;
import jenkins.plugins.gerrit.GerritURI;
import jenkins.plugins.gerrit.SSLNoVerifyCertificateManagerClientBuilderExtension;
import jenkins.plugins.gerrit.UsernamePasswordCredentialsProvider;
import jenkins.plugins.gerrit.api.ssh.GerritApiSSH;
import org.apache.sshd.client.SshClient;
import org.eclipse.jgit.transport.URIish;
import org.jenkinsci.plugins.workflow.steps.StepContext;

import java.io.IOException;
import java.io.PrintStream;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

/**
 * A wrapper on top of GerritRestApi.
 * Enables common functionality.
 */
public class GerritApiBuilder {

  private PrintStream logger;
  private Boolean insecureHttps;
  private URIish gerritApiUrl;
  private UsernamePasswordCredentialsProvider credentialsProvider;
  private static final int SSH_TIMEOUT_MS = 30000;


  public GerritApiBuilder logger(PrintStream logger) {
    this.logger = logger;
    return this;
  }

  public GerritApiBuilder gerritApiUrl(URIish gerritApiUrl) {
    this.gerritApiUrl = gerritApiUrl;
    return this;
  }

  private GerritApiBuilder gerritApiUrl(String gerritApiUrl) throws URISyntaxException {
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

  public GerritApiBuilder credentialsProvider(UsernamePasswordCredentialsProvider credentialsProvider) {
    this.credentialsProvider = credentialsProvider;
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
      StandardUsernamePasswordCredentials credentials = com.cloudbees.plugins.credentials.CredentialsProvider.findCredentialById(credentialsId,
              StandardUsernamePasswordCredentials.class, context.get(Run.class));
      credentialsProvider(new UsernamePasswordCredentialsProvider(credentials));
    }
    return this;
  }

  public GerritApi build() {
    if (gerritApiUrl == null) {
      logger.println("Gerrit Review is disabled no API URL");
      return null;
    }
    if (credentialsProvider == null) {
      logger.println("Gerrit Review is disabled no credentials");
      return null;
    }
    UsernamePasswordCredentialsProvider.UsernamePassword usernamePassword = credentialsProvider.getUsernamePassword(gerritApiUrl);
    if (usernamePassword.username == null) {
      logger.println("Gerrit Review is disabled no credentials");
      return null;
    }
    boolean useRest;
    switch (gerritApiUrl.getScheme()) {
      case "http":
      case "https":
        useRest = true;
        break;
      case "ssh":
        useRest = false;
        break;
      default:
        throw new IllegalStateException("Unknown scheme " + gerritApiUrl.getScheme());
    }
    if (useRest) {
      GerritAuthData.Basic authData =
              new GerritAuthData.Basic(gerritApiUrl.toString(), usernamePassword.username, usernamePassword.password);
      List<HttpClientBuilderExtension> extensions = new ArrayList<>();
      if (Boolean.TRUE.equals(insecureHttps)) {
        extensions.add(SSLNoVerifyCertificateManagerClientBuilderExtension.INSTANCE);
      }
      return new GerritRestApiFactory()
              .create(authData, extensions.toArray(new HttpClientBuilderExtension[0]));
    } else {
      //todo: SSHCredentialsPlugin
      return new GerritApiSSH(SshClient.setUpDefaultClient(), gerritApiUrl, SSH_TIMEOUT_MS);
    }
  }
}

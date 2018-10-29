package jenkins.plugins.gerrit;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.google.gerrit.extensions.api.GerritApi;
import com.urswolfer.gerrit.client.rest.GerritAuthData;
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
import jenkins.plugins.gerrit.api.ssh.GerritApiSSH;
import org.apache.sshd.client.SshClient;
import org.eclipse.jgit.transport.URIish;
import org.jenkinsci.plugins.workflow.steps.StepContext;

/** A wrapper on top of GerritApi. Enables common functionality. */
public class GerritApiBuilder {

  private PrintStream logger = System.out;
  private URIish gerritApiUrl;
  private Boolean insecureHttps;
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
    } else {
      gerritApiUrl(new URIish(gerritApiUrl));
    }
    return this;
  }

  public GerritApiBuilder insecureHttps(Boolean insecureHttps) {
    this.insecureHttps = insecureHttps;
    return this;
  }

  public GerritApiBuilder credentialsProvider(
      UsernamePasswordCredentialsProvider credentialsProvider) {
    this.credentialsProvider = credentialsProvider;
    return this;
  }

  public GerritApiBuilder stepContext(StepContext context)
      throws URISyntaxException, IOException, InterruptedException {
    EnvVars envVars = context.get(EnvVars.class);
    logger(context.get(TaskListener.class).getLogger());
    if (envVars.containsKey("GERRIT_API_URL")) {
      gerritApiUrl(envVars.get("GERRIT_API_URL"));
    } else if (envVars.containsKey("GERRIT_CHANGE_URL")) {
      gerritApiUrl(new GerritURI(new URIish(envVars.get("GERRIT_CHANGE_URL"))).getApiURI());
    }
    insecureHttps(Boolean.parseBoolean(envVars.get("GERRIT_API_INSECURE_HTTPS")));
    String credentialsId = envVars.get("GERRIT_CREDENTIALS_ID");
    if (credentialsId != null) {
      StandardUsernamePasswordCredentials credentials =
          CredentialsProvider.findCredentialById(
              credentialsId, StandardUsernamePasswordCredentials.class, context.get(Run.class));
      if (credentials != null) {
        credentialsProvider(new UsernamePasswordCredentialsProvider(credentials));
      }
    }
    return this;
  }

  public GerritApi build() {
    List<HttpClientBuilderExtension> extensions = new ArrayList<>();
    GerritAuthData authData;
    if (gerritApiUrl == null) {
      logger.println("Gerrit Review is disabled no API URL");
      return null;
    } else if (credentialsProvider == null) {
      logger.println("Gerrit Review is disabled no credentials");
      authData = new AnonymousAuth(gerritApiUrl.toString());
    } else {
      UsernamePasswordCredentialsProvider.UsernamePassword usernamePassword =
          credentialsProvider.getUsernamePassword(gerritApiUrl);
      authData =
          new GerritAuthData.Basic(
              gerritApiUrl.toString(), usernamePassword.username, usernamePassword.password);
      if (Boolean.TRUE.equals(insecureHttps)) {
        extensions.add(SSLNoVerifyCertificateManagerClientBuilderExtension.INSTANCE);
      }
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
        throw new IllegalStateException(
            String.format("Unknown scheme %s", gerritApiUrl.getScheme()));
    }
    if (useRest) {
      return new GerritRestApiFactory()
          .create(authData, extensions.toArray(new HttpClientBuilderExtension[0]));
    } else {
      // todo: handle credentials, including SSHCredentialsPlugin
      return new GerritApiSSH(SshClient.setUpDefaultClient(), gerritApiUrl, SSH_TIMEOUT_MS);
    }
  }

  public boolean isAnonymous() {
    return gerritApiUrl == null || credentialsProvider == null;
  }

  public static class AnonymousAuth implements GerritAuthData {
    private final String gerritApiUrl;

    public AnonymousAuth(String gerritApiUrl) {
      this.gerritApiUrl = gerritApiUrl;
    }

    @Override
    public String getLogin() {
      return null;
    }

    @Override
    public String getPassword() {
      return null;
    }

    @Override
    public boolean isHttpPassword() {
      return false;
    }

    @Override
    public String getHost() {
      return gerritApiUrl;
    }

    @Override
    public boolean isLoginAndPasswordAvailable() {
      return false;
    }
  }
}

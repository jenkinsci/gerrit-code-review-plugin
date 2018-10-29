package jenkins.plugins.gerrit;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.google.common.base.Strings;
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
import org.apache.commons.lang.StringUtils;
import org.apache.sshd.client.SshClient;
import org.eclipse.jgit.transport.URIish;
import org.jenkinsci.plugins.workflow.steps.StepContext;

/** A wrapper on top of GerritApi. Enables common functionality. */
public class GerritApiBuilder {

  private PrintStream logger = System.out;
  private URIish gerritApiUrl;
  private Boolean insecureHttps;
  private boolean requireAuthentication;
  private String username;
  private String password;

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

  public GerritApiBuilder requireAuthentication() {
    this.requireAuthentication = true;
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
    if (StringUtils.isNotEmpty(envVars.get("GERRIT_API_URL"))) {
      gerritApiUrl(envVars.get("GERRIT_API_URL"));
    } else if (StringUtils.isNotEmpty(envVars.get("GERRIT_CHANGE_URL"))) {
      gerritApiUrl(new GerritURI(new URIish(envVars.get("GERRIT_CHANGE_URL"))).getApiURI());
    }
    insecureHttps(Boolean.parseBoolean(envVars.get("GERRIT_API_INSECURE_HTTPS")));
    String credentialsId = StringUtils.defaultIfEmpty(envVars.get("GERRIT_CREDENTIALS_ID"), null);
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
    GerritApi gerritApi = null;
    if (gerritApiUrl == null) {
      logger.println("Gerrit Review is disabled no API URL");
    } else if (requireAuthentication && Strings.isNullOrEmpty(username)) {
      logger.println(
          "Gerrit Review requires authentication, however there are no credentials defined or are empty.");
    } else {
      List<HttpClientBuilderExtension> extensions = new ArrayList<>();
      if (Boolean.TRUE.equals(insecureHttps)) {
        extensions.add(SSLNoVerifyCertificateManagerClientBuilderExtension.INSTANCE);
      }
      gerritApi =
          new GerritRestApiFactory()
              .create(
                  new GerritAuthData.Basic(gerritApiUrl.toString(), username, password),
                  extensions.toArray(new HttpClientBuilderExtension[0]));
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
      return gerritApi;
    } else {
      // todo: handle credentials, including SSHCredentialsPlugin
      return new GerritApiSSH(SshClient.setUpDefaultClient(), gerritApiUrl, SSH_TIMEOUT_MS);
    }
  }

  @Override
  public String toString() {
    return gerritApiUrl == null ? "null" : gerritApiUrl.toString();
  }
}

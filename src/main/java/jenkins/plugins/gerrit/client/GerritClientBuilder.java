package jenkins.plugins.gerrit.client;

import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Logger;
import com.urswolfer.gerrit.client.rest.GerritAuthData;
import com.urswolfer.gerrit.client.rest.GerritRestApi;
import com.urswolfer.gerrit.client.rest.GerritRestApiFactory;
import com.urswolfer.gerrit.client.rest.http.HttpClientBuilderExtension;
import hudson.EnvVars;
import hudson.model.Run;
import hudson.model.TaskListener;
import jenkins.plugins.gerrit.GerritURI;
import jenkins.plugins.gerrit.SSLNoVerifyCertificateManagerClientBuilderExtension;
import jenkins.plugins.gerrit.UsernamePasswordCredentialsProvider;
import jenkins.plugins.gerrit.client.rest.GerritClientRest;
import jenkins.plugins.gerrit.client.ssh.GerritClientSSH;
import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.transport.RemoteSession;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.util.FS;
import org.jenkinsci.plugins.gitclient.trilead.TrileadSessionFactory;
import org.jenkinsci.plugins.workflow.steps.StepContext;

import java.io.IOException;
import java.io.PrintStream;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * A wrapper on top of GerritRestApi.
 * Enables common functionality.
 */
public class GerritClientBuilder {

  private PrintStream logger;
  private Boolean insecureHttps;
  private URIish gerritApiUrl;
  private UsernamePasswordCredentialsProvider credentialsProvider;
  private static final int SSH_TIMEOUT_MS = 30000;


  public GerritClientBuilder logger(PrintStream logger) {
    this.logger = logger;
    return this;
  }

  public GerritClientBuilder gerritApiUrl(URIish gerritApiUrl) {
    this.gerritApiUrl = gerritApiUrl;
    return this;
  }

  private GerritClientBuilder gerritApiUrl(String gerritApiUrl) throws URISyntaxException {
    if (gerritApiUrl == null) {
      this.gerritApiUrl = null;
    }
    else {
      gerritApiUrl(new URIish(gerritApiUrl));
    }
    return this;
  }

  public GerritClientBuilder insecureHttps(Boolean insecureHttps) {
    this.insecureHttps = insecureHttps;
    return this;
  }

  public GerritClientBuilder credentialsProvider(UsernamePasswordCredentialsProvider credentialsProvider) {
    this.credentialsProvider = credentialsProvider;
    return this;
  }

  public GerritClientBuilder stepContext(StepContext context) throws URISyntaxException, IOException, InterruptedException {
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

  public GerritClient build() throws IOException {
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
      GerritRestApi gerritRestApi = new GerritRestApiFactory()
              .create(authData, extensions.toArray(new HttpClientBuilderExtension[0]));
      return new GerritClientRest(gerritRestApi, gerritApiUrl.getScheme());
    } else {
      RemoteSession remoteSession;
      try {

        //todo: JSch.setConfig //Strict Auth should be optional
        //todo: Prettier logging
        //todo: SSHCredentialsPlugin

        java.util.logging.Logger sshLogger = java.util.logging.Logger.getLogger("SSH");
        sshLogger.setLevel(Level.ALL);
        JSch.setLogger(new Logger() {
          @Override
          public boolean isEnabled(int level) {
            Level l = getLevel(level);
            return sshLogger.isLoggable(l);
          }

          @Override
          public void log(int level, String message) {
            Level l = getLevel(level);
            sshLogger.log(l, message);
          }

          private Level getLevel(int level) {
            switch (level) {
              case 0:
                return Level.FINE;
              case 1:
                return Level.INFO;
              case 2:
                return Level.WARNING;
              case 3:
                return Level.SEVERE;
              case 4:
                return Level.SEVERE;
              default:
                return Level.INFO;
            }
          }
        });
        remoteSession = TrileadSessionFactory.getInstance().getSession(gerritApiUrl, credentialsProvider, FS.DETECTED, SSH_TIMEOUT_MS);
      } catch (TransportException e) {
        throw new IOException(e);
      }
      return new GerritClientSSH(remoteSession, SSH_TIMEOUT_MS);
    }
  }
}

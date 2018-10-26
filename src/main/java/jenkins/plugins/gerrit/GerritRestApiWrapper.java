package jenkins.plugins.gerrit;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gson.JsonElement;
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
public class GerritRestApiWrapper {

  private PrintStream logger;
  private URIish gerritApiUrl;
  private GerritRestApi gerritRestApi;

  GerritRestApiWrapper(PrintStream logger, URIish gerritApiUrl, Boolean insecureHttps,
      String username, String password) {

    this.logger = logger;
    this.gerritApiUrl = gerritApiUrl; // no toString at GerritRestApi

    GerritAuthData.Basic authData =
        new GerritAuthData.Basic(gerritApiUrl.toString(), username, password);
    List<HttpClientBuilderExtension> extensions = new ArrayList<>();
    if (Boolean.TRUE.equals(insecureHttps)) {
      extensions.add(SSLNoVerifyCertificateManagerClientBuilderExtension.INSTANCE);
    }
    gerritRestApi = new GerritRestApiFactory()
            .create(authData, extensions.toArray(new HttpClientBuilderExtension[0]));
  }

  public GerritRestApi getGerritRestApi() {
    return gerritRestApi;
  }

  public JsonElement postRequest(String path, String payload) throws RestApiException {
    preRequest(path, "POST", payload);
    JsonElement response = gerritRestApi.restClient().postRequest(path, payload);
    postRequest(response);
    return response;
  }

  public JsonElement putRequest(String path, String payload) throws RestApiException {
    preRequest(path, "PUT", payload);
    JsonElement response = gerritRestApi.restClient().putRequest(path, payload);
    postRequest(response);
    return response;
  }

  private void preRequest(String path, String method, String payload) {
    logger.format("Gerrit Review to '%s%s' %s payload: %s%n", gerritApiUrl, path, method, payload);
  }

  private void postRequest(Object response) {
    logger.format("Result: %s%n", response);
  }

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {
    private PrintStream logger;
    private URIish gerritApiUrl;
    private Boolean insecureHttps;
    private String username;
    private String password;

    private Builder() {
    }

    public Builder logger(PrintStream logger) {
      this.logger = logger;
      return this;
    }

    public Builder gerritApiUrl(URIish gerritApiUrl) {
      this.gerritApiUrl = gerritApiUrl;
      return this;
    }

    public Builder gerritApiUrl(String gerritApiUrl) throws URISyntaxException {
      if (gerritApiUrl == null) {
        this.gerritApiUrl = null;
      }
      else {
        gerritApiUrl(new URIish(gerritApiUrl));
      }
      return this;
    }

    public Builder insecureHttps(Boolean insecureHttps) {
      this.insecureHttps = insecureHttps;
      return this;
    }

    public Builder credentials(String username, String password) {
      this.username = username;
      this.password = password;
      return this;
    }

    public Builder credentials(StandardUsernamePasswordCredentials credentials) {
      if (credentials != null) {
        username = credentials.getUsername();
        password = credentials.getPassword().getPlainText();
      }
      return this;
    }

    public Builder stepContext(StepContext context) throws URISyntaxException, IOException, InterruptedException {
      EnvVars envVars = context.get(EnvVars.class);
      logger(context.get(TaskListener.class).getLogger());
      gerritApiUrl(envVars.get("GERRIT_Api_URL"));
      insecureHttps(Boolean.parseBoolean(envVars.get("GERRIT_Api_INSECURE_HTTPS")));
      String credentialsId = envVars.get("GERRIT_CREDENTIALS_ID");
      if (credentialsId != null) {
        credentials(CredentialsProvider.findCredentialById(credentialsId,
          StandardUsernamePasswordCredentials.class, context.get(Run.class)));
      }
      return this;
    }

    public GerritRestApiWrapper build() {
      GerritRestApiWrapper wrapper = null;
      if (gerritApiUrl == null) {
        logger.println("Gerrit Review is disabled no API URL");
      }
      else if (username == null) {
        logger.println("Gerrit Review is disabled no credentials");
      }
      else {
        wrapper = new GerritRestApiWrapper(logger, gerritApiUrl, insecureHttps, username, password);
      }
      return wrapper;
    }
  }
}

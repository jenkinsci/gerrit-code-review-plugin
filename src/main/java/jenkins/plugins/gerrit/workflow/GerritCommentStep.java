// Copyright (C) 2018 GerritForge Ltd
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package jenkins.plugins.gerrit.workflow;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gson.JsonElement;
import com.urswolfer.gerrit.client.rest.GerritAuthData;
import com.urswolfer.gerrit.client.rest.GerritRestApi;
import com.urswolfer.gerrit.client.rest.GerritRestApiFactory;
import hudson.EnvVars;
import hudson.Extension;
import hudson.model.Run;
import hudson.model.TaskListener;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nonnull;
import jenkins.plugins.gerrit.SSLNoVerifyCertificateManagerClientBuilderExtension;
import org.eclipse.jgit.transport.URIish;
import org.jenkinsci.plugins.workflow.steps.*;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

public class GerritCommentStep extends Step {
  private String path;
  private int line;
  private String message;

  @DataBoundConstructor
  public GerritCommentStep(String path, String message) {
    this.path = path;
    this.message = message;
  }

  public int getLine() {
    return line;
  }

  @DataBoundSetter
  public void setLine(int line) {
    this.line = line;
  }

  public class Execution extends SynchronousStepExecution<Void> {
    private final TaskListener listener;
    private final EnvVars envVars;
    private final Run run;

    protected Execution(@Nonnull StepContext context) throws IOException, InterruptedException {
      super(context);

      this.envVars = context.get(EnvVars.class);
      this.listener = getContext().get(TaskListener.class);
      this.run = getContext().get(Run.class);
    }

    @Override
    protected Void run() throws Exception {
      String gerritApiUrl = envVars.get("GERRIT_API_URL");
      String credentialsId = envVars.get("GERRIT_CREDENTIALS_ID");
      String branch = envVars.get("BRANCH_NAME");

      if (gerritApiUrl == null) {
        echo("GERRIT_API_URL is not available, disabling Gerrit integration");
        return null;
      }

      Pattern changeBranchPattern = Pattern.compile("([0-9][0-9])/([0-9]+)/([0-9]+)");
      Matcher matcher = changeBranchPattern.matcher(branch);
      if (matcher.matches()) {
        int changeId = Integer.parseInt(matcher.group(2));
        int patchsetId = Integer.parseInt(matcher.group(3));
        echo("Gerrit comment on change %d %s:%d (%s)", changeId, path, line, message);

        String jsonPayload =
            "{\"path\": \""
                + path
                + "\", "
                + " \"line\": "
                + line
                + ", "
                + "\"message\": \""
                + message
                + "\" }";

        echo("POST %s to Gerrit", jsonPayload);
        put(
            credentialsId,
            gerritApiUrl,
            "/changes/" + changeId + "/revisions/" + patchsetId + "/drafts",
            jsonPayload);
      }
      return null;
    }

    private void echo(String fmt, Object... args) {
      String msg = String.format(fmt, args);
      listener.getLogger().println(msg);
    }

    private void put(String credentialsId, String remoteUrl, String path, String jsonPayload)
        throws URISyntaxException, RestApiException {
      if (credentialsId != null) {
        StandardUsernamePasswordCredentials credentials =
            CredentialsProvider.findCredentialById(
                credentialsId, StandardUsernamePasswordCredentials.class, run);
        gerritApiPut(
            new URIish(remoteUrl),
            path,
            credentials.getUsername(),
            credentials.getPassword().getPlainText(),
            jsonPayload);
      } else {
        echo(
            "*WARNING* NO feedback sent to Gerrit because of missing credentials for ${it.getUrl()}");
      }
    }

    private JsonElement gerritApiPut(
        URIish uri, String path, String username, String password, String jsonPayload)
        throws URISyntaxException, RestApiException {
      echo("PUT Gerrit Review %s to %s%s", jsonPayload, uri, path);
      GerritAuthData.Basic authData =
          new GerritAuthData.Basic(uri.toString(), username, password);
      GerritRestApi gerritApi =
          new GerritRestApiFactory()
              .create(authData, SSLNoVerifyCertificateManagerClientBuilderExtension.INSTANCE);
      JsonElement result = gerritApi.restClient().putRequest(path, jsonPayload);
      echo("Result: %s", result);
      return result;
    }
  }

  @Override
  public StepExecution start(StepContext stepContext) throws Exception {
    return new GerritCommentStep.Execution(stepContext);
  }

  @Extension
  public static class DescriptorImpl extends StepDescriptor {

    @Override
    public Set<Class<?>> getRequiredContext() {
      return Collections.emptySet();
    }

    @Override
    public String getFunctionName() {
      return "gerritComment";
    }

    @Nonnull
    @Override
    public String getDisplayName() {
      return "Gerrit Review Comment";
    }
  }
}

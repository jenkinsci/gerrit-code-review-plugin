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

public class GerritReviewStep extends Step {
  private String label = "Verified";
  private int score;
  private String message = "";

  @DataBoundConstructor
  public GerritReviewStep() {}

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
      String branch = envVars.get("BRANCH_NAME");
      Pattern changeBranchPattern = Pattern.compile("([0-9][0-9])/([0-9]+)/([0-9]+)");
      Matcher matcher = changeBranchPattern.matcher(branch);
      if (matcher.matches()) {
        int changeId = Integer.parseInt(matcher.group(2));
        int patchsetId = Integer.parseInt(matcher.group(3));
        echo("Gerrit review change %d label %s / %d (%s)", changeId, label, score, message);

        String credentialsId = envVars.get("GERRIT_CREDENTIALS_ID");
        String gerritApiUrl = envVars.get("GERRIT_API_URL");
        String notify = score < 0 ? ", \"notify\" : \"OWNER\"" : "";
        String jsonPayload =
            "{\"labels\":{\""
                + label
                + "\":"
                + score
                + "},"
                + " \"message\": \""
                + message
                + "\""
                + notify
                + ", "
                + "\"drafts\": \"PUBLISH\", "
                + ciTag(label)
                + " }";

        echo("POST %s to Gerrit", jsonPayload);
        post(
            credentialsId,
            gerritApiUrl,
            "/changes/" + changeId + "/revisions/" + patchsetId + "/review",
            jsonPayload);
      }
      return null;
    }

    private void echo(String fmt, Object... args) {
      String msg = String.format(fmt, args);
      listener.getLogger().println(msg);
    }

    private String ciTag(String operation) {
      return " \"tag\" : \"autogenerated:jenkins:" + operation + "\" ";
    }

    private void post(String credentialsId, String remoteUrl, String path, String jsonPayload)
        throws URISyntaxException, RestApiException {
      if (credentialsId != null) {
        StandardUsernamePasswordCredentials credentials =
            CredentialsProvider.findCredentialById(
                credentialsId, StandardUsernamePasswordCredentials.class, run);
        gerritApiPost(
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

    private JsonElement gerritApiPost(
        URIish uri, String path, String username, String password, String jsonPayload)
        throws URISyntaxException, RestApiException {
      echo("Posting Gerrit Review %s to %s%s", jsonPayload, uri, path);
      GerritAuthData.Basic authData =
          new GerritAuthData.Basic(uri.toString(), username, password);
      GerritRestApi gerritApi =
          new GerritRestApiFactory()
              .create(authData, SSLNoVerifyCertificateManagerClientBuilderExtension.INSTANCE);
      JsonElement result = gerritApi.restClient().postRequest(path, jsonPayload);
      echo("Result: %s", result);
      return result;
    }
  }

  public int getScore() {
    return score;
  }

  @DataBoundSetter
  public void setScore(int score) {
    this.score = score;
  }

  public String getMessage() {
    return message;
  }

  @DataBoundSetter
  public void setMessage(String message) {
    this.message = message;
  }

  public String getLabel() {
    return label;
  }

  @DataBoundSetter
  public void setLabel(String label) {
    this.label = label;
  }

  @Override
  public StepExecution start(StepContext stepContext) throws Exception {
    return new Execution(stepContext);
  }

  @Extension
  public static class DescriptorImpl extends StepDescriptor {

    @Override
    public Set<Class<?>> getRequiredContext() {
      return Collections.emptySet();
    }

    @Override
    public String getFunctionName() {
      return "gerritReview";
    }

    @Nonnull
    @Override
    public String getDisplayName() {
      return "Gerrit Review Label";
    }
  }
}

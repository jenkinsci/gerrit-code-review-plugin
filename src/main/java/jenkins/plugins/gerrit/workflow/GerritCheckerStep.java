// Copyright (C) 2019 SAP SE
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

import com.google.gerrit.extensions.restapi.RestApiException;
import hudson.EnvVars;
import hudson.Extension;
import hudson.model.Run;
import hudson.model.TaskListener;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nonnull;
import jenkins.model.Jenkins;
import jenkins.plugins.gerrit.GerritApiBuilder;
import jenkins.plugins.gerrit.checks.api.BlockingCondition;
import jenkins.plugins.gerrit.checks.api.CheckerInput;
import jenkins.plugins.gerrit.checks.api.CheckerStatus;
import jenkins.plugins.gerrit.checks.client.GerritChecksApi;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.steps.SynchronousStepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

public class GerritCheckerStep extends Step {
  private String uuid;
  private String name;
  private String description;
  private CheckerStatus status;
  private EnumSet<BlockingCondition> blocking;
  private String query;

  @DataBoundConstructor
  public GerritCheckerStep() {}

  public class Execution extends SynchronousStepExecution<Void> {
    private final Pattern JOB_URL_PATTERN =
        Pattern.compile("^(?<multiBranchUrl>https?:\\/\\/[^\\/]+\\/(.*?)job\\/[^\\/]+)");

    private final TaskListener listener;

    protected Execution(StepContext context)
        throws IOException, InterruptedException, URISyntaxException {
      super(context);
      this.listener = getContext().get(TaskListener.class);
    }

    @Override
    protected Void run() throws Exception {
      CheckerInput checker = new CheckerInput();
      checker.uuid = uuid;
      checker.name = name;
      checker.description = description;
      checker.status = status;
      checker.blocking = blocking;
      checker.query = query;

      createOrUpdateChecker(checker);
      return null;
    }

    void createOrUpdateChecker(CheckerInput checker) throws InterruptedException, IOException {
      try {
        GerritChecksApi gerritChecksApi =
            new GerritApiBuilder()
                .stepContext(getContext())
                .requireAuthentication()
                .buildChecksApi();
        if (gerritChecksApi == null) {
          return;
        }
        EnvVars envVars = getContext().get(EnvVars.class);
        checker.repository = envVars.get("GERRIT_PROJECT");
        try {
          String jobUrl = getJobUrl(getContext());
          Matcher matcher = JOB_URL_PATTERN.matcher(jobUrl);
          if (!matcher.find()) {
            throw new IOException(String.format("Invalid job URL %s", jobUrl));
          }
          checker.url = matcher.group("multiBranchUrl") + "/";
        } catch (NullPointerException | IOException e) {
          listener.getLogger().println("Could not set checker URL.");
        }

        try {
          gerritChecksApi.checkers().get(checker.uuid);
          listener
              .getLogger()
              .println(
                  String.format(
                      "Checker %s already exists. Updating it to current configuration.",
                      checker.uuid));
          gerritChecksApi.checkers().update(checker);
          listener.getLogger().println("Updated checker " + checker.uuid);
        } catch (RestApiException e) {
          gerritChecksApi.checkers().create(checker);
          listener.getLogger().println("Created checker " + checker.uuid);
        }
      } catch (IOException | RestApiException | URISyntaxException e) {
        listener.getLogger().println("Could not create checker " + checker.uuid);
        throw new IOException("Failed to create checker: ", e);
      }
    }
  }

  public String getUuid() {
    return uuid;
  }

  @DataBoundSetter
  public void setUuid(String uuid) {
    this.uuid = uuid;
  }

  public String getName() {
    return name;
  }

  @DataBoundSetter
  public void setName(String name) {
    this.name = name;
  }

  public String getDescription() {
    return description;
  }

  @DataBoundSetter
  public void setDescription(String description) {
    this.description = description;
  }

  public CheckerStatus getStatus() {
    return status;
  }

  @DataBoundSetter
  public void setStatus(CheckerStatus status) {
    this.status = status;
  }

  public EnumSet<BlockingCondition> getBlocking() {
    return blocking;
  }

  @DataBoundSetter
  public void setBlocking(List<BlockingCondition> blocking) {
    this.blocking = EnumSet.copyOf(blocking);
  }

  public String getQuery() {
    return query;
  }

  @DataBoundSetter
  public void setQuery(String query) {
    this.query = query;
  }

  @Override
  public StepExecution start(StepContext stepContext) throws Exception {
    return new Execution(stepContext);
  }

  private String getJobUrl(StepContext stepContext) throws IOException, InterruptedException {
    String rootUrl = Jenkins.getInstance().getRootUrl();
    if (rootUrl == null) {
      throw new NullPointerException("Jenkins URL has to be set in the Jenkins configuration.");
    }
    return rootUrl + stepContext.get(Run.class).getUrl();
  }

  @Extension
  public static class DescriptorImpl extends StepDescriptor {

    @Override
    public Set<Class<?>> getRequiredContext() {
      Set<Class<?>> requiredContext = new HashSet<Class<?>>();
      requiredContext.add(EnvVars.class);
      requiredContext.add(Run.class);

      return requiredContext;
    }

    @Override
    public String getFunctionName() {
      return "gerritChecker";
    }

    @Nonnull
    @Override
    public String getDisplayName() {
      return "Gerrit Create Checker";
    }
  }
}

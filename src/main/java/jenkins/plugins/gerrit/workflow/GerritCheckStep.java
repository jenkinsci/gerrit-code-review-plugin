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

import com.google.gerrit.plugins.checks.api.CheckInput;
import com.google.gerrit.plugins.checks.api.CheckState;
import com.google.gerrit.plugins.checks.client.GerritChecksApi;
import hudson.Extension;
import hudson.model.Run;
import hudson.model.TaskListener;
import java.io.IOException;
import java.net.URISyntaxException;
import java.sql.Timestamp;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nonnull;
import jenkins.model.Jenkins;
import jenkins.plugins.gerrit.GerritApiBuilder;
import jenkins.plugins.gerrit.GerritChange;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.steps.SynchronousStepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

public class GerritCheckStep extends Step {
  private Map<String, String> checks;
  private String message = "";
  private String url;

  @DataBoundConstructor
  public GerritCheckStep() {}

  public class Execution extends SynchronousStepExecution<Void> {
    private final TaskListener listener;
    private final String consoleLogUri;

    protected Execution(StepContext context)
        throws IOException, InterruptedException, URISyntaxException {
      super(context);
      this.listener = getContext().get(TaskListener.class);
      this.consoleLogUri = getConsoleLogUri(context);
    }

    @Override
    protected Void run() throws Exception {
      GerritChecksApi gerritChecksApi =
          new GerritApiBuilder().stepContext(getContext()).requireAuthentication().buildChecksApi();
      if (gerritChecksApi == null) {
        return null;
      }
      GerritChange change = new GerritChange(getContext());
      if (change.valid()) {
        listener
            .getLogger()
            .format(
                "Gerrit review change %d/%d checks %s (%s)%n",
                change.getChangeId(), change.getRevision(), checks, message);
        if (checks != null) {
          for (Map.Entry<String, String> check : checks.entrySet()) {
            CheckInput input = new CheckInput();
            input.checkerUuid = check.getKey();
            input.state = CheckState.valueOf(check.getValue());
            input.message = message;
            input.url = url != null ? url : consoleLogUri;
            input = setCheckTimestamps(input, input.state);
            gerritChecksApi
                .checks()
                .change(change.getChangeId())
                .patchSet(change.getRevision())
                .update(input);
          }
        }
      }
      return null;
    }
  }

  public Map<String, String> getChecks() {
    return checks;
  }

  @DataBoundSetter
  public void setChecks(Map<String, String> checks) {
    this.checks = checks;
  }

  public String getMessage() {
    return message;
  }

  @DataBoundSetter
  public void setMessage(String message) {
    this.message = message;
  }

  public String getUrl() {
    return url;
  }

  @DataBoundSetter
  public void setUrl(String url) {
    this.url = url;
  }

  @Override
  public StepExecution start(StepContext stepContext) throws Exception {
    return new Execution(stepContext);
  }

  private String getConsoleLogUri(StepContext stepContext)
      throws IOException, InterruptedException {
    String rootUrl = Jenkins.getInstance().getRootUrl();
    if (rootUrl == null) {
      throw new NullPointerException("Jenkins URL has to be set in the Jenkins configuration.");
    }
    return rootUrl + stepContext.get(Run.class).getUrl() + "console";
  }

  private CheckInput setCheckTimestamps(CheckInput input, CheckState state) {
    Timestamp now = new Timestamp(System.currentTimeMillis());
    switch (state) {
      case RUNNING:
        input.started = now;
        break;
      case SUCCESSFUL:
      case FAILED:
        input.finished = now;
        break;
      case NOT_STARTED:
      case NOT_RELEVANT:
      case SCHEDULED:
      default:
        break;
    }

    return input;
  }

  @Extension
  public static class DescriptorImpl extends StepDescriptor {

    @Override
    public Set<Class<?>> getRequiredContext() {
      return Collections.emptySet();
    }

    @Override
    public String getFunctionName() {
      return "gerritCheck";
    }

    @Nonnull
    @Override
    public String getDisplayName() {
      return "Gerrit Review Check";
    }
  }
}

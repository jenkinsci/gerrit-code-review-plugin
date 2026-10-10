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

import com.google.gerrit.extensions.api.GerritApi;
import com.google.gerrit.extensions.api.changes.ChangeApi;
import com.google.gerrit.extensions.api.changes.Changes;
import com.google.gerrit.extensions.api.changes.DraftInput;
import com.google.gerrit.extensions.api.changes.RevisionApi;
import com.google.gerrit.extensions.restapi.RestApiException;
import hudson.Extension;
import hudson.model.TaskListener;
import java.util.Collections;
import java.util.Set;
import javax.annotation.Nonnull;
import jenkins.plugins.gerrit.GerritApiBuilder;
import jenkins.plugins.gerrit.GerritChange;
import jenkins.plugins.gerrit.GerritVersion;
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

  public static class Execution extends SynchronousNonBlockingStepExecution<Void> {
    private static final long serialVersionUID = 1L;

    private final String path;
    private final int line;
    private final String message;

    protected Execution(@Nonnull StepContext context, String path, int line, String message) {
      super(context);
      this.path = path;
      this.line = line;
      this.message = message;
    }

    @Override
    protected Void run() throws Exception {
      TaskListener listener = getContext().get(TaskListener.class);
      GerritApi gerritApi =
          new GerritApiBuilder().stepContext(getContext()).requireAuthentication().build();
      if (gerritApi == null) {
        return null;
      }

      GerritChange change = new GerritChange(getContext());
      if (change.valid()) {
        listener
            .getLogger()
            .format(
                "Gerrit review change %d/%d %s=%d (%s)%n",
                change.getChangeId(), change.getRevision(), path, line, message);
        DraftInput draftInput = new DraftInput();
        draftInput.path = path;
        draftInput.line = line;
        draftInput.message = message;
        RevisionApi revisionApi = getChangeApi(gerritApi, change).revision(change.getRevision());
        GerritStepInterruption.checkBeforeMutation();
        revisionApi.createDraft(draftInput);
      }
      return null;
    }
  }

  private static ChangeApi getChangeApi(GerritApi gerritApi, GerritChange change)
      throws RestApiException {
    Changes changesApi = gerritApi.changes();
    return GerritVersion.isVersionBelow215(gerritApi)
        ? changesApi.id(change.getChangeId())
        : changesApi.id(change.getProject(), change.getChangeId());
  }

  @Override
  public StepExecution start(StepContext stepContext) throws Exception {
    return new Execution(stepContext, path, line, message);
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

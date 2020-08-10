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
import com.google.gerrit.extensions.api.changes.DraftInput;
import hudson.EnvVars;
import hudson.Extension;
import hudson.model.TaskListener;
import java.io.IOException;
import java.util.Collections;
import java.util.Set;
import javax.annotation.Nonnull;
import jenkins.plugins.gerrit.GerritApiBuilder;
import jenkins.plugins.gerrit.GerritChange;
import org.jenkinsci.plugins.workflow.steps.*;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

public class GerritCommentStep extends Step {

  private String path;
  private String message;
  private CommentLocation location;

  @DataBoundConstructor
  public GerritCommentStep(String path, String message) {
    this.path = path;
    this.message = message;
    this.location = new CommentFile();
  }

  /**
   * Set line of comment. This is a helper property setter to maintain compatibility with earlier
   * releases: setLocation() should normally be used instead.
   *
   * @param line Line where to post the comment.
   */
  @DataBoundSetter
  public void setLine(int line) {
    this.location = new CommentLine(line);
  }

  public CommentLocation getLocation() {
    return location;
  }

  @DataBoundSetter
  public void setLocation(CommentLocation location) {
    this.location = location;
  }

  public class Execution extends SynchronousStepExecution<Void> {
    private final TaskListener listener;
    private final EnvVars envVars;

    protected Execution(@Nonnull StepContext context) throws IOException, InterruptedException {
      super(context);

      this.envVars = context.get(EnvVars.class);
      this.listener = getContext().get(TaskListener.class);
    }

    @Override
    protected Void run() throws Exception {
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
                "Gerrit review change %d/%d %s=%s (%s)%n",
                change.getChangeId(), change.getRevision(), path, location, message);
        DraftInput draftInput = new DraftInput();
        draftInput.path = path;
        draftInput.message = message;
        location.apply(draftInput);
        gerritApi
            .changes()
            .id(change.getChangeId())
            .revision(change.getRevision())
            .createDraft(draftInput);
      }
      return null;
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

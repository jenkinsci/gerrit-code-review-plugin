package jenkins.plugins.gerrit.workflow;

import com.google.gerrit.extensions.api.changes.DraftInput;
import hudson.Extension;
import hudson.model.Descriptor;
import javax.annotation.Nonnull;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

public class CommentFile extends CommentLocation {
  @DataBoundConstructor
  public CommentFile() {}

  @Override
  public String toString() {
    return "File";
  }

  @Override
  public void apply(DraftInput draftInput) {
    draftInput.line = 0;
  }

  @Symbol("file")
  @Extension
  public static class DescriptorImpl extends Descriptor<CommentLocation> {
    @Nonnull
    @Override
    public String getDisplayName() {
      return "Comment File";
    }
  }
}

package jenkins.plugins.gerrit.workflow;

import com.google.gerrit.extensions.api.changes.DraftInput;
import hudson.Extension;
import hudson.model.Descriptor;
import javax.annotation.Nonnull;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

public class CommentLine extends CommentLocation {
  private int line;

  @DataBoundConstructor
  public CommentLine(int line) {
    this.line = line;
  }

  @Override
  public void apply(DraftInput draftInput) {
    draftInput.line = line;
  }

  @Override
  public String toString() {
    return String.format("Line:%s", line);
  }

  @Symbol("line")
  @Extension
  public static class DescriptorImpl extends Descriptor<CommentLocation> {
    @Nonnull
    @Override
    public String getDisplayName() {
      return "Comment Line";
    }
  }
}

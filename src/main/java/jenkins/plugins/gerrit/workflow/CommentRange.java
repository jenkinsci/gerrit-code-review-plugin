package jenkins.plugins.gerrit.workflow;

import com.google.gerrit.extensions.api.changes.DraftInput;
import com.google.gerrit.extensions.client.Comment;
import hudson.Extension;
import hudson.model.Descriptor;
import javax.annotation.Nonnull;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

public class CommentRange extends CommentLocation {
  private int endCharacter;
  private int endLine;
  private int startCharacter;
  private int startLine;

  @DataBoundConstructor
  public CommentRange(int startLine, int endLine, int startCharacter, int endCharacter) {
    this.startLine = startLine;
    this.endLine = endLine;
    this.startCharacter = startCharacter;
    this.endCharacter = endCharacter;
  }

  @Override
  public String toString() {
    return String.format("Range:%s-%s", startLine, endLine);
  }

  @Override
  public void apply(DraftInput draftInput) {
    draftInput.range = new Comment.Range();
    draftInput.range.startLine = startLine;
    draftInput.range.startCharacter = startCharacter;
    draftInput.range.endLine = endLine;
    draftInput.range.endCharacter = endCharacter;
  }

  @Symbol("range")
  @Extension
  public static class DescriptorImpl extends Descriptor<CommentLocation> {
    @Nonnull
    @Override
    public String getDisplayName() {
      return "Comment Range";
    }
  }
}

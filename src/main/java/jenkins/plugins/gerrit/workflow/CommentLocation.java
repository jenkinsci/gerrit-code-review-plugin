package jenkins.plugins.gerrit.workflow;

import com.google.gerrit.extensions.api.changes.DraftInput;
import hudson.ExtensionPoint;
import hudson.model.AbstractDescribableImpl;

public abstract class CommentLocation extends AbstractDescribableImpl<CommentLocation>
    implements ExtensionPoint {
  public abstract void apply(DraftInput draftInput);
}

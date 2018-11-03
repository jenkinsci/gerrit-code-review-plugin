package jenkins.plugins.gerrit;

import edu.umd.cs.findbugs.annotations.NonNull;
import jenkins.plugins.git.AbstractGitSCMSource;
import jenkins.scm.api.mixin.ChangeRequestSCMRevision;

public class ChangeSCMRevision extends ChangeRequestSCMRevision<ChangeSCMHead> {

  private static final long serialVersionUID = 1L;
  private final @NonNull String patchsetHash;

  ChangeSCMRevision(@NonNull ChangeSCMHead head, @NonNull String patchsetHash) {
    super(head, new AbstractGitSCMSource.SCMRevisionImpl(head.getTarget(), patchsetHash));
    this.patchsetHash = patchsetHash;
  }

  /**
   * The commit hash of the head of the pull request branch.
   *
   * @return The commit hash of the head of the pull request branch
   */
  @NonNull
  public String getPatchsetHash() {
    return patchsetHash;
  }

  @Override
  public boolean equivalent(ChangeRequestSCMRevision<?> o) {
    if (!(o instanceof ChangeSCMRevision)) {
      return false;
    }
    ChangeSCMRevision other = (ChangeSCMRevision) o;
    return getHead().equals(other.getHead()) && patchsetHash.equals(other.patchsetHash);
  }

  @Override
  public int _hashCode() {
    return patchsetHash.hashCode();
  }

  @Override
  public String toString() {
    return patchsetHash;
  }
}

package jenkins.plugins.gerrit;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.Serial;
import jenkins.plugins.git.AbstractGitSCMSource;
import jenkins.scm.api.mixin.ChangeRequestSCMRevision;

public class ChangeSCMRevision extends ChangeRequestSCMRevision<ChangeSCMHead> {

  @Serial private static final long serialVersionUID = 1L;
  private final @NonNull String patchsetHash;
  private final boolean isFilteredByPendingChecks;

  ChangeSCMRevision(@NonNull ChangeSCMHead head, @NonNull String patchsetHash) {
    super(head, new AbstractGitSCMSource.SCMRevisionImpl(head.getTarget(), patchsetHash));
    this.patchsetHash = patchsetHash;
    this.isFilteredByPendingChecks = !head.getPendingCheckerUuids().isEmpty();
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

    // Force a rebuild, if the job building this change already exists, but has a pending checks.
    // Only used, if the FilterChecksTrait is used.
    if (isFilteredByPendingChecks) {
      return false;
    }

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

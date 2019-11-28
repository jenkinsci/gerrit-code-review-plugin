package jenkins.plugins.gerrit;

import edu.umd.cs.findbugs.annotations.NonNull;
import jenkins.plugins.git.GitSCMSourceContext;
import jenkins.plugins.git.GitSCMSourceRequest;
import jenkins.scm.api.SCMHeadObserver;
import jenkins.scm.api.SCMSourceCriteria;

public class GerritSCMSourceContext<
        C extends GitSCMSourceContext<C, R>, R extends GitSCMSourceRequest>
    extends GitSCMSourceContext<C, R> {

  /**
   * Constructor.
   *
   * @param criteria (optional) criteria.
   * @param observer the {@link SCMHeadObserver}.
   */
  public GerritSCMSourceContext(SCMSourceCriteria criteria, @NonNull SCMHeadObserver observer) {
    super(criteria, observer);
  }
}

package jenkins.plugins.gerrit;

import edu.umd.cs.findbugs.annotations.NonNull;
import jenkins.scm.api.SCMNavigator;
import jenkins.scm.api.SCMSourceObserver;
import jenkins.scm.api.trait.SCMNavigatorContext;
import jenkins.scm.api.trait.SCMNavigatorRequest;

public class GerritSCMNavigatorRequest extends SCMNavigatorRequest {
  /**
   * Constructor.
   *
   * @param source the source.
   * @param context the context.
   * @param observer the observer.
   */
  protected GerritSCMNavigatorRequest(
      @NonNull SCMNavigator source,
      @NonNull SCMNavigatorContext<?, ?> context,
      @NonNull SCMSourceObserver observer) {
    super(source, context, observer);
  }
}

package jenkins.plugins.gerrit;

import edu.umd.cs.findbugs.annotations.NonNull;
import jenkins.scm.api.SCMNavigator;
import jenkins.scm.api.SCMSourceObserver;
import jenkins.scm.api.trait.SCMNavigatorContext;

public class GerritSCMNavigatorContext
    extends SCMNavigatorContext<GerritSCMNavigatorContext, GerritSCMNavigatorRequest> {
  @NonNull
  @Override
  public GerritSCMNavigatorRequest newRequest(
      @NonNull SCMNavigator navigator, @NonNull SCMSourceObserver observer) {
    return new GerritSCMNavigatorRequest(navigator, this, observer);
  }
}

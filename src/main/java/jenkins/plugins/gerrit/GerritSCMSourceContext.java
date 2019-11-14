package jenkins.plugins.gerrit;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.TaskListener;
import java.util.ArrayList;
import java.util.List;
import jenkins.plugins.gerrit.traits.ChangeDiscoveryTrait.Strategy;
import jenkins.plugins.git.GitSCMSourceContext;
import jenkins.plugins.git.GitSCMSourceRequest;
import jenkins.scm.api.SCMHeadObserver;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.SCMSourceCriteria;

public class GerritSCMSourceContext
    extends GitSCMSourceContext<GerritSCMSourceContext, GitSCMSourceRequest> {

  @NonNull private Strategy changeDiscoveryStrategy = Strategy.OPEN_CHANGES;
  /** {@code true} if the {@link GerritSCMSourceRequest} will need information about branches. */
  @NonNull private List<String> refSpecs = new ArrayList<>();

  public GerritSCMSourceContext(SCMSourceCriteria criteria, SCMHeadObserver observer) {
    super(criteria, observer);
  }

  /**
   * Defines the strategy to be used to select, which changes to build.
   *
   * @param strategy the strategy.
   * @return {@code this} for method chaining.
   */
  @NonNull
  public GerritSCMSourceContext withChangeDiscoveryStrategy(Strategy strategy) {
    changeDiscoveryStrategy = strategy;
    return this;
  }

  /**
   * Returns the {@link Strategy} to use to discover changes to build.
   *
   * @return the {@link Strategy} to use to discover changes to build.
   */
  @NonNull
  public final Strategy changeDiscoveryStrategy() {
    return changeDiscoveryStrategy;
  }

  @NonNull
  @Override
  public GitSCMSourceRequest newRequest(
      @NonNull SCMSource source, @CheckForNull TaskListener listener) {
    return new GitSCMSourceRequest(source, this, listener);
  }
}

// Copyright (C) 2019 GerritForge Ltd
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package jenkins.plugins.gerrit;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.TaskListener;
import java.util.ArrayList;
import java.util.List;
import jenkins.plugins.gerrit.traits.ChangeDiscoveryTrait.ChangeDiscoveryStrategy;
import jenkins.plugins.git.GitSCMSourceContext;
import jenkins.plugins.git.GitSCMSourceRequest;
import jenkins.scm.api.SCMHeadObserver;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.SCMSourceCriteria;

public class GerritSCMSourceContext
    extends GitSCMSourceContext<GerritSCMSourceContext, GitSCMSourceRequest> {

  @NonNull
  private ChangeDiscoveryStrategy changeDiscoveryStrategy = ChangeDiscoveryStrategy.OPEN_CHANGES;
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
  public GerritSCMSourceContext withChangeDiscoveryStrategy(ChangeDiscoveryStrategy strategy) {
    changeDiscoveryStrategy = strategy;
    return this;
  }

  /**
   * Returns the {@link ChangeDiscoveryStrategy} to use to discover changes to build.
   *
   * @return the {@link ChangeDiscoveryStrategy} to use to discover changes to build.
   */
  @NonNull
  public final ChangeDiscoveryStrategy changeDiscoveryStrategy() {
    return changeDiscoveryStrategy;
  }

  @NonNull
  @Override
  public GitSCMSourceRequest newRequest(
      @NonNull SCMSource source, @CheckForNull TaskListener listener) {
    return new GitSCMSourceRequest(source, this, listener);
  }
}

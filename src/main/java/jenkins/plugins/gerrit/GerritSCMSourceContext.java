// Copyright (C) 2019 SAP SE
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
import jenkins.plugins.gerrit.traits.FilterChecksTrait.ChecksQueryOperator;
import jenkins.plugins.git.GitSCMSourceContext;
import jenkins.scm.api.SCMHeadObserver;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.SCMSourceCriteria;

public class GerritSCMSourceContext
    extends GitSCMSourceContext<GerritSCMSourceContext, GerritSCMSourceRequest> {

  @NonNull private ChecksQueryOperator checksQueryOperator = ChecksQueryOperator.SCHEME;
  @NonNull private boolean filterForPendingChecks = false;
  @NonNull private String checksQueryString = "";
  @NonNull private String changesQueryFilter = "";

  public GerritSCMSourceContext(SCMSourceCriteria criteria, SCMHeadObserver observer) {
    super(criteria, observer);
  }

  /**
   * Defines whether changes should by filtered by pending checks.
   *
   * @param wantFilter whether the filter should be applied.
   * @return {@code this} for method chaining.
   */
  @NonNull
  public GerritSCMSourceContext wantFilterForPendingChecks(boolean wantFilter) {
    filterForPendingChecks = wantFilter;
    return this;
  }

  /**
   * Returns true, if open changes should be filtered for pending checks
   *
   * @return boolean whether open changes should be filtered for pending checks.
   */
  @NonNull
  public final boolean filterForPendingChecks() {
    return filterForPendingChecks;
  }

  /**
   * Defines the query operator to be used to query pending checks.
   *
   * @param queryOperator the query operator to be used.
   * @return {@code this} for method chaining.
   */
  @NonNull
  public GerritSCMSourceContext withChecksQueryOperator(ChecksQueryOperator queryOperator) {
    checksQueryOperator = queryOperator;
    return this;
  }

  /**
   * Returns the {@link ChecksQueryOperator} to use to discover pending checks.
   *
   * @return the {@link ChecksQueryOperator} to use to discover pending checks.
   */
  @NonNull
  public final ChecksQueryOperator checksQueryOperator() {
    return checksQueryOperator;
  }

  /**
   * Defines the query string to be used to query pending checks.
   *
   * @param queryString the query string to be used.
   * @return {@code this} for method chaining.
   */
  @NonNull
  public GerritSCMSourceContext withChecksQueryString(String queryString) {
    checksQueryString = queryString;
    return this;
  }

  /**
   * Returns the ID or scheme to use to discover pending checks.
   *
   * @return the ID or scheme to use to discover pending checks.
   */
  @NonNull
  public final String checksQueryString() {
    return checksQueryString;
  }

  @NonNull
  @Override
  public GerritSCMSourceRequest newRequest(
      @NonNull SCMSource source, @CheckForNull TaskListener listener) {
    return new GerritSCMSourceRequest((GerritSCMSource) source, this, listener);
  }

  public void setChangeFilter(String changesQueryFilter) {
    this.changesQueryFilter = changesQueryFilter;
  }

  public String changesQueryFilter() {
    return changesQueryFilter;
  }
}

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

package jenkins.plugins.gerrit.traits;

import hudson.Extension;
import hudson.util.ListBoxModel;
import java.io.IOException;
import javax.annotation.Nonnull;
import jenkins.plugins.gerrit.ChangeSCMHead;
import jenkins.plugins.gerrit.GerritSCMSource;
import jenkins.plugins.gerrit.GerritSCMSourceContext;
import jenkins.plugins.gerrit.GerritSCMSourceRequest;
import jenkins.plugins.git.GitSCMBuilder;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMHeadCategory;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.trait.SCMBuilder;
import jenkins.scm.api.trait.SCMHeadFilter;
import jenkins.scm.api.trait.SCMSourceContext;
import jenkins.scm.api.trait.SCMSourceRequest;
import jenkins.scm.api.trait.SCMSourceTrait;
import jenkins.scm.api.trait.SCMSourceTraitDescriptor;
import jenkins.scm.impl.trait.Discovery;
import org.kohsuke.stapler.DataBoundConstructor;

public class FilterChecksTrait extends SCMSourceTrait {

  public enum ChecksQueryOperator {
    ID,
    SCHEME
  }

  private final ChecksQueryOperator queryOperator;
  private final String queryString;

  /** Constructor for stapler. */
  @DataBoundConstructor
  public FilterChecksTrait(ChecksQueryOperator queryOperator, String queryString) {
    this.queryOperator = queryOperator;
    this.queryString = queryString;
  }

  /**
   * Returns the query operator.
   *
   * @return the query operator.
   */
  public ChecksQueryOperator getQueryOperator() {
    return queryOperator;
  }

  /**
   * Returns the query string.
   *
   * @return the query string.
   */
  public String getQueryString() {
    return queryString;
  }

  /** {@inheritDoc} */
  @Override
  protected void decorateContext(SCMSourceContext<?, ?> context) {
    GerritSCMSourceContext ctx = (GerritSCMSourceContext) context;
    ctx.wantFilterForPendingChecks(true);
    ctx.withChecksQueryOperator(queryOperator);
    ctx.withChecksQueryString(queryString);
    ctx.withFilter(new PendingChecksFilter());
  }

  /** {@inheritDoc} */
  @Override
  public boolean includeCategory(@Nonnull SCMHeadCategory category) {
    return category.isUncategorized();
  }

  /** Our descriptor. */
  @Extension
  @Discovery
  public static class DescriptorImpl extends SCMSourceTraitDescriptor {

    /** {@inheritDoc} */
    @Override
    public String getDisplayName() {
      return Messages.FilterChecksTrait_displayName();
    }

    public ListBoxModel doFillQueryOperatorItems() {
      ListBoxModel items = new ListBoxModel();

      items.add(Messages.FilterChecksTrait_checkerIdOperator(), ChecksQueryOperator.ID.name());
      items.add(Messages.FilterChecksTrait_schemeOperator(), ChecksQueryOperator.SCHEME.name());

      return items;
    }

    /** {@inheritDoc} */
    @Override
    public Class<? extends SCMBuilder> getBuilderClass() {
      return GitSCMBuilder.class;
    }

    /** {@inheritDoc} */
    @Override
    public Class<? extends SCMSourceContext> getContextClass() {
      return GerritSCMSourceContext.class;
    }

    /** {@inheritDoc} */
    @Override
    public Class<? extends SCMSource> getSourceClass() {
      return GerritSCMSource.class;
    }
  }

  public static class PendingChecksFilter extends SCMHeadFilter {

    @Override
    public boolean isExcluded(SCMSourceRequest request, SCMHead head)
        throws IOException, InterruptedException {
      if (head instanceof ChangeSCMHead) {
        return !((GerritSCMSourceRequest) request)
            .getPatchsetWithPendingChecks()
            .containsKey(
                String.format(
                    "%d/%d",
                    ((ChangeSCMHead) head).getChangeNumber(),
                    ((ChangeSCMHead) head).getPatchSetNumber()));
      }
      return true;
    }
  }
}

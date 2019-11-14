// Copyright (C) 2018 GerritForge Ltd
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
import javax.annotation.Nonnull;
import jenkins.plugins.gerrit.GerritSCMSource;
import jenkins.plugins.gerrit.GerritSCMSourceContext;
import jenkins.plugins.git.GitSCMBuilder;
import jenkins.scm.api.SCMHeadCategory;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.trait.SCMBuilder;
import jenkins.scm.api.trait.SCMSourceContext;
import jenkins.scm.api.trait.SCMSourceTrait;
import jenkins.scm.api.trait.SCMSourceTraitDescriptor;
import jenkins.scm.impl.trait.Discovery;
import org.kohsuke.stapler.DataBoundConstructor;

public class FilterChecksTrait extends SCMSourceTrait {

  public enum ChecksQueryOperator {
    ID,
    SCHEME
  }

  private ChecksQueryOperator queryOperator;
  private String queryString;

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
      return jenkins.plugins.gerrit.traits.Messages.FilterChecksTrait_displayName();
    }

    public ListBoxModel doFillQueryOperatorItems() {
      ListBoxModel items = new ListBoxModel();

      items.add(
          jenkins.plugins.gerrit.traits.Messages.FilterChecksTrait_checkerIdOperator(),
          ChecksQueryOperator.ID.name());
      items.add(
          jenkins.plugins.gerrit.traits.Messages.FilterChecksTrait_schemeOperator(),
          ChecksQueryOperator.SCHEME.name());

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
}

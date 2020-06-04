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
import javax.annotation.Nonnull;
import jenkins.plugins.gerrit.GerritSCMSource;
import jenkins.plugins.gerrit.GerritSCMSourceContext;
import jenkins.plugins.git.GitSCMBuilder;
import jenkins.plugins.git.traits.Messages;
import jenkins.scm.api.*;
import jenkins.scm.api.trait.*;
import jenkins.scm.impl.trait.Discovery;
import org.kohsuke.stapler.DataBoundConstructor;

/** A {@link Discovery} trait that would discover all the Gerrit Changes */
public class ChangeDiscoveryTrait extends SCMSourceTrait {
  private final String queryString;

  /** Constructor for stapler. */
  @DataBoundConstructor
  public ChangeDiscoveryTrait(String queryString) {
    this.queryString = queryString;
  }

  /**
   * Returns the changes query string.
   *
   * @return the changes query string.
   */
  public String getQueryString() {
    return queryString;
  }

  /** {@inheritDoc} */
  @Override
  protected void decorateContext(SCMSourceContext<?, ?> context) {
    GerritSCMSourceContext ctx = (GerritSCMSourceContext) context;
    ctx.wantBranches(true);
    ctx.withAuthority(new BranchSCMHeadAuthority());
    ctx.setChangeFilter(queryString);
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
      return jenkins.plugins.gerrit.traits.Messages.ChangeDiscoveryTrait_displayName();
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

  /** Trusts branches from the repository. */
  public static class BranchSCMHeadAuthority
      extends SCMHeadAuthority<SCMSourceRequest, SCMHead, SCMRevision> {
    /** {@inheritDoc} */
    @Override
    protected boolean checkTrusted(@Nonnull SCMSourceRequest request, @Nonnull SCMHead head) {
      return true;
    }

    /** Out descriptor. */
    @Extension
    public static class DescriptorImpl extends SCMHeadAuthorityDescriptor {
      /** {@inheritDoc} */
      @Override
      public String getDisplayName() {
        return Messages.BranchDiscoveryTrait_authorityDisplayName();
      }

      /** {@inheritDoc} */
      @Override
      public boolean isApplicableToOrigin(@Nonnull Class<? extends SCMHeadOrigin> originClass) {
        return SCMHeadOrigin.Default.class.isAssignableFrom(originClass);
      }
    }
  }
}

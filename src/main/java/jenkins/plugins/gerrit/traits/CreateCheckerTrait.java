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

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import java.io.IOException;
import java.util.EnumSet;
import javax.annotation.Nonnull;
import javax.servlet.ServletException;
import jenkins.plugins.gerrit.GerritSCMSource;
import jenkins.plugins.gerrit.GerritSCMSourceContext;
import jenkins.plugins.gerrit.PendingChecksFilter;
import jenkins.plugins.gerrit.checks.api.BlockingCondition;
import jenkins.plugins.gerrit.checks.api.CheckerInput;
import jenkins.plugins.gerrit.checks.api.CheckerStatus;
import jenkins.plugins.gerrit.traits.FilterChecksTrait.ChecksQueryOperator;
import jenkins.plugins.git.GitSCMBuilder;
import jenkins.scm.api.SCMHeadCategory;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.trait.SCMBuilder;
import jenkins.scm.api.trait.SCMSourceContext;
import jenkins.scm.api.trait.SCMSourceTrait;
import jenkins.scm.api.trait.SCMSourceTraitDescriptor;
import jenkins.scm.impl.trait.Discovery;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

public class CreateCheckerTrait extends SCMSourceTrait {

  @NonNull private String uuid;
  @NonNull private String name;
  private String description;
  // TODO: Can status be set automatically, when a job is disabled?
  private CheckerStatus status;
  private EnumSet<BlockingCondition> blocking;
  private String query;

  /** Constructor for stapler. */
  @DataBoundConstructor
  public CreateCheckerTrait(
      @NonNull String uuid,
      @NonNull String name,
      String description,
      CheckerStatus status,
      EnumSet<BlockingCondition> blocking,
      String query) {
    this.uuid = uuid;
    this.name = name;
    this.description = description;
    this.status = status;
    this.blocking = blocking;
    this.query = query;
  }

  /**
   * Returns the checker's uuid.
   *
   * @return the checker's uuid.
   */
  @NonNull
  public String getUuid() {
    return uuid;
  }

  /**
   * Returns the checker's display name.
   *
   * @return the checker's display name.
   */
  @NonNull
  public String getName() {
    return name;
  }

  /**
   * Returns the checker's description.
   *
   * @return the checker's description.
   */
  public String getDescription() {
    return description;
  }

  /**
   * Returns the checker's status.
   *
   * @return the checker's status.
   */
  public CheckerStatus getStatus() {
    return status;
  }

  /**
   * Returns the checker's blocking condition.
   *
   * @return the checker's blocking condition.
   */
  public EnumSet<BlockingCondition> getBlocking() {
    return blocking;
  }

  /**
   * Returns the checker's query.
   *
   * @return the checker's query.
   */
  public String getQuery() {
    return query;
  }

  /** {@inheritDoc} */
  @Override
  protected void decorateContext(SCMSourceContext<?, ?> context) {
    GerritSCMSourceContext ctx = (GerritSCMSourceContext) context;
    ctx.wantCreateChecker(true);
    ctx.withChecker(createCheckerInput());
    ctx.wantFilterForPendingChecks(true);
    ctx.withChecksQueryOperator(ChecksQueryOperator.ID);
    ctx.withChecksQueryString(uuid);
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
      return jenkins.plugins.gerrit.traits.Messages.CreateCheckerTrait_displayName();
    }

    public ListBoxModel doFillStatusItems() {
      ListBoxModel items = new ListBoxModel();

      for (CheckerStatus status : CheckerStatus.values()) {
        items.add(status.name());
      }

      return items;
    }

    public FormValidation doCheckUuid(@QueryParameter String value)
        throws IOException, ServletException {
      if (value.isEmpty() || value.split(":").length != 2) {
        return FormValidation.error("A valid checker UUID is required.");
      } else {
        return FormValidation.ok();
      }
    }

    public FormValidation doCheckName(@QueryParameter String value)
        throws IOException, ServletException {
      if (value.isEmpty()) {
        return FormValidation.error("A display name for the checker is required.");
      } else {
        return FormValidation.ok();
      }
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

  private CheckerInput createCheckerInput() {
    CheckerInput checker = new CheckerInput();
    checker.uuid = uuid;
    checker.name = name;
    checker.description = description;
    checker.status = status;
    checker.blocking = blocking;
    checker.query = query;
    return checker;
  }
}

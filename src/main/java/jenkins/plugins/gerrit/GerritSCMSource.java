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

package jenkins.plugins.gerrit;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;
import hudson.Extension;
import hudson.Util;
import hudson.model.Descriptor;
import hudson.model.Item;
import hudson.model.ParameterValue;
import hudson.model.Queue;
import hudson.model.queue.Tasks;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.GitStatus;
import hudson.plugins.git.browser.GitRepositoryBrowser;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.plugins.git.extensions.GitSCMExtensionDescriptor;
import hudson.scm.RepositoryBrowser;
import hudson.scm.SCM;
import hudson.security.ACL;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import java.io.PrintWriter;
import java.net.URISyntaxException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import jenkins.model.Jenkins;
import jenkins.plugins.gerrit.traits.ChangeDiscoveryTrait;
import jenkins.plugins.git.AbstractGitSCMSource;
import jenkins.plugins.git.GitSCMBuilder;
import jenkins.plugins.git.traits.*;
import jenkins.scm.api.*;
import jenkins.scm.api.trait.SCMHeadPrefilter;
import jenkins.scm.api.trait.SCMSourceTrait;
import jenkins.scm.api.trait.SCMSourceTraitDescriptor;
import jenkins.scm.api.trait.SCMTrait;
import jenkins.scm.impl.ChangeRequestSCMHeadCategory;
import jenkins.scm.impl.UncategorizedSCMHeadCategory;
import jenkins.scm.impl.form.NamedArrayList;
import jenkins.scm.impl.trait.Discovery;
import jenkins.scm.impl.trait.Selection;
import org.acegisecurity.context.SecurityContext;
import org.acegisecurity.context.SecurityContextHolder;
import org.apache.commons.lang.StringUtils;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.URIish;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.*;

/** A {@link SCMSource} that discovers branches in a git repository. */
public class GerritSCMSource extends AbstractGerritSCMSource {

  private static final String DEFAULT_INCLUDES = "*";

  private static final String DEFAULT_EXCLUDES = "";

  public static final Logger LOGGER = Logger.getLogger(GerritSCMSource.class.getName());

  private final String remote;

  private Boolean insecureHttps;

  @CheckForNull private String credentialsId;

  private List<SCMSourceTrait> traits = new ArrayList<>();

  @DataBoundConstructor
  public GerritSCMSource(String remote) {
    this.remote = remote;
  }

  @DataBoundSetter
  public void setInsecureHttps(Boolean insecureHttps) {
    this.insecureHttps = insecureHttps;
  }

  @DataBoundSetter
  public void setCredentialsId(@CheckForNull String credentialsId) {
    this.credentialsId = credentialsId;
  }

  @DataBoundSetter
  public void setTraits(List<SCMSourceTrait> traits) {
    this.traits = SCMTrait.asSetList(traits);
  }

  private RefSpecsSCMSourceTrait asRefSpecsSCMSourceTrait(String rawRefSpecs, String remoteName) {
    if (rawRefSpecs != null) {
      Set<String> defaults = new HashSet<>();
      defaults.add("+refs/heads/*:refs/remotes/origin/*");
      if (remoteName != null) {
        defaults.add("+refs/heads/*:refs/remotes/" + remoteName + "/*");
      }
      if (!defaults.contains(rawRefSpecs.trim())) {
        List<String> templates = new ArrayList<>();
        for (String rawRefSpec : rawRefSpecs.split(" ")) {
          if (StringUtils.isBlank(rawRefSpec)) {
            continue;
          }
          if (defaults.contains(rawRefSpec)) {
            templates.add(AbstractGitSCMSource.REF_SPEC_DEFAULT);
          } else {
            templates.add(rawRefSpec);
          }
        }
        if (!templates.isEmpty()) {
          return new RefSpecsSCMSourceTrait(templates.toArray(new String[templates.size()]));
        }
      }
    }
    return null;
  }

  @Restricted(DoNotUse.class)
  public boolean isIgnoreOnPushNotifications() {
    return SCMTrait.find(traits, IgnoreOnPushNotificationTrait.class) != null;
  }

  // For Stapler only
  @Restricted(DoNotUse.class)
  @DataBoundSetter
  public void setBrowser(GitRepositoryBrowser browser) {
    List<SCMSourceTrait> traits = new ArrayList<>(this.traits);
    for (Iterator<SCMSourceTrait> iterator = traits.iterator(); iterator.hasNext(); ) {
      if (iterator.next() instanceof GitBrowserSCMSourceTrait) {
        iterator.remove();
      }
    }
    if (browser != null) {
      traits.add(new GitBrowserSCMSourceTrait(browser));
    }
    setTraits(traits);
  }

  // For Stapler only
  @Restricted(DoNotUse.class)
  @DataBoundSetter
  public void setGitTool(String gitTool) {
    List<SCMSourceTrait> traits = new ArrayList<>(this.traits);
    gitTool = Util.fixEmptyAndTrim(gitTool);
    for (Iterator<SCMSourceTrait> iterator = traits.iterator(); iterator.hasNext(); ) {
      if (iterator.next() instanceof GitToolSCMSourceTrait) {
        iterator.remove();
      }
    }
    if (gitTool != null) {
      traits.add(new GitToolSCMSourceTrait(gitTool));
    }
    setTraits(traits);
  }

  // For Stapler only
  @Restricted(DoNotUse.class)
  @DataBoundSetter
  public void setExtensions(@CheckForNull List<GitSCMExtension> extensions) {
    List<SCMSourceTrait> traits = new ArrayList<>(this.traits);
    for (Iterator<SCMSourceTrait> iterator = traits.iterator(); iterator.hasNext(); ) {
      if (iterator.next() instanceof GitSCMExtensionTrait) {
        iterator.remove();
      }
    }
    EXTENSIONS:
    for (GitSCMExtension extension : Util.fixNull(extensions)) {
      for (SCMSourceTraitDescriptor d : SCMSourceTrait.all()) {
        if (d instanceof GitSCMExtensionTraitDescriptor) {
          GitSCMExtensionTraitDescriptor descriptor = (GitSCMExtensionTraitDescriptor) d;
          if (descriptor.getExtensionClass().isInstance(extension)) {
            try {
              SCMSourceTrait trait = descriptor.convertToTrait(extension);
              if (trait != null) {
                traits.add(trait);
                continue EXTENSIONS;
              }
            } catch (UnsupportedOperationException e) {
              LOGGER.log(
                  Level.WARNING,
                  "Could not convert " + extension.getClass().getName() + " to a trait",
                  e);
            }
          }
        }
        LOGGER.log(
            Level.FINE,
            "Could not convert {0} to a trait (likely because this option does not "
                + "make sense for a GitSCMSource)",
            extension.getClass().getName());
      }
    }
    setTraits(traits);
  }

  @Override
  public String getCredentialsId() {
    return credentialsId;
  }

  public String getRemote() {
    return remote;
  }

  @Override
  public Boolean getInsecureHttps() {
    return insecureHttps;
  }

  @Restricted(DoNotUse.class)
  public String getRawRefSpecs() {
    String remoteName = null;
    RefSpecsSCMSourceTrait refSpecs = null;
    for (SCMSourceTrait trait : traits) {
      if (trait instanceof RemoteNameSCMSourceTrait) {
        remoteName = ((RemoteNameSCMSourceTrait) trait).getRemoteName();
        if (refSpecs != null) break;
      }
      if (trait instanceof RefSpecsSCMSourceTrait) {
        refSpecs = (RefSpecsSCMSourceTrait) trait;
        if (remoteName != null) break;
      }
    }
    if (remoteName == null) {
      remoteName = AbstractGitSCMSource.DEFAULT_REMOTE_NAME;
    }
    if (refSpecs == null) {
      return AbstractGitSCMSource.REF_SPEC_DEFAULT.replaceAll(
          AbstractGitSCMSource.REF_SPEC_REMOTE_NAME_PLACEHOLDER, remoteName);
    }
    StringBuilder result = new StringBuilder();
    boolean first = true;
    Pattern placeholder = Pattern.compile(AbstractGitSCMSource.REF_SPEC_REMOTE_NAME_PLACEHOLDER);
    for (String template : refSpecs.asStrings()) {
      if (first) {
        first = false;
      } else {
        result.append(' ');
      }
      result.append(placeholder.matcher(template).replaceAll(remoteName));
    }
    return result.toString();
  }

  @Override
  @Restricted(DoNotUse.class)
  @SuppressWarnings("deprecation")
  protected List<RefSpec> getRefSpecs() {
    return new GerritSCMSourceContext(null, SCMHeadObserver.none()).withTraits(traits).asRefSpecs();
  }

  @Nonnull
  @Override
  public List<SCMSourceTrait> getTraits() {
    return traits;
  }

  @Symbol({"gerrit", "git"})
  @Extension
  public static class DescriptorImpl extends SCMSourceDescriptor {

    @Override
    public String getDisplayName() {
      return jenkins.plugins.gerrit.Messages.GerritSCMSource_DisplayName();
    }

    @SuppressWarnings("deprecation")
    public ListBoxModel doFillCredentialsIdItems(
        @AncestorInPath Item context,
        @QueryParameter String remote,
        @QueryParameter String credentialsId) {
      if (context == null && !Jenkins.getActiveInstance().hasPermission(Jenkins.ADMINISTER)
          || context != null && !context.hasPermission(Item.EXTENDED_READ)) {
        return new StandardListBoxModel().includeCurrentValue(credentialsId);
      }
      return new StandardListBoxModel()
          .includeEmptyValue()
          .includeMatchingAs(
              context instanceof Queue.Task
                  ? Tasks.getAuthenticationOf((Queue.Task) context)
                  : ACL.SYSTEM,
              context,
              StandardUsernameCredentials.class,
              URIRequirementBuilder.fromUri(remote).build(),
              GitClient.CREDENTIALS_MATCHER)
          .includeCurrentValue(credentialsId);
    }

    @SuppressWarnings("deprecation")
    public FormValidation doCheckCredentialsId(
        @AncestorInPath Item context, @QueryParameter String remote, @QueryParameter String value) {
      if (context == null && !Jenkins.getActiveInstance().hasPermission(Jenkins.ADMINISTER)
          || context != null && !context.hasPermission(Item.EXTENDED_READ)) {
        return FormValidation.ok();
      }

      value = Util.fixEmptyAndTrim(value);
      if (value == null) {
        return FormValidation.ok();
      }

      remote = Util.fixEmptyAndTrim(remote);
      if (remote == null)
      // not set, can't check
      {
        return FormValidation.ok();
      }

      for (ListBoxModel.Option o :
          CredentialsProvider.listCredentials(
              StandardUsernameCredentials.class,
              context,
              context instanceof Queue.Task
                  ? Tasks.getAuthenticationOf((Queue.Task) context)
                  : ACL.SYSTEM,
              URIRequirementBuilder.fromUri(remote).build(),
              GitClient.CREDENTIALS_MATCHER)) {
        if (StringUtils.equals(value, o.value)) {
          // TODO check if this type of credential is acceptable to the Git client or does it merit
          // warning
          // NOTE: we would need to actually lookup the credential to do the check, which may
          // require
          // fetching the actual credential instance from a remote credentials store. Perhaps this
          // is
          // not required
          return FormValidation.ok();
        }
      }
      // no credentials available, can't check
      return FormValidation.warning("Cannot find any credentials with id " + value);
    }

    @SuppressWarnings("deprecation")
    @Restricted(NoExternalUse.class)
    public GitSCM.DescriptorImpl getSCMDescriptor() {
      return (GitSCM.DescriptorImpl) Jenkins.getActiveInstance().getDescriptor(GitSCM.class);
    }

    @Restricted(DoNotUse.class)
    public List<GitSCMExtensionDescriptor> getExtensionDescriptors() {
      return getSCMDescriptor().getExtensionDescriptors();
    }

    @Restricted(DoNotUse.class)
    public List<Descriptor<RepositoryBrowser<?>>> getBrowserDescriptors() {
      return getSCMDescriptor().getBrowserDescriptors();
    }

    @Restricted(DoNotUse.class)
    public boolean showGitToolOptions() {
      return getSCMDescriptor().showGitToolOptions();
    }

    @Restricted(DoNotUse.class)
    public ListBoxModel doFillGitToolItems() {
      return getSCMDescriptor().doFillGitToolItems();
    }

    @SuppressWarnings("unchecked")
    public List<NamedArrayList<? extends SCMSourceTraitDescriptor>> getTraitsDescriptorLists() {
      List<NamedArrayList<? extends SCMSourceTraitDescriptor>> result = new ArrayList<>();
      List<SCMSourceTraitDescriptor> descriptors =
          SCMSourceTrait._for(this, GerritSCMSourceContext.class, GitSCMBuilder.class);
      NamedArrayList.select(
          descriptors,
          "Within Repository",
          NamedArrayList.anyOf(
              NamedArrayList.withAnnotation(Selection.class),
              NamedArrayList.withAnnotation(Discovery.class)),
          true,
          result);
      NamedArrayList.select(descriptors, "Additional", null, true, result);
      return result;
    }

    public List<SCMSourceTrait> getTraitsDefaults() {
      return Arrays.<SCMSourceTrait>asList(
          new ChangeDiscoveryTrait(ChangeDiscoveryTrait.ChangeDiscoveryStrategy.OPEN_CHANGES),
          new RefSpecsSCMSourceTrait(REF_SPEC_DEFAULT, REF_SPEC_CHANGES));
    }

    @Nonnull
    @Override
    protected SCMHeadCategory[] createCategories() {
      return new SCMHeadCategory[] {
        UncategorizedSCMHeadCategory.DEFAULT,
        new ChangeRequestSCMHeadCategory(Messages._GerritSCMSource_ChangeRequestCategory())
      };
    }
  }

  @Extension
  public static class ListenerImpl extends GitStatus.Listener {
    @Override
    @SuppressWarnings("deprecation")
    public List<GitStatus.ResponseContributor> onNotifyCommit(
        String origin,
        URIish uri,
        @Nullable final String sha1,
        List<ParameterValue> buildParameters,
        String... branches) {
      List<GitStatus.ResponseContributor> result = new ArrayList<GitStatus.ResponseContributor>();
      final boolean notified[] = {false};
      // run in high privilege to see all the projects anonymous users don't see.
      // this is safe because when we actually schedule a build, it's a build that can
      // happen at some random time anyway.
      Jenkins jenkins = Jenkins.getInstance();
      SecurityContext old = jenkins.getACL().impersonate(ACL.SYSTEM);
      try {
        if (branches.length > 0) {
          final URIish u = uri;
          for (final String branch : branches) {
            SCMHeadEvent.fireNow(
                new SCMHeadEvent<String>(SCMEvent.Type.UPDATED, branch, origin) {
                  @Override
                  public boolean isMatch(@Nonnull SCMNavigator navigator) {
                    return false;
                  }

                  @Nonnull
                  @Override
                  public String getSourceName() {
                    // we will never be called here as do not match any navigator
                    return u.getHumanishName();
                  }

                  @Override
                  public boolean isMatch(SCMSource source) {
                    if (source instanceof GerritSCMSource) {
                      GerritSCMSource git = (GerritSCMSource) source;
                      GerritSCMSourceContext ctx =
                          new GerritSCMSourceContext(null, SCMHeadObserver.none())
                              .withTraits(git.getTraits());
                      if (ctx.ignoreOnPushNotifications()) {
                        return false;
                      }
                      URIish remote;
                      try {
                        remote = new URIish(git.getRemote());
                      } catch (URISyntaxException e) {
                        // ignore
                        return false;
                      }
                      if (GitStatus.looselyMatches(u, remote)) {
                        notified[0] = true;
                        return true;
                      }
                      return false;
                    }
                    return false;
                  }

                  @Nonnull
                  @Override
                  public Map<SCMHead, SCMRevision> heads(@Nonnull SCMSource source) {
                    if (source instanceof GerritSCMSource) {
                      GerritSCMSource git = (GerritSCMSource) source;
                      GerritSCMSourceContext ctx =
                          new GerritSCMSourceContext(null, SCMHeadObserver.none())
                              .withTraits(git.getTraits());
                      if (ctx.ignoreOnPushNotifications()) {
                        return Collections.emptyMap();
                      }
                      URIish remote;
                      try {
                        remote = new URIish(git.getRemote());
                      } catch (URISyntaxException e) {
                        // ignore
                        return Collections.emptyMap();
                      }
                      if (GitStatus.looselyMatches(u, remote)) {
                        SCMHead head = new SCMHead(branch);
                        for (SCMHeadPrefilter filter : ctx.prefilters()) {
                          if (filter.isExcluded(git, head)) {
                            return Collections.emptyMap();
                          }
                        }
                        return Collections.<SCMHead, SCMRevision>singletonMap(
                            head, sha1 != null ? new SCMRevisionImpl(head, sha1) : null);
                      }
                    }
                    return Collections.emptyMap();
                  }

                  @Override
                  public boolean isMatch(@Nonnull SCM scm) {
                    return false; // TODO rewrite the legacy event system to fire through SCM API
                  }
                });
          }
        } else {
          for (final SCMSourceOwner owner : SCMSourceOwners.all()) {
            for (SCMSource source : owner.getSCMSources()) {
              if (source instanceof GerritSCMSource) {
                GerritSCMSource git = (GerritSCMSource) source;
                GerritSCMSourceContext ctx =
                    new GerritSCMSourceContext(null, SCMHeadObserver.none())
                        .withTraits(git.getTraits());
                if (ctx.ignoreOnPushNotifications()) {
                  continue;
                }
                URIish remote;
                try {
                  remote = new URIish(git.getRemote());
                } catch (URISyntaxException e) {
                  // ignore
                  continue;
                }
                if (GitStatus.looselyMatches(uri, remote)) {
                  LOGGER.info(
                      "Triggering the indexing of "
                          + owner.getFullDisplayName()
                          + " as a result of event from "
                          + origin);
                  owner.onSCMSourceUpdated(source);
                  result.add(
                      new GitStatus.ResponseContributor() {
                        @Override
                        public void addHeaders(StaplerRequest req, StaplerResponse rsp) {
                          rsp.addHeader("Triggered", owner.getAbsoluteUrl());
                        }

                        @Override
                        public void writeBody(PrintWriter w) {
                          w.println("Scheduled indexing of " + owner.getFullDisplayName());
                        }
                      });
                  notified[0] = true;
                }
              }
            }
          }
        }
      } finally {
        SecurityContextHolder.setContext(old);
      }
      if (!notified[0]) {
        result.add(
            new GitStatus.MessageResponseContributor(
                "No Git consumers using SCM API plugin for: " + uri.toString()));
      }
      return result;
    }
  }
}

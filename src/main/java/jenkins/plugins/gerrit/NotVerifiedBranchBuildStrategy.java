package jenkins.plugins.gerrit;

import javax.annotation.Nonnull;

import org.kohsuke.stapler.DataBoundConstructor;

import hudson.Extension;
import hudson.model.TaskListener;
import jenkins.branch.BranchBuildStrategy;
import jenkins.branch.BranchBuildStrategyDescriptor;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.SCMSourceDescriptor;
import jenkins.scm.api.mixin.TagSCMHead;

public class NotVerifiedBranchBuildStrategy extends BranchBuildStrategy {

    @DataBoundConstructor
    public NotVerifiedBranchBuildStrategy() {}

    @Override
    public boolean isAutomaticBuild(SCMSource source, SCMHead head, SCMRevision currRevision,
            SCMRevision lastBuiltRevision, SCMRevision lastSeeRevision,
            TaskListener listener) {
                if (!(head instanceof ChangeSCMHead)) {
                    // fall back to default behaviour
                    // build everything except tags
                    return !(head instanceof TagSCMHead);
                }

                // only build a gerrit change if it doesn't have a verify vote
                ChangeSCMHead changeHead = (ChangeSCMHead) head;
                return !changeHead.getHasVerifiedVote();
    }

    @Extension
    public static class DescriptorImpl extends BranchBuildStrategyDescriptor {
        @Nonnull
        @Override
        public String getDisplayName() {
            return Messages.NotVerifiedBranchBuildStrategy_DisplayName();
        }

        @Override
        public boolean isApplicable(SCMSourceDescriptor sourceDescriptor) {
            return (sourceDescriptor instanceof GerritSCMSource.DescriptorImpl);
        }
    }

}
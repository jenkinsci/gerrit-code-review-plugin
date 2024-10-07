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

public class NotVerifiedBranchBuildStrategy extends BranchBuildStrategy {

    @DataBoundConstructor
    public NotVerifiedBranchBuildStrategy() {}

    @Override
    public boolean isAutomaticBuild(SCMSource source, SCMHead head, SCMRevision currRevision,
            SCMRevision lastBuiltRevision, SCMRevision lastSeeRevision,
            TaskListener listener) {
                if (!(head instanceof ChangeSCMHead)) {
                    return true;
                }

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
    }

}
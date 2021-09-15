package jenkins.plugins.gerrit.traits;

import hudson.Extension;
import jenkins.scm.api.trait.SCMSourceTrait;
import jenkins.scm.api.trait.SCMSourceTraitDescriptor;
import jenkins.scm.impl.trait.Discovery;
import org.kohsuke.stapler.DataBoundConstructor;

public class AWSKinesisTriggerTrait extends SCMSourceTrait {
    private final String streamName;

    @DataBoundConstructor
    public AWSKinesisTriggerTrait(String streamName) {
        this.streamName = streamName;
    }

    public String getStreamName() { return streamName; }


    @Extension
    @Discovery
    public static class DescriptorImpl extends SCMSourceTraitDescriptor {

        /** {@inheritDoc} */
        @Override
        public String getDisplayName() {
            return Messages.AWSKinesisTriggerTrait_displayName();
        }
    }

}

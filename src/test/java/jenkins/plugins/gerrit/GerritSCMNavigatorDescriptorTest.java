package jenkins.plugins.gerrit;

import java.util.Collections;
import jenkins.branch.OrganizationFolder;
import jenkins.plugins.git.AbstractGitSCMSource;
import jenkins.plugins.git.traits.RefSpecsSCMSourceTrait;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

/** @author Réda Housni Alaoui */
public class GerritSCMNavigatorDescriptorTest {

  @Rule public JenkinsRule j = new JenkinsRule();

  @Test
  public void testConfigRoundtrip() throws Exception {
    OrganizationFolder organizationFolder = j.createProject(OrganizationFolder.class);
    organizationFolder.getSCMNavigators().add(new GerritSCMNavigator());
    organizationFolder = j.configRoundtrip(organizationFolder);

    j.assertEqualDataBoundBeans(
        new GerritSCMNavigator(), organizationFolder.getSCMNavigators().get(0));
  }

  @Test
  public void testConfigRoundTripWithCustomValues() throws Exception {
    OrganizationFolder organizationFolder = j.createProject(OrganizationFolder.class);

    GerritSCMNavigator genuineNavigator =
        new GerritSCMNavigator(
            "https://gerrit.example.org",
            true,
            "my-credentials-id",
            Collections.singletonList(
                new RefSpecsSCMSourceTrait(AbstractGitSCMSource.REF_SPEC_DEFAULT)));

    organizationFolder.getSCMNavigators().add(genuineNavigator);
    organizationFolder = j.configRoundtrip(organizationFolder);

    j.assertEqualDataBoundBeans(genuineNavigator, organizationFolder.getSCMNavigators().get(0));
  }
}

package jenkins.plugins.gerrit;

import java.io.IOException;
import jenkins.branch.OrganizationFolder;
import org.junit.Before;
import org.junit.Rule;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockserver.junit.MockServerRule;

/** @author Réda Housni Alaoui */
public class GerritSCMNavigatorTest {

  @Rule public MockServerRule g = new MockServerRule(this);
  @Rule public JenkinsRule j = new JenkinsRule();

  @Before
  public void beforeEach() throws IOException {
    OrganizationFolder project = j.createProject(OrganizationFolder.class);
  }
}

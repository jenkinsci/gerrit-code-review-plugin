package jenkins.plugins.gerrit;

import com.gargoylesoftware.htmlunit.html.HtmlForm;
import java.io.IOException;
import jenkins.branch.OrganizationFolder;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.xml.sax.SAXException;

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
  public void testDefaultFormValues() throws IOException, SAXException {
    OrganizationFolder organizationFolder = j.createProject(OrganizationFolder.class);
    HtmlForm form =
        j.createWebClient().getPage(organizationFolder, "configure").getFormByName("config");

  }
}

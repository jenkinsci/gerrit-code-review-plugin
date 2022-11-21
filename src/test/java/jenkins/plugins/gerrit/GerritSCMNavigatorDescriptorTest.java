// Copyright (C) 2022 Réda Housni Alaoui <reda-alaoui@hey.com>
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

import java.util.Collections;
import jenkins.branch.OrganizationFolder;
import jenkins.plugins.git.AbstractGitSCMSource;
import jenkins.plugins.git.traits.RefSpecsSCMSourceTrait;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

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

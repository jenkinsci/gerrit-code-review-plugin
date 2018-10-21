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

package jenkins.plugins.gerrit.workflow;

import static org.junit.Assert.assertTrue;

import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

public class GerritCommentStepTest {

  @Rule public JenkinsRule j = new JenkinsRule();

  @Test
  public void gerritCommentStepInvokeTest() throws Exception {
    WorkflowJob p = j.jenkins.createProject(WorkflowJob.class, "p");
    p.setDefinition(
        new CpsFlowDefinition(
            "node {\n"
                + "  withEnv(['BRANCH_NAME=21/5621/1']) {\n"
                + "    gerritComment path: '/path/to/file', line: 1, message: 'Invalid spacing'\n"
                + "  }\n"
                + "}",
            true));
    WorkflowRun run = j.assertBuildStatusSuccess(p.scheduleBuild2(0));
    String log = JenkinsRule.getLog(run);
    System.out.println(log);
    assertTrue(log.contains("gerritComment"));
    assertTrue(log.contains("/path/to/file"));
    assertTrue(log.contains("1"));
    assertTrue(log.contains("Invalid spacing"));
    assertTrue(log.contains("5621"));
  }
}

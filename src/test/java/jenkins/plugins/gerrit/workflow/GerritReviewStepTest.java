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

public class GerritReviewStepTest {

  @Rule public JenkinsRule j = new JenkinsRule();

  @Test
  public void gerritReviewStepInvokeNoEnvTest() throws Exception {
    WorkflowJob p = j.jenkins.createProject(WorkflowJob.class, "p");
    p.setDefinition(
        new CpsFlowDefinition(
            "node {\n"
                + "  gerritReview label: 'Verified', score: -1, message: 'Does not work'\n"
                + "}",
            true));
    WorkflowRun run = j.assertBuildStatusSuccess(p.scheduleBuild2(0));
    String log = JenkinsRule.getLog(run);
    System.out.println(log);
    assertTrue(log.contains("GERRIT_API_URL is not available"));
  }

  @Test
  public void gerritReviewStepInvokeTest() throws Exception {
    WorkflowJob p = j.jenkins.createProject(WorkflowJob.class, "p");
    p.setDefinition(
        new CpsFlowDefinition(
            "node {\n"
                + "  withEnv(['GERRIT_API_URL=https://host/a/project', 'BRANCH_NAME=21/4321/1']) {\n"
                + "    gerritReview label: 'Verified', score: -1, message: 'Does not work'\n"
                + "  }\n"
                + "}",
            true));
    WorkflowRun run = j.assertBuildStatusSuccess(p.scheduleBuild2(0));
    String log = JenkinsRule.getLog(run);
    System.out.println(log);
    assertTrue(log.contains("gerritReview"));
    assertTrue(log.contains("Verified"));
    assertTrue(log.contains("-1"));
    assertTrue(log.contains("Does not work"));
    assertTrue(log.contains("4321"));
  }
}

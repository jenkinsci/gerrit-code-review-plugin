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

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import com.google.gerrit.extensions.api.changes.DraftInput;
import java.io.IOException;
import java.util.Collections;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockserver.junit.MockServerRule;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.JsonBody;
import org.mockserver.verify.VerificationTimes;

public class GerritCommentStepTest {

  @Rule public MockServerRule g = new MockServerRule(this);
  @Rule public JenkinsRule j = new JenkinsRule();

  String projectName = "test-project";
  int changeNumber = 4321;
  int revision = 1;
  String path = "/path/to/file";
  int line = 1;
  String message = "Invalid spacing";
  String branch = String.format("%02d/%d/%d", changeNumber % 100, changeNumber, revision);
  DraftInput draftInput;

  @Before
  public void setup() {
    draftInput = new DraftInput();
    draftInput.path = path;
    draftInput.line = line;
    draftInput.message = message;
  }

  @Test
  public void gerritCommentStepInvokeTestForGerrit2_14() throws Exception {

    WorkflowJob p = createWorkflowJob(path, line, message, branch);

    setupServerVersion("2.14");

    setupDrafts(String.valueOf(changeNumber));

    WorkflowRun run = j.assertBuildStatusSuccess(p.scheduleBuild2(0));
    String log = JenkinsRule.getLog(run);

    verifyDrafts(String.valueOf(changeNumber));
  }

  @Test
  public void gerritCommentStepInvokeTestForGerrit2_16() throws Exception {
    WorkflowJob p = createWorkflowJob(path, line, message, branch);
    setupServerVersion("2.16");

    String changeId = String.format("%s~%s", projectName, changeNumber);
    setupDrafts(changeId);

    WorkflowRun run = j.assertBuildStatusSuccess(p.scheduleBuild2(0));
    String log = JenkinsRule.getLog(run);

    verifyDrafts(changeId);
  }

  private void verifyDrafts(String changeId) {
    g.getClient()
        .verify(
            HttpRequest.request(
                String.format("/a/changes/%s/revisions/%s/drafts", changeId, revision)),
            VerificationTimes.once());
  }

  private void setupServerVersion(String version) {
    g.getClient()
        .when(HttpRequest.request("/a/config/server/version").withMethod("GET"))
        .respond(HttpResponse.response().withStatusCode(200).withBody(")]}'\n\"" + version + "\""));
  }

  private void setupDrafts(String changeId) {
    g.getClient()
        .when(
            HttpRequest.request(
                    String.format("/a/changes/%s/revisions/%s/drafts", changeId, revision))
                .withMethod("PUT")
                .withBody(JsonBody.json(draftInput)))
        .respond(
            HttpResponse.response()
                .withStatusCode(200)
                .withBody(JsonBody.json(Collections.emptyMap())));
  }

  private WorkflowJob createWorkflowJob(String path, int line, String message, String branch)
      throws IOException {
    UsernamePasswordCredentialsImpl c =
        new UsernamePasswordCredentialsImpl(
            CredentialsScope.GLOBAL, "cid", "cid", "USERNAME", "PASSWORD");
    CredentialsProvider.lookupStores(j.jenkins)
        .iterator()
        .next()
        .addCredentials(Domain.global(), c);
    WorkflowJob p = j.jenkins.createProject(WorkflowJob.class, "p");
    p.setDefinition(
        new CpsFlowDefinition(
            String.format(
                ""
                    + "node {\n"
                    + "  withEnv([\n"
                    + "    'GERRIT_API_URL=https://%s:%s/',\n"
                    + "    'GERRIT_PROJECT=%s',\n"
                    + "    'GERRIT_API_INSECURE_HTTPS=true',\n"
                    + "    'GERRIT_CREDENTIALS_ID=cid',\n"
                    + "    'BRANCH_NAME=%s',\n"
                    + "  ]) {\n"
                    + "    gerritComment path: '%s', line: %s, message: '%s'\n"
                    + "  }\n"
                    + "}",
                g.getClient().remoteAddress().getHostString(),
                g.getClient().remoteAddress().getPort(),
                projectName,
                branch,
                path,
                line,
                message),
            true));
    return p;
  }
}

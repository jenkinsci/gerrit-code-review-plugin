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
import java.util.Collections;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
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

  @Test
  public void gerritCommentWithLine() throws Exception {
    String path = "/path/to/file";
    int line = 1;
    String message = "Invalid spacing";

    String gerritCommentScript =
        String.format("gerritComment path: '%s', line: %s, message: '%s'", path, line, message);
    DraftInput draftInput = new DraftInput();
    draftInput.path = path;
    draftInput.line = line;
    draftInput.message = message;
    gerritCommentTest(gerritCommentScript, JsonBody.json(draftInput));
  }

  @Test
  public void gerritCommentWithLocationLine() throws Exception {
    String path = "/path/to/file";
    int line = 1;
    String message = "Invalid spacing";

    String gerritCommentScript =
        String.format(
            "gerritComment path: '%s', location: [$class: 'CommentLine', line: %s], message: '%s'",
            path, line, message);
    DraftInput draftInput = new DraftInput();
    draftInput.path = path;
    draftInput.line = line;
    draftInput.message = message;
    gerritCommentTest(gerritCommentScript, JsonBody.json(draftInput));
  }

  @Test
  public void gerritCommentWithLocationFile() throws Exception {
    String path = "/path/to/file";
    int line = 0;
    String message = "Invalid spacing";

    String gerritCommentScript =
        String.format(
            "gerritComment path: '%s', location: [$class: 'CommentFile'], message: '%s'",
            path, message);
    DraftInput draftInput = new DraftInput();
    draftInput.path = path;
    draftInput.line = line;
    draftInput.message = message;
    gerritCommentTest(gerritCommentScript, JsonBody.json(draftInput));
  }

  @Test
  public void gerritCommentWithLocationRange() throws Exception {
    String path = "/path/to/file";
    String message = "Invalid spacing";

    String gerritCommentScript =
        String.format(
            "gerritComment path: '%s', message: '%s', location: [$class: 'CommentRange', startLine: 1,"
                + " endLine: 2, startCharacter: 3, endCharacter: 4]",
            path, message);
    // We cannot use DraftInput, as Range would get serialized to JSON with an extra property
    // 'isValid'
    gerritCommentTest(
        gerritCommentScript,
        JsonBody.json(
            "{\"path\":\"/path/to/file\",\"range\":{"
                + "\"start_line\":1,\"start_character\":3,\"end_line\":2,\"end_character\":4},"
                + "\"message\":\"Invalid spacing\"}"));
  }

  private void gerritCommentTest(String gerritCommentScript, JsonBody commentBody)
      throws Exception {
    int changeId = 4321;
    int revision = 1;
    String branch = String.format("%02d/%d/%d", changeId % 100, changeId, revision);

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
                    + "    'GERRIT_API_URL=https://%s:%s/a/project',\n"
                    + "    'GERRIT_API_INSECURE_HTTPS=true',\n"
                    + "    'GERRIT_CREDENTIALS_ID=cid',\n"
                    + "    'BRANCH_NAME=%s',\n"
                    + "  ]) {\n"
                    + "    %s\n"
                    + "  }\n"
                    + "}",
                g.getClient().remoteAddress().getHostString(),
                g.getClient().remoteAddress().getPort(),
                branch,
                gerritCommentScript),
            true));

    g.getClient()
        .when(
            HttpRequest.request(
                    String.format(
                        "/a/project/a/changes/%s/revisions/%s/drafts", changeId, revision))
                .withMethod("PUT")
                .withBody(commentBody))
        .respond(
            HttpResponse.response()
                .withStatusCode(200)
                .withBody(JsonBody.json(Collections.emptyMap())));

    WorkflowRun run = j.assertBuildStatusSuccess(p.scheduleBuild2(0));
    String log = JenkinsRule.getLog(run);

    g.getClient()
        .verify(
            HttpRequest.request(
                String.format("/a/project/a/changes/%s/revisions/%s/drafts", changeId, revision)),
            VerificationTimes.once());
  }
}

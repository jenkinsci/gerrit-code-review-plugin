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
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import hudson.model.Result;
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

public class GerritReviewStepTest {

  @Rule public MockServerRule g = new MockServerRule(this);
  @Rule public JenkinsRule j = new JenkinsRule();

  @Test
  public void gerritCommentStepInvokeNoAPITest() throws Exception {
    WorkflowJob p = j.jenkins.createProject(WorkflowJob.class, "p");
    p.setDefinition(
        new CpsFlowDefinition(
            "node {\n"
                + "  withEnv([\n"
                + "  ]) {\n"
                + "    gerritReview label: 'Verified', score: -1, message: 'Does not work'\n"
                + "  }\n"
                + "}",
            true));
    WorkflowRun run = j.assertBuildStatusSuccess(p.scheduleBuild2(0));
    String log = JenkinsRule.getLog(run);

    j.assertLogContains("Gerrit Review is disabled no API URL", run);
  }

  @Test
  public void gerritCommentStepInvokeNoCredTest() throws Exception {
    WorkflowJob p = j.jenkins.createProject(WorkflowJob.class, "p");
    p.setDefinition(
        new CpsFlowDefinition(
            "node {\n"
                + "  withEnv([\n"
                + "    'GERRIT_API_URL=http://host/a/project',\n"
                + "  ]) {\n"
                + "    gerritReview label: 'Verified', score: -1, message: 'Does not work'\n"
                + "  }\n"
                + "}",
            true));
    WorkflowRun run = j.assertBuildStatusSuccess(p.scheduleBuild2(0));
    String log = JenkinsRule.getLog(run);

    j.assertLogContains("Gerrit Review is disabled no credentials", run);
  }

  @Test
  public void gerritCommentStepInvokeMissingCredTest() throws Exception {
    WorkflowJob p = j.jenkins.createProject(WorkflowJob.class, "p");
    p.setDefinition(
        new CpsFlowDefinition(
            "node {\n"
                + "  withEnv([\n"
                + "    'GERRIT_API_URL=http://host/a/project',\n"
                + "    'GERRIT_CREDENTIALS_ID=cid',\n"
                + "  ]) {\n"
                + "    gerritReview label: 'Verified', score: -1, message: 'Does not work'\n"
                + "  }\n"
                + "}",
            true));
    WorkflowRun run = j.assertBuildStatusSuccess(p.scheduleBuild2(0));
    String log = JenkinsRule.getLog(run);

    j.assertLogContains("Gerrit Review is disabled no credentials", run);
  }

  @Test
  public void gerritReviewStepInvokeFailSSLValidationTest() throws Exception {
    int changeId = 4321;
    int revision = 1;
    String label = "Verfied";
    int score = -1;
    String message = "Does not work";
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
                    + "    'GERRIT_CREDENTIALS_ID=cid',\n"
                    + "    'BRANCH_NAME=%s',\n"
                    + "  ]) {\n"
                    + "    gerritReview label: '%s', score: %s, message: '%s'\n"
                    + "  }\n"
                    + "}",
                g.getClient().remoteAddress().getHostString(),
                g.getClient().remoteAddress().getPort(),
                branch,
                label,
                score,
                message),
            true));

    WorkflowRun run = j.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0));
    String log = JenkinsRule.getLog(run);

    j.assertLogContains("javax.net.ssl.SSLHandshakeException", run);
  }

  @Test
  public void gerritReviewStepInvokeTest() throws Exception {
    int changeId = 4321;
    int revision = 1;
    String label = "Verfied";
    int score = -1;
    String message = "Does not work";
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
                    + "    gerritReview labels: ['%s': %s], message: '%s'\n"
                    + "  }\n"
                    + "}",
                g.getClient().remoteAddress().getHostString(),
                g.getClient().remoteAddress().getPort(),
                branch,
                label,
                score,
                message),
            true));

    g.getClient()
        .when(HttpRequest.request("/a/project/login/").withMethod("POST"))
        .respond(HttpResponse.response().withStatusCode(200));
    ReviewInput reviewInput = new ReviewInputForObjectMapper().label(label, score).message(message);
    reviewInput.drafts = ReviewInput.DraftHandling.PUBLISH;
    reviewInput.tag = "autogenerated:jenkins";
    reviewInput.notify = NotifyHandling.OWNER;
    g.getClient()
        .when(
            HttpRequest.request(
                    String.format(
                        "/a/project/a/changes/%s/revisions/%s/review", changeId, revision))
                .withMethod("POST")
                .withBody(JsonBody.json(reviewInput)))
        .respond(
            HttpResponse.response()
                .withStatusCode(200)
                .withBody(JsonBody.json(Collections.emptyMap())));

    WorkflowRun run = j.assertBuildStatusSuccess(p.scheduleBuild2(0));
    String log = JenkinsRule.getLog(run);

    g.getClient()
        .verify(
            HttpRequest.request("/a/project/login/").withMethod("POST"), VerificationTimes.once());
    g.getClient()
        .verify(
            HttpRequest.request(
                String.format("/a/project/a/changes/%s/revisions/%s/review", changeId, revision)),
            VerificationTimes.once());
  }

  @Test
  public void gerritReviewStepInvokeLabelsTest() throws Exception {
    int changeId = 4321;
    int revision = 1;
    String label1 = "Verfied";
    int score1 = -1;
    String label2 = "CI-Review";
    int score2 = -1;
    String message = "Does not work";
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
                    + "    gerritReview labels: ['%s': %s, '%s': %s], message: '%s'\n"
                    + "  }\n"
                    + "}",
                g.getClient().remoteAddress().getHostString(),
                g.getClient().remoteAddress().getPort(),
                branch,
                label1,
                score1,
                label2,
                score2,
                message),
            true));

    g.getClient()
        .when(HttpRequest.request("/a/project/login/").withMethod("POST"))
        .respond(HttpResponse.response().withStatusCode(200));
    ReviewInput reviewInput =
        new ReviewInputForObjectMapper()
            .label(label1, score1)
            .label(label2, score2)
            .message(message);
    reviewInput.drafts = ReviewInput.DraftHandling.PUBLISH;
    reviewInput.tag = "autogenerated:jenkins";
    reviewInput.notify = NotifyHandling.OWNER;
    g.getClient()
        .when(
            HttpRequest.request(
                    String.format(
                        "/a/project/a/changes/%s/revisions/%s/review", changeId, revision))
                .withMethod("POST")
                .withBody(JsonBody.json(reviewInput)))
        .respond(
            HttpResponse.response()
                .withStatusCode(200)
                .withBody(JsonBody.json(Collections.emptyMap())));

    WorkflowRun run = j.assertBuildStatusSuccess(p.scheduleBuild2(0));
    String log = JenkinsRule.getLog(run);

    g.getClient()
        .verify(
            HttpRequest.request("/a/project/login/").withMethod("POST"), VerificationTimes.once());
    g.getClient()
        .verify(
            HttpRequest.request(
                String.format("/a/project/a/changes/%s/revisions/%s/review", changeId, revision)),
            VerificationTimes.once());
  }

  @JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
  private static class ReviewInputForObjectMapper extends ReviewInput {}
}

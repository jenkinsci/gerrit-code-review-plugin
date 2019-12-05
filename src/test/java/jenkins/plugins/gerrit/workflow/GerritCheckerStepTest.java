// Copyright (C) 2019 SAP SE
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
import hudson.model.Result;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import jenkins.plugins.gerrit.checks.api.BlockingCondition;
import jenkins.plugins.gerrit.checks.api.CheckerInput;
import jenkins.plugins.gerrit.checks.api.CheckerStatus;
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

public class GerritCheckerStepTest {

  @Rule public MockServerRule g = new MockServerRule(this);
  @Rule public JenkinsRule j = new JenkinsRule();

  private final String checkerUuid = "test:checker";
  private final String checkerName = "checker";
  private final String description = "description";
  private final String project = "project";
  private final String status = "ENABLED";
  private final String blocking = "STATE_NOT_PASSING";
  private final String query = "(status:open)";

  private UsernamePasswordCredentialsImpl c;
  private WorkflowJob p;

  @Before
  public void setup() throws IOException {
    c =
        new UsernamePasswordCredentialsImpl(
            CredentialsScope.GLOBAL, "cid", "cid", "USERNAME", "PASSWORD");
    CredentialsProvider.lookupStores(j.jenkins)
        .iterator()
        .next()
        .addCredentials(Domain.global(), c);
    p = j.jenkins.createProject(WorkflowJob.class, "p");
  }

  @Test
  public void gerritCheckStepInvokeFailSSLValidationTest() throws Exception {
    p.setDefinition(
        new CpsFlowDefinition(
            String.format(
                ""
                    + "node {\n"
                    + "  withEnv([\n"
                    + "    'GERRIT_API_URL=https://%s:%s/a/project',\n"
                    + "    'GERRIT_CREDENTIALS_ID=cid',\n"
                    + "    'GERRIT_PROJECT=%s',\n"
                    + "  ]) {\n"
                    + "    gerritChecker uuid: '%s', name: '%s'\n"
                    + "  }\n"
                    + "}",
                g.getClient().remoteAddress().getHostString(),
                g.getClient().remoteAddress().getPort(),
                project,
                checkerUuid,
                checkerName),
            true));

    WorkflowRun run = j.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0));
    String log = JenkinsRule.getLog(run);

    j.assertLogContains("javax.net.ssl.SSLHandshakeException", run);
  }

  @Test
  public void gerritCheckStepInvokeTestCreatesChecker() throws Exception {
    p.setDefinition(
        new CpsFlowDefinition(
            String.format(
                ""
                    + "node {\n"
                    + "  withEnv([\n"
                    + "    'GERRIT_API_URL=https://%s:%s/a/project',\n"
                    + "    'GERRIT_CREDENTIALS_ID=cid',\n"
                    + "    'GERRIT_API_INSECURE_HTTPS=true',\n"
                    + "    'GERRIT_PROJECT=%s',\n"
                    + "  ]) {\n"
                    + "    gerritChecker uuid: '%s', name: '%s', description: '%s', status: '%s', blocking: ['%s'], query: '%s'\n"
                    + "  }\n"
                    + "}",
                g.getClient().remoteAddress().getHostString(),
                g.getClient().remoteAddress().getPort(),
                project,
                checkerUuid,
                checkerName,
                description,
                status,
                blocking,
                query),
            true));

    CheckerInput checkerInput = createCheckerInput();
    String expectedUrl = "/a/plugins/checks/checkers/";

    g.getClient()
        .when(HttpRequest.request(expectedUrl + checkerInput.uuid).withMethod("GET"))
        .respond(HttpResponse.response().withStatusCode(404));

    g.getClient()
        .when(
            HttpRequest.request(expectedUrl)
                .withMethod("POST")
                .withBody(JsonBody.json(checkerInput)))
        .respond(
            HttpResponse.response()
                .withStatusCode(201)
                .withBody(JsonBody.json(Collections.emptyMap())));

    WorkflowRun run = j.assertBuildStatusSuccess(p.scheduleBuild2(0));
    String log = JenkinsRule.getLog(run);

    g.getClient()
        .verify(HttpRequest.request(expectedUrl + checkerInput.uuid))
        .clear(HttpRequest.request(expectedUrl + checkerInput.uuid))
        .verify(HttpRequest.request(expectedUrl), VerificationTimes.once());
  }

  @Test
  public void gerritCheckStepInvokeTestUpdatesChecker() throws Exception {
    p.setDefinition(
        new CpsFlowDefinition(
            String.format(
                ""
                    + "node {\n"
                    + "  withEnv([\n"
                    + "    'GERRIT_API_URL=https://%s:%s/a/project',\n"
                    + "    'GERRIT_CREDENTIALS_ID=cid',\n"
                    + "    'GERRIT_API_INSECURE_HTTPS=true',\n"
                    + "    'GERRIT_PROJECT=%s',\n"
                    + "  ]) {\n"
                    + "    gerritChecker uuid: '%s', name: '%s', description: '%s', status: '%s', blocking: ['%s'], query: '%s'\n"
                    + "  }\n"
                    + "}",
                g.getClient().remoteAddress().getHostString(),
                g.getClient().remoteAddress().getPort(),
                project,
                checkerUuid,
                checkerName,
                description,
                status,
                blocking,
                query),
            true));

    CheckerInput checkerInput = createCheckerInput();
    String expectedUrl = "/a/plugins/checks/checkers/" + checkerInput.uuid;

    g.getClient()
        .when(HttpRequest.request(expectedUrl).withMethod("GET"))
        .respond(HttpResponse.response().withStatusCode(200));

    g.getClient()
        .when(
            HttpRequest.request(expectedUrl)
                .withMethod("POST")
                .withBody(JsonBody.json(checkerInput)))
        .respond(
            HttpResponse.response()
                .withStatusCode(200)
                .withBody(JsonBody.json(Collections.emptyMap())));

    WorkflowRun run = j.assertBuildStatusSuccess(p.scheduleBuild2(0));
    String log = JenkinsRule.getLog(run);

    g.getClient().verify(HttpRequest.request(expectedUrl), VerificationTimes.exactly(2));
  }

  private CheckerInput createCheckerInput() throws IOException {
    CheckerInput checkerInput = new CheckerInputForObjectMapper();
    checkerInput.uuid = checkerUuid;
    checkerInput.name = checkerName;
    checkerInput.description = description;
    checkerInput.repository = project;
    checkerInput.url = j.getURL().toString() + p.getUrl();
    checkerInput.status = CheckerStatus.valueOf(status);
    BlockingCondition[] blockingArray =
        new BlockingCondition[] {BlockingCondition.valueOf(blocking)};
    Set<BlockingCondition> blockingSet = EnumSet.noneOf(BlockingCondition.class);
    blockingSet.addAll(Arrays.asList(blockingArray));
    checkerInput.blocking = blockingSet;
    checkerInput.query = query;
    return checkerInput;
  }

  @JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
  private static class CheckerInputForObjectMapper extends CheckerInput {}
}

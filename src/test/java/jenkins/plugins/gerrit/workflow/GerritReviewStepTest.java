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

import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.exactly;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import hudson.model.Result;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.jvnet.hudson.test.JenkinsRule;

@RunWith(Parameterized.class)
public class GerritReviewStepTest {
  @Rule
  public WireMockRule wireMock =
      new WireMockRule(wireMockConfig().dynamicHttpsPort().httpDisabled(true));

  @Rule public JenkinsRule j = new JenkinsRule();

  private static final String projectName = "test-project";
  private static final int changeNumber = 4321;
  private final String gerritVersion;
  private final String changeId;

  public GerritReviewStepTest(String gerritVersion, String changeId) {
    this.gerritVersion = gerritVersion;
    this.changeId = changeId;
  }

  @Parameterized.Parameters
  public static Collection<Object[]> gerritVersionToChangeIdData() {
    return Arrays.asList(
        new Object[][] {
          {"2.14", String.valueOf(changeNumber)},
          {"2.16", String.format("%s~%s", projectName, changeNumber)}
        });
  }

  @Before
  public void setup() {
    setupServerVersion();
  }

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

    j.assertLogContains("Gerrit Review requires authentication", run);
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

    j.assertLogContains("Gerrit Review requires authentication", run);
  }

  @Test
  public void gerritReviewStepInvokeFailSSLValidationTest() throws Exception {
    int revision = 1;
    String label = "Verfied";
    int score = -1;
    String message = "Does not work";
    String branch = String.format("%02d/%d/%d", changeNumber % 100, changeNumber, revision);

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
                "localhost", wireMock.httpsPort(), branch, label, score, message),
            true));

    WorkflowRun run = j.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0));
    String log = JenkinsRule.getLog(run);

    j.assertLogContains("javax.net.ssl.SSLHandshakeException", run);
  }

  @Test
  public void gerritReviewStepInvokeTest() throws Exception {
    int revision = 1;
    String label = "Verfied";
    int score = -1;
    String message = "Does not work";
    String branch = String.format("%02d/%d/%d", changeNumber % 100, changeNumber, revision);

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
                    + "    'GERRIT_API_URL=https://%s:%s',\n"
                    + "    'GERRIT_API_INSECURE_HTTPS=true',\n"
                    + "    'GERRIT_CREDENTIALS_ID=cid',\n"
                    + "    'GERRIT_PROJECT=%s',\n"
                    + "    'BRANCH_NAME=%s',\n"
                    + "  ]) {\n"
                    + "    gerritReview labels: ['%s': %s], message: '%s'\n"
                    + "  }\n"
                    + "}",
                "localhost", wireMock.httpsPort(), projectName, branch, label, score, message),
            true));

    ReviewInput reviewInput = new ReviewInput().label(label, score).message(message);
    reviewInput.drafts = ReviewInput.DraftHandling.PUBLISH;
    reviewInput.tag = "autogenerated:jenkins";
    reviewInput.notify = NotifyHandling.OWNER;
    stubFor(
        post(urlEqualTo(String.format("/a/changes/%s/revisions/%s/review", changeId, revision)))
            .withRequestBody(
                equalToJson(
                    new GsonBuilder()
                        .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
                        .create()
                        .toJson(reviewInput)))
            .willReturn(ok(new Gson().toJson(Map.of()))));

    WorkflowRun run = j.assertBuildStatusSuccess(p.scheduleBuild2(0));
    String log = JenkinsRule.getLog(run);

    verify(
        exactly(1),
        postRequestedFor(
            urlEqualTo(String.format("/a/changes/%s/revisions/%s/review", changeId, revision))));
  }

  @Test
  public void gerritReviewStepInvokeLabelsTest() throws Exception {
    int revision = 1;
    String label1 = "Verfied";
    int score1 = -1;
    String label2 = "CI-Review";
    int score2 = -1;
    String message = "Does not work";
    String branch = String.format("%02d/%d/%d", changeNumber % 100, changeNumber, revision);

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
                    + "    'GERRIT_API_URL=https://%s:%s',\n"
                    + "    'GERRIT_API_INSECURE_HTTPS=true',\n"
                    + "    'GERRIT_CREDENTIALS_ID=cid',\n"
                    + "    'GERRIT_PROJECT=%s',\n"
                    + "    'BRANCH_NAME=%s',\n"
                    + "  ]) {\n"
                    + "    gerritReview labels: ['%s': %s, '%s': %s], message: '%s', notify: 'NONE'\n"
                    + "  }\n"
                    + "}",
                "localhost",
                wireMock.httpsPort(),
                projectName,
                branch,
                label1,
                score1,
                label2,
                score2,
                message),
            true));

    ReviewInput reviewInput =
        new ReviewInput().label(label1, score1).label(label2, score2).message(message);
    reviewInput.drafts = ReviewInput.DraftHandling.PUBLISH;
    reviewInput.tag = "autogenerated:jenkins";
    reviewInput.notify = NotifyHandling.NONE;
    stubFor(
        post(urlEqualTo(String.format("/a/changes/%s/revisions/%s/review", changeId, revision)))
            .withRequestBody(
                equalToJson(
                    new GsonBuilder()
                        .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
                        .create()
                        .toJson(reviewInput),
                    true,
                    true))
            .willReturn(ok(new Gson().toJson(Map.of()))));

    WorkflowRun run = j.assertBuildStatusSuccess(p.scheduleBuild2(0));
    String log = JenkinsRule.getLog(run);

    verify(
        exactly(1),
        postRequestedFor(
            urlEqualTo(String.format("/a/changes/%s/revisions/%s/review", changeId, revision))));
  }

  @Test
  public void gerritReviewStepInvokeNotifyPropTest() throws Exception {
    // This test is exactly the same as gerritReviewStepInvokeLabelsTest, but with notify set to
    // NONE
    String genuineNotifyValue = System.getProperty("gerrit.notify");
    System.setProperty("gerrit.notify", "NONE"); // Changed
    try {
      int revision = 1;
      String label1 = "Verfied";
      int score1 = -1;
      String label2 = "CI-Review";
      int score2 = -1;
      String message = "Does not work";
      String branch = String.format("%02d/%d/%d", changeNumber % 100, changeNumber, revision);

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
                      + "    'GERRIT_API_URL=https://%s:%s',\n"
                      + "    'GERRIT_API_INSECURE_HTTPS=true',\n"
                      + "    'GERRIT_CREDENTIALS_ID=cid',\n"
                      + "    'GERRIT_PROJECT=%s',\n"
                      + "    'BRANCH_NAME=%s',\n"
                      + "  ]) {\n"
                      + "    gerritReview labels: ['%s': %s, '%s': %s], message: '%s'\n"
                      + "  }\n"
                      + "}",
                  "localhost",
                  wireMock.httpsPort(),
                  projectName,
                  branch,
                  label1,
                  score1,
                  label2,
                  score2,
                  message),
              true));

      ReviewInput reviewInput =
          new ReviewInput().label(label1, score1).label(label2, score2).message(message);
      reviewInput.drafts = ReviewInput.DraftHandling.PUBLISH;
      reviewInput.tag = "autogenerated:jenkins";
      reviewInput.notify = NotifyHandling.NONE; // Changed
      stubFor(
          post(urlEqualTo(String.format("/a/changes/%s/revisions/%s/review", changeId, revision)))
              .withRequestBody(
                  equalToJson(
                      new GsonBuilder()
                          .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
                          .create()
                          .toJson(reviewInput)))
              .willReturn(ok(new Gson().toJson(Map.of()))));

      WorkflowRun run = j.assertBuildStatusSuccess(p.scheduleBuild2(0));
      String log = JenkinsRule.getLog(run);

      verify(
          exactly(1),
          postRequestedFor(
              urlEqualTo(String.format("/a/changes/%s/revisions/%s/review", changeId, revision))));
    } finally {
      if (genuineNotifyValue == null) {
        System.clearProperty("gerrit.notify");
      } else {
        System.setProperty("gerrit.notify", genuineNotifyValue);
      }
    }
  }

  private void setupServerVersion() {
    stubFor(
        get(urlEqualTo("/a/config/server/version"))
            .willReturn(ok(")]}'\n\"" + gerritVersion + "\"")));
  }
}

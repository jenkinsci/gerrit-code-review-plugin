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

import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.exactly;
import static com.github.tomakehurst.wiremock.client.WireMock.findAll;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static com.google.gson.FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES;
import static java.time.Instant.now;
import static java.time.ZoneOffset.UTC;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.lessThanOrEqualTo;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.google.gerrit.plugins.checks.api.CheckInput;
import com.google.gerrit.plugins.checks.api.CheckState;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import hudson.model.Result;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

public class GerritCheckStepTest {

  @Rule
  public WireMockRule wireMock =
      new WireMockRule(wireMockConfig().dynamicHttpsPort().httpDisabled(true));

  @Rule public JenkinsRule j = new JenkinsRule();

  @Test
  public void gerritCheckStepInvokeFailSSLValidationTest() throws Exception {
    int changeId = 4321;
    int revision = 1;
    String checkerUuid = "checker";
    String checkStatus = "SUCCESSFUL";
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
                    + "    gerritCheck checks: [%s: '%s'], message: '%s'\n"
                    + "  }\n"
                    + "}",
                "localhost",
                wireMock.httpsPort(),
                branch,
                checkerUuid,
                checkStatus,
                message),
            true));

    WorkflowRun run = j.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0));
    String log = JenkinsRule.getLog(run);

    j.assertLogContains("javax.net.ssl.SSLHandshakeException", run);
  }

  @Test
  public void gerritCheckStepInvokeTest() throws Exception {
    int changeId = 4321;
    int revision = 1;
    String checkerUuid = "checker";
    String checkStatus = "SUCCESSFUL";
    String message = "Does work";
    String branch = String.format("%02d/%d/%d", changeId % 100, changeId, revision);

    UsernamePasswordCredentialsImpl c =
        new UsernamePasswordCredentialsImpl(
            CredentialsScope.GLOBAL, "cid", "cid", "USERNAME", "PASSWORD");
    CredentialsProvider.lookupStores(j.jenkins)
        .iterator()
        .next()
        .addCredentials(Domain.global(), c);
    WorkflowJob p = j.jenkins.createProject(WorkflowJob.class, "q");
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
                    + "    gerritCheck checks: [%s: '%s'], message: '%s'\n"
                    + "  }\n"
                    + "}",
                "localhost",
                wireMock.httpsPort(),
                branch,
                checkerUuid,
                checkStatus,
                message),
            true));

    String expectedUrl = String.format("/a/changes/%s/revisions/%s/checks/", changeId, revision);

    CheckInput checkInput = new CheckInput();
    checkInput.checkerUuid = checkerUuid;
    checkInput.state = CheckState.valueOf(checkStatus);
    checkInput.message = message;
    checkInput.url = j.getURL().toString() + p.getUrl() + "1/console";
    stubFor(
        post(expectedUrl)
            .withRequestBody(
                equalToJson(
                    new Gson()
                        .newBuilder()
                        .setFieldNamingStrategy(LOWER_CASE_WITH_UNDERSCORES)
                        .create()
                        .toJson(checkInput),
                    true,
                    true))
            .willReturn(ok()));

    WorkflowRun run = j.assertBuildStatusSuccess(p.scheduleBuild2(0));
    String log = JenkinsRule.getLog(run);

    verify(exactly(1), postRequestedFor(urlEqualTo(expectedUrl)));
  }

  @Test
  public void gerritCheckStepTestWithUrlSet() throws Exception {
    int changeId = 4321;
    int revision = 1;
    String checkerUuid = "checker";
    String checkStatus = "SUCCESSFUL";
    String url = "https://example.com/test";
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
                    + "    gerritCheck checks: [%s: '%s'], url: '%s'\n"
                    + "  }\n"
                    + "}",
                "localhost",
                wireMock.httpsPort(),
                branch,
                checkerUuid,
                checkStatus,
                url),
            true));

    String expectedUrl = String.format("/a/changes/%s/revisions/%s/checks/", changeId, revision);

    CheckInput checkInput = new CheckInput();
    checkInput.checkerUuid = checkerUuid;
    checkInput.state = CheckState.valueOf(checkStatus);
    checkInput.url = url;
    stubFor(
        post(expectedUrl)
            .withRequestBody(
                equalToJson(
                    new Gson()
                        .newBuilder()
                        .setFieldNamingStrategy(LOWER_CASE_WITH_UNDERSCORES)
                        .create()
                        .toJson(checkInput),
                    true,
                    true))
            .willReturn(ok()));

    WorkflowRun run = j.assertBuildStatusSuccess(p.scheduleBuild2(0));
    String log = JenkinsRule.getLog(run);

    verify(exactly(1), postRequestedFor(urlEqualTo(expectedUrl)));
  }

  @Test
  public void gerritCheckStepTestProducesUtcTimestamps() throws Exception {
    long beginTestEpoch = now().getEpochSecond();
    int changeId = 4321;
    int revision = 1;
    String checkerUuid = "checker";
    String checkStatus = "RUNNING";
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
                    + "    gerritCheck checks: [%s: '%s']\n"
                    + "  }\n"
                    + "}",
                "localhost",
                wireMock.httpsPort(),
                branch,
                checkerUuid,
                checkStatus),
            true));

    String expectedUrl = String.format("/a/changes/%s/revisions/%s/checks/", changeId, revision);

    stubFor(post(expectedUrl).willReturn(ok()));

    WorkflowRun run = j.assertBuildStatusSuccess(p.scheduleBuild2(0));
    String log = JenkinsRule.getLog(run);

    verify(exactly(1), postRequestedFor(urlEqualTo(expectedUrl)));
    // It's very difficult to run verifications on the request body using verify(),
    // but we can extract and parse the requests as JSON
    String startedAt = requestBodyJson(expectedUrl).getAsJsonObject().get("started").getAsString();

    // validate that the timestamp is in the proper format (not ISO-8601, not epoch millis, etc)
    assertThat(
        "Timestamp should be in SQL format",
        startedAt,
        matchesPattern("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3}"));

    // Whatever timestamp was formatted, validate that when it is parsed as a UTC time,
    // it represents current time, +/- 10 seconds
    long parsedEpoch = instantFromUtc(startedAt).getEpochSecond();
    long endTestEpoch = now().getEpochSecond();
    assertThat(
        "Timestamp parsed as UTC should be between the time ranges of the test.",
        parsedEpoch,
        allOf(lessThanOrEqualTo(endTestEpoch), greaterThanOrEqualTo(beginTestEpoch)));
  }

  private static Instant instantFromUtc(String s) {
    // Parse the "local"-format timestamp text as if it is UTC
    DateTimeFormatter formatter =
        new DateTimeFormatterBuilder()
            .append(DateTimeFormatter.ISO_LOCAL_DATE)
            .appendLiteral(' ')
            .append(DateTimeFormatter.ISO_LOCAL_TIME)
            .toFormatter()
            .withZone(UTC);
    return ZonedDateTime.parse(s, formatter).toInstant();
  }

  /**
   * Returns the JsonElement representation of the request body matching {@code requestUrl}.
   *
   * @param requestUrl the HttpRequest url to match
   * @return {@link JsonElement} of the request body
   */
  private JsonElement requestBodyJson(String requestUrl) {
    Gson gson = new Gson();

    String request = findAll(postRequestedFor(urlEqualTo(requestUrl))).get(0).getBodyAsString();
    return gson.fromJson(request, JsonElement.class);
  }

  private static Matcher<String> matchesPattern(String pattern) {
    return new TypeSafeMatcher<>() {
      @Override
      protected boolean matchesSafely(String s) {
        return s.matches(pattern);
      }

      @Override
      public void describeTo(Description description) {
        description.appendText("matchesPattern /" + pattern + "/");
      }
    };
  }
}

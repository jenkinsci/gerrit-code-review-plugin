// Copyright (C) 2026 NVIDIA Corporation
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

package com.google.gerrit.plugins.checks.client;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.plugins.checks.api.CheckInfo;
import com.google.gerrit.plugins.checks.api.CheckInput;
import com.google.gerrit.plugins.checks.api.CheckState;
import com.google.gson.reflect.TypeToken;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedList;
import java.util.Queue;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.eclipse.jgit.transport.URIish;
import org.junit.Rule;
import org.junit.Test;
import org.mockserver.junit.MockServerRule;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.verify.VerificationTimes;

public class ChecksTerminalUpdateTest {
  private static final int CHANGE_NUMBER = 1;
  private static final int PATCH_SET_NUMBER = 2;

  @Rule public MockServerRule g = new MockServerRule(this);

  @Test
  public void matchingFinishedCheckSkipsPost() throws Exception {
    Timestamp desiredFinished = new Timestamp(1_000L);
    CheckInput desired = terminalInput(desiredFinished);
    CheckInfo existing = matchingInfo(desired, desiredFinished);
    FakeChecks checks = new FakeChecks();
    checks.reads.add(existing);

    assertSame(existing, checks.updateTerminal(desired));
    assertEquals(1, checks.readCount);
    assertEquals(0, checks.updateCount);
  }

  @Test
  public void matchingCheckFromDifferentPublicationDoesNotSkipPost() throws Exception {
    Timestamp desiredFinished = new Timestamp(1_000L);
    CheckInput desired = terminalInput(desiredFinished);
    CheckInfo existing = matchingInfo(desired, new Timestamp(999L));
    CheckInfo updated = matchingInfo(desired, desiredFinished);
    FakeChecks checks = new FakeChecks();
    checks.reads.add(existing);
    checks.updateResult = updated;

    assertSame(updated, checks.updateTerminal(desired));
    assertEquals(1, checks.readCount);
    assertEquals(1, checks.updateCount);
  }

  @Test
  public void matchingCheckWithoutFinishedTimestampDoesNotSkipPost() throws Exception {
    CheckInput desired = terminalInput(new Timestamp(1_000L));
    CheckInfo existing = matchingInfo(desired, null);
    CheckInfo updated = matchingInfo(desired, desired.finished);
    FakeChecks checks = new FakeChecks();
    checks.reads.add(existing);
    checks.updateResult = updated;

    assertSame(updated, checks.updateTerminal(desired));
    assertEquals(1, checks.updateCount);
  }

  @Test
  public void exactReadbackReconcilesAmbiguousPost() throws Exception {
    Timestamp frozenFinished = new Timestamp(1_000L);
    CheckInput desired = terminalInput(frozenFinished);
    CheckInfo observed = matchingInfo(desired, frozenFinished);
    FakeChecks checks = new FakeChecks();
    checks.reads.add(null);
    checks.reads.add(observed);
    checks.updateFailure =
        new Checks.AmbiguousCheckUpdateException("response lost", new java.io.IOException());

    assertSame(observed, checks.updateTerminal(desired));
    assertEquals(2, checks.readCount);
    assertEquals(1, checks.updateCount);
    assertSame(frozenFinished, checks.lastUpdate.finished);
  }

  @Test
  public void ambiguousMismatchDoesNotRetryWithoutServerCas() throws Exception {
    CheckInput desired = terminalInput(new Timestamp(1_000L));
    CheckInfo newerWriter = matchingInfo(desired, new Timestamp(2_000L));
    FakeChecks checks = new FakeChecks();
    checks.reads.add(null);
    checks.reads.add(newerWriter);
    checks.updateFailure =
        new Checks.AmbiguousCheckUpdateException("response lost", new java.io.IOException());

    try {
      checks.updateTerminal(desired);
      fail("Expected unreconciled ambiguous update to fail");
    } catch (RestApiException expected) {
      assertEquals(1, checks.updateCount);
      assertEquals(2, checks.readCount);
    }
  }

  @Test
  public void definitivePostFailureIsNeverRetriedOrReadBack() throws Exception {
    CheckInput desired = terminalInput(new Timestamp(1_000L));
    FakeChecks checks = new FakeChecks();
    checks.reads.add(null);
    checks.updateFailure = new RestApiException("HTTP 400");

    try {
      checks.updateTerminal(desired);
      fail("Expected definitive update failure");
    } catch (RestApiException expected) {
      assertEquals(1, checks.updateCount);
      assertEquals(1, checks.readCount);
    }
  }

  @Test
  public void requestTimeoutIsReconciledByExactReadback() throws Exception {
    assertAmbiguousHttpStatusReconciled(408);
  }

  @Test
  public void gatewayTimeoutIsReconciledByExactReadback() throws Exception {
    assertAmbiguousHttpStatusReconciled(504);
  }

  @Test
  public void nanosecondWidthResponseTimestampReconcilesAmbiguousPost() throws Exception {
    Timestamp frozenFinished = Timestamp.from(Instant.parse("2019-01-31T09:59:32.126789123Z"));
    Timestamp wireFinished = Timestamp.from(Instant.parse("2019-01-31T09:59:32.126Z"));
    CheckInput desired = terminalInput(frozenFinished);
    String requestPath = checksCollectionPath();
    String readbackPath = requestPath + desired.checkerUuid;
    g.getClient()
        .when(HttpRequest.request(requestPath).withMethod("POST"))
        .respond(HttpResponse.response().withStatusCode(504).withBody("ambiguous"));
    g.getClient()
        .when(HttpRequest.request(readbackPath).withMethod("GET"))
        .respond(
            HttpResponse.response()
                .withStatusCode(200)
                .withBody(
                    ")]}'\n"
                        + "{\"checker_uuid\":\"checker:uuid\","
                        + "\"state\":\"SUCCESSFUL\",\"message\":\"done\","
                        + "\"url\":\"https://jenkins.example/job/1\","
                        + "\"finished\":\"2019-01-31 09:59:32.126000000\"}"));

    try (CloseableHttpClient client = HttpClientBuilder.create().build()) {
      InitialMissThenHttpReadbackChecks checks =
          new InitialMissThenHttpReadbackChecks(checksBaseUrl(), client);
      checks.change(CHANGE_NUMBER).patchSet(PATCH_SET_NUMBER);

      CheckInfo observed = checks.updateTerminal(desired);
      assertEquals(wireFinished, observed.finished);
    }

    g.getClient()
        .verify(HttpRequest.request(requestPath).withMethod("POST"), VerificationTimes.once());
    g.getClient()
        .verify(HttpRequest.request(readbackPath).withMethod("GET"), VerificationTimes.once());
  }

  @Test
  public void badRequestIsDefinitiveAndIsNotReadBack() throws Exception {
    CheckInput desired = terminalInput(new Timestamp(1_000L));
    String requestPath = checksCollectionPath();
    g.getClient()
        .when(HttpRequest.request(requestPath).withMethod("POST"))
        .respond(HttpResponse.response().withStatusCode(400).withBody("bad request"));

    try (CloseableHttpClient client = HttpClientBuilder.create().build()) {
      ReadbackChecks checks = newReadbackChecks(client);
      checks.reads.add(null);
      checks.reads.add(matchingInfo(desired, desired.finished));
      try {
        checks.updateTerminal(desired);
        fail("Expected definitive HTTP 400 failure");
      } catch (RestApiException expected) {
        assertEquals(1, checks.readCount);
      }
    }

    g.getClient()
        .verify(HttpRequest.request(requestPath).withMethod("POST"), VerificationTimes.once());
  }

  @Test
  public void noContentIsAcceptedAsSuccessfulPost() throws Exception {
    CheckInput desired = terminalInput(new Timestamp(1_000L));
    String requestPath = checksCollectionPath();
    g.getClient()
        .when(HttpRequest.request(requestPath).withMethod("POST"))
        .respond(HttpResponse.response().withStatusCode(204));

    try (CloseableHttpClient client = HttpClientBuilder.create().build()) {
      ReadbackChecks checks = newReadbackChecks(client);
      checks.reads.add(null);

      assertNull(checks.updateTerminal(desired));
      assertEquals(1, checks.readCount);
    }

    g.getClient()
        .verify(HttpRequest.request(requestPath).withMethod("POST"), VerificationTimes.once());
  }

  @Test
  public void postUsesUtf8JsonBody() throws Exception {
    CheckInput desired = terminalInput(new Timestamp(1_000L));
    desired.message = "réussi ✓";
    String requestPath = checksCollectionPath();
    String expectedBody =
        AbstractEndpoint.JsonBodyParser.createRequestBody(
            desired, new TypeToken<CheckInput>() {}.getType());
    HttpRequest expectedRequest =
        HttpRequest.request(requestPath)
            .withMethod("POST")
            .withHeader("Content-Type", "application/json; charset=UTF-8")
            .withBody(expectedBody);
    g.getClient().when(expectedRequest).respond(HttpResponse.response().withStatusCode(204));

    try (CloseableHttpClient client = HttpClientBuilder.create().build()) {
      ReadbackChecks checks = newReadbackChecks(client);
      checks.reads.add(null);

      assertNull(checks.updateTerminal(desired));
    }

    g.getClient().verify(expectedRequest, VerificationTimes.once());
  }

  private void assertAmbiguousHttpStatusReconciled(int statusCode) throws Exception {
    Timestamp frozenFinished = new Timestamp(1_000L);
    CheckInput desired = terminalInput(frozenFinished);
    CheckInfo observed = matchingInfo(desired, frozenFinished);
    String requestPath = checksCollectionPath();
    g.getClient()
        .when(HttpRequest.request(requestPath).withMethod("POST"))
        .respond(HttpResponse.response().withStatusCode(statusCode).withBody("ambiguous"));

    try (CloseableHttpClient client = HttpClientBuilder.create().build()) {
      ReadbackChecks checks = newReadbackChecks(client);
      checks.reads.add(null);
      checks.reads.add(observed);

      assertSame(observed, checks.updateTerminal(desired));
      assertEquals(2, checks.readCount);
    }

    g.getClient()
        .verify(HttpRequest.request(requestPath).withMethod("POST"), VerificationTimes.once());
  }

  private ReadbackChecks newReadbackChecks(CloseableHttpClient client) throws Exception {
    ReadbackChecks checks = new ReadbackChecks(checksBaseUrl(), client);
    checks.change(CHANGE_NUMBER).patchSet(PATCH_SET_NUMBER);
    return checks;
  }

  private URIish checksBaseUrl() throws Exception {
    return new URIish(
        String.format(
            "http://%s:%d",
            g.getClient().remoteAddress().getHostString(),
            g.getClient().remoteAddress().getPort()));
  }

  private static String checksCollectionPath() {
    return String.format("/changes/%d/revisions/%d/checks/", CHANGE_NUMBER, PATCH_SET_NUMBER);
  }

  private static CheckInput terminalInput(Timestamp finished) {
    CheckInput input = new CheckInput();
    input.checkerUuid = "checker:uuid";
    input.state = CheckState.SUCCESSFUL;
    input.message = "done";
    input.url = "https://jenkins.example/job/1";
    input.finished = finished;
    return input;
  }

  private static CheckInfo matchingInfo(CheckInput desired, Timestamp finished) {
    CheckInfo info = new CheckInfo();
    info.checkerUuid = desired.checkerUuid;
    info.state = desired.state;
    info.message = desired.message;
    info.url = desired.url;
    info.finished = finished;
    return info;
  }

  private static class FakeChecks extends Checks {
    private final Queue<CheckInfo> reads = new LinkedList<>();
    private int readCount;
    private int updateCount;
    private CheckInput lastUpdate;
    private CheckInfo updateResult;
    private RestApiException updateFailure;

    FakeChecks() throws Exception {
      super(new URIish("https://gerrit.example"), null, false);
    }

    @Override
    public CheckInfo getIfPresent(String checkerUuid) {
      readCount++;
      return reads.remove();
    }

    @Override
    public CheckInfo update(CheckInput input) throws RestApiException {
      updateCount++;
      lastUpdate = input;
      if (updateFailure != null) {
        throw updateFailure;
      }
      return updateResult;
    }
  }

  private static class ReadbackChecks extends Checks {
    private final Queue<CheckInfo> reads = new LinkedList<>();
    private int readCount;

    ReadbackChecks(URIish baseUrl, CloseableHttpClient client) throws Exception {
      super(baseUrl, client, false);
    }

    @Override
    public CheckInfo getIfPresent(String checkerUuid) {
      readCount++;
      return reads.remove();
    }
  }

  private static class InitialMissThenHttpReadbackChecks extends Checks {
    private boolean initialRead = true;

    InitialMissThenHttpReadbackChecks(URIish baseUrl, CloseableHttpClient client) throws Exception {
      super(baseUrl, client, false);
    }

    @Override
    public CheckInfo getIfPresent(String checkerUuid) throws RestApiException {
      if (initialRead) {
        initialRead = false;
        return null;
      }
      return super.getIfPresent(checkerUuid);
    }
  }
}

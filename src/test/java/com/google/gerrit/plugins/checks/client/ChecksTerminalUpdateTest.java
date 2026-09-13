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
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.plugins.checks.api.CheckInfo;
import com.google.gerrit.plugins.checks.api.CheckInput;
import com.google.gerrit.plugins.checks.api.CheckState;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
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
  public void successfulPostDoesNotReadBeforeOrAfter() throws Exception {
    CheckInput desired = terminalInput(new Timestamp(1_000L));
    CheckInfo updated = matchingInfo(desired, desired.finished);
    FakeChecks checks = new FakeChecks(0L);
    checks.updateResult = updated;

    assertSame(updated, checks.updateTerminal(desired));
    assertEquals(1, checks.updateCount);
    assertEquals(0, checks.readCount);
  }

  @Test
  public void terminalInputRequiresTerminalState() throws Exception {
    CheckInput desired = terminalInput(new Timestamp(1_000L));
    desired.state = CheckState.RUNNING;
    FakeChecks checks = new FakeChecks(0L);

    assertRejectedBeforePost(checks, desired);
  }

  @Test
  public void terminalInputRequiresNonemptyCheckerUuid() throws Exception {
    for (String checkerUuid : new String[] {null, "", " \t\n"}) {
      CheckInput desired = terminalInput(new Timestamp(1_000L));
      desired.checkerUuid = checkerUuid;
      FakeChecks checks = new FakeChecks(0L);

      assertRejectedBeforePost(checks, desired);
    }
  }

  @Test
  public void terminalInputRequiresNonNullNonEpochFinished() throws Exception {
    for (Timestamp finished : new Timestamp[] {null, new Timestamp(0L)}) {
      CheckInput desired = terminalInput(finished);
      FakeChecks checks = new FakeChecks(0L);

      assertRejectedBeforePost(checks, desired);
    }
  }

  @Test
  public void ambiguousPostPollsPastMissingAndStaleReadsUntilMatch() throws Exception {
    CheckInput desired = terminalInput(new Timestamp(1_000L));
    CheckInfo stale = matchingInfo(desired, new Timestamp(999L));
    CheckInfo observed = matchingInfo(desired, desired.finished);
    FakeChecks checks = new FakeChecks(0L, 0L, 0L);
    checks.reads.add(null);
    checks.reads.add(stale);
    checks.reads.add(observed);
    checks.updateFailure = ambiguousFailure();

    assertSame(observed, checks.updateTerminal(desired));
    assertEquals(1, checks.updateCount);
    assertEquals(3, checks.readCount);
    assertSame(desired.finished, checks.lastUpdate.finished);
  }

  @Test
  public void ambiguousMismatchExhaustsScheduleWithoutSecondPost() throws Exception {
    CheckInput desired = terminalInput(new Timestamp(1_000L));
    CheckInfo stale = matchingInfo(desired, new Timestamp(999L));
    Checks.AmbiguousCheckUpdateException ambiguous = ambiguousFailure();
    FakeChecks checks = new FakeChecks(0L, 0L, 0L);
    checks.reads.add(null);
    checks.reads.add(stale);
    checks.reads.add(stale);
    checks.updateFailure = ambiguous;

    RestApiException failure = expectUnreconciled(checks, desired);

    assertSame(ambiguous, failure.getCause());
    assertEquals(1, checks.updateCount);
    assertEquals(3, checks.readCount);
  }

  @Test
  public void readFailuresAreSuppressedOnUnreconciledResult() throws Exception {
    CheckInput desired = terminalInput(new Timestamp(1_000L));
    RestApiException firstReadFailure = new RestApiException("first read failed");
    RestApiException secondReadFailure = new RestApiException("second read failed");
    Checks.AmbiguousCheckUpdateException ambiguous = ambiguousFailure();
    FakeChecks checks = new FakeChecks(0L, 0L, 0L);
    checks.reads.add(firstReadFailure);
    checks.reads.add(null);
    checks.reads.add(secondReadFailure);
    checks.updateFailure = ambiguous;

    RestApiException failure = expectUnreconciled(checks, desired);

    assertSame(ambiguous, failure.getCause());
    assertEquals(2, failure.getSuppressed().length);
    assertSame(firstReadFailure, failure.getSuppressed()[0]);
    assertSame(secondReadFailure, failure.getSuppressed()[1]);
    assertEquals(1, checks.updateCount);
  }

  @Test
  public void interruptionBeforePostStopsMutation() throws Exception {
    CheckInput desired = terminalInput(new Timestamp(1_000L));
    FakeChecks checks = new FakeChecks(0L, 0L);

    Thread.currentThread().interrupt();
    try {
      checks.updateTerminal(desired);
      fail("Expected interruption");
    } catch (InterruptedException expected) {
      assertTrue(Thread.currentThread().isInterrupted());
      assertEquals(0, checks.updateCount);
      assertEquals(0, checks.readCount);
    } finally {
      Thread.interrupted();
    }
  }

  @Test
  public void interruptionAfterAmbiguousPostStopsBeforeFirstRead() throws Exception {
    CheckInput desired = terminalInput(new Timestamp(1_000L));
    Checks.AmbiguousCheckUpdateException ambiguous = ambiguousFailure();
    FakeChecks checks = new FakeChecks(0L, 0L);
    checks.reads.add(matchingInfo(desired, desired.finished));
    checks.updateFailure = ambiguous;
    checks.interruptAfterUpdate = true;

    try {
      checks.updateTerminal(desired);
      fail("Expected interruption");
    } catch (InterruptedException expected) {
      assertSame(ambiguous, expected.getCause());
      assertEquals(1, checks.updateCount);
      assertEquals(0, checks.readCount);
    } finally {
      Thread.interrupted();
    }
  }

  @Test
  public void interruptionDuringMatchingReadStopsBeforeSuccess() throws Exception {
    CheckInput desired = terminalInput(new Timestamp(1_000L));
    FakeChecks checks = new FakeChecks(0L, 0L);
    checks.reads.add(matchingInfo(desired, desired.finished));
    checks.updateFailure = ambiguousFailure();
    checks.interruptAfterRead = true;

    try {
      checks.updateTerminal(desired);
      fail("Expected interruption");
    } catch (InterruptedException expected) {
      assertEquals(1, checks.updateCount);
      assertEquals(1, checks.readCount);
    } finally {
      Thread.interrupted();
    }
  }

  @Test
  public void reconciliationRequiresExactCheckerUuidAndState() throws Exception {
    CheckInput desired = terminalInput(new Timestamp(1_000L));

    CheckInfo differentUuid = matchingInfo(desired, desired.finished);
    differentUuid.checkerUuid = "checker:UUID";
    assertDoesNotReconcile(desired, differentUuid);

    CheckInfo differentState = matchingInfo(desired, desired.finished);
    differentState.state = CheckState.FAILED;
    assertDoesNotReconcile(desired, differentState);
  }

  @Test
  public void reconciliationTrimsNonNullMessageAndUrl() throws Exception {
    CheckInput desired = terminalInput(new Timestamp(1_000L));
    desired.message = " \tdone\n";
    desired.url = " https://jenkins.example/job/1\r ";
    CheckInfo observed = matchingInfo(desired, desired.finished);
    observed.message = "done";
    observed.url = "https://jenkins.example/job/1";

    assertReconciles(desired, observed);
  }

  @Test
  public void reconciliationCanonicalizesNonNullEmptyStringsToNull() throws Exception {
    CheckInput desired = terminalInput(new Timestamp(1_000L));
    desired.message = " \t";
    desired.url = "\n";
    CheckInfo observed = matchingInfo(desired, desired.finished);
    observed.message = null;
    observed.url = "  ";

    assertReconciles(desired, observed);
  }

  @Test
  public void defaultEmptyMessageReconcilesWithAbsentServerField() throws Exception {
    CheckInput desired = terminalInput(new Timestamp(1_000L));
    desired.message = "";
    CheckInfo observed = matchingInfo(desired, desired.finished);
    observed.message = null;

    assertReconciles(desired, observed);
  }

  @Test
  public void nullDesiredMessageAndUrlArePatchWildcards() throws Exception {
    CheckInput desired = terminalInput(new Timestamp(1_000L));
    desired.message = null;
    desired.url = null;
    CheckInfo observed = matchingInfo(desired, desired.finished);
    observed.message = "a server-owned message";
    observed.url = "https://server.example/result";

    assertReconciles(desired, observed);
  }

  @Test
  public void nonNullDesiredMessageAndUrlMustMatchAfterCanonicalization() throws Exception {
    CheckInput desired = terminalInput(new Timestamp(1_000L));
    CheckInfo differentMessage = matchingInfo(desired, desired.finished);
    differentMessage.message = "different";
    assertDoesNotReconcile(desired, differentMessage);

    CheckInfo differentUrl = matchingInfo(desired, desired.finished);
    differentUrl.url = "https://jenkins.example/job/2";
    assertDoesNotReconcile(desired, differentUrl);
  }

  @Test
  public void nullDesiredStartedIsPatchWildcard() throws Exception {
    CheckInput desired = terminalInput(new Timestamp(1_000L));
    desired.started = null;
    CheckInfo observed = matchingInfo(desired, desired.finished);
    observed.started = new Timestamp(9_000L);

    assertReconciles(desired, observed);
  }

  @Test
  public void nonNullStartedMatchesAtMillisecondPrecision() throws Exception {
    CheckInput desired = terminalInput(new Timestamp(1_000L));
    desired.started = Timestamp.from(Instant.parse("2019-01-31T09:59:32.126789123Z"));
    CheckInfo observed = matchingInfo(desired, desired.finished);
    observed.started = Timestamp.from(Instant.parse("2019-01-31T09:59:32.126000001Z"));

    assertReconciles(desired, observed);

    observed.started = Timestamp.from(Instant.parse("2019-01-31T09:59:32.127Z"));
    assertDoesNotReconcile(desired, observed);
  }

  @Test
  public void epochStartedMeansExpectedNull() throws Exception {
    CheckInput desired = terminalInput(new Timestamp(1_000L));
    desired.started = new Timestamp(0L);
    CheckInfo observed = matchingInfo(desired, desired.finished);
    observed.started = null;

    assertReconciles(desired, observed);

    observed.started = new Timestamp(0L);
    assertDoesNotReconcile(desired, observed);
  }

  @Test
  public void finishedMatchesAtMillisecondPrecision() throws Exception {
    CheckInput desired =
        terminalInput(Timestamp.from(Instant.parse("2019-01-31T09:59:32.126789123Z")));
    CheckInfo observed =
        matchingInfo(desired, Timestamp.from(Instant.parse("2019-01-31T09:59:32.126000001Z")));

    assertReconciles(desired, observed);

    observed.finished = Timestamp.from(Instant.parse("2019-01-31T09:59:32.127Z"));
    assertDoesNotReconcile(desired, observed);
  }

  @Test
  public void okIsSuccessfulWithoutReadback() throws Exception {
    assertSuccessfulStatusReturnsBodyWithoutReadback(200);
  }

  @Test
  public void createdIsSuccessfulWithoutReadback() throws Exception {
    assertSuccessfulStatusReturnsBodyWithoutReadback(201);
  }

  @Test
  public void noContentIsAmbiguousAndReconciled() throws Exception {
    assertAmbiguousHttpStatusReconciled(204);
  }

  @Test
  public void acceptedIsAmbiguousAndReconciled() throws Exception {
    assertAmbiguousHttpStatusReconciled(202);
  }

  @Test
  public void requestTimeoutIsAmbiguousAndReconciled() throws Exception {
    assertAmbiguousHttpStatusReconciled(408);
  }

  @Test
  public void gatewayTimeoutIsAmbiguousAndReconciled() throws Exception {
    assertAmbiguousHttpStatusReconciled(504);
  }

  @Test
  public void otherTwoHundredStatusIsAmbiguousAndReconciled() throws Exception {
    assertAmbiguousHttpStatusReconciled(203);
  }

  @Test
  public void bodylessBadRequestIsDefinitiveAndNotReadBack() throws Exception {
    assertDefinitiveStatusIsNotReadBack(400, null);
  }

  @Test
  public void malformedSuccessfulResponseIsAmbiguousAndReconciled() throws Exception {
    CheckInput desired = terminalInput(new Timestamp(1_000L));
    CheckInfo observed = matchingInfo(desired, desired.finished);
    expectPost(200, "{not-json");
    expectReadback(200, responseBody(observed));

    try (CloseableHttpClient client = HttpClientBuilder.create().build()) {
      assertEquals(observed, newChecks(client, 0L).updateTerminal(desired));
    }

    verifyOnePostAndGetCount(1);
  }

  @Test
  public void bodylessOkResponseIsAmbiguousAndReconciled() throws Exception {
    CheckInput desired = terminalInput(new Timestamp(1_000L));
    CheckInfo observed = matchingInfo(desired, desired.finished);
    expectPost(200, null);
    expectReadback(200, responseBody(observed));

    try (CloseableHttpClient client = HttpClientBuilder.create().build()) {
      assertEquals(observed, newChecks(client, 0L).updateTerminal(desired));
    }

    verifyOnePostAndGetCount(1);
  }

  @Test
  public void incompleteSuccessfulResponseIsAmbiguousAndReconciled() throws Exception {
    CheckInput desired = terminalInput(new Timestamp(1_000L));
    CheckInfo observed = matchingInfo(desired, desired.finished);
    expectPost(200, "{}");
    expectReadback(200, responseBody(observed));

    try (CloseableHttpClient client = HttpClientBuilder.create().build()) {
      assertEquals(observed, newChecks(client, 0L).updateTerminal(desired));
    }

    verifyOnePostAndGetCount(1);
  }

  @Test
  public void nanosecondWidthReadbackMatchesMillisecondRequest() throws Exception {
    Timestamp frozenFinished = Timestamp.from(Instant.parse("2019-01-31T09:59:32.126789123Z"));
    Timestamp wireFinished = Timestamp.from(Instant.parse("2019-01-31T09:59:32.126Z"));
    CheckInput desired = terminalInput(frozenFinished);
    expectPost(504, "ambiguous");
    expectReadback(
        200,
        ")]}'\n"
            + "{\"checker_uuid\":\"checker:uuid\","
            + "\"state\":\"SUCCESSFUL\",\"message\":\"done\","
            + "\"url\":\"https://jenkins.example/job/1\","
            + "\"finished\":\"2019-01-31 09:59:32.126000000\"}");

    try (CloseableHttpClient client = HttpClientBuilder.create().build()) {
      CheckInfo observed = newChecks(client, 0L).updateTerminal(desired);
      assertEquals(wireFinished, observed.finished);
    }

    verifyOnePostAndGetCount(1);
  }

  private void assertSuccessfulStatusReturnsBodyWithoutReadback(int statusCode) throws Exception {
    CheckInput desired = terminalInput(new Timestamp(1_000L));
    CheckInfo returned = matchingInfo(desired, desired.finished);
    expectPost(statusCode, responseBody(returned));

    try (CloseableHttpClient client = HttpClientBuilder.create().build()) {
      assertEquals(returned, newChecks(client, 0L).updateTerminal(desired));
    }

    verifyOnePostAndGetCount(0);
  }

  private void assertAmbiguousHttpStatusReconciled(int statusCode) throws Exception {
    CheckInput desired = terminalInput(new Timestamp(1_000L));
    CheckInfo observed = matchingInfo(desired, desired.finished);
    expectPost(statusCode, "ambiguous response body");
    expectReadback(200, responseBody(observed));

    try (CloseableHttpClient client = HttpClientBuilder.create().build()) {
      assertEquals(observed, newChecks(client, 0L).updateTerminal(desired));
    }

    verifyOnePostAndGetCount(1);
  }

  private void assertDefinitiveStatusIsNotReadBack(int statusCode, String body) throws Exception {
    CheckInput desired = terminalInput(new Timestamp(1_000L));
    expectPost(statusCode, body);

    try (CloseableHttpClient client = HttpClientBuilder.create().build()) {
      try {
        newChecks(client, 0L).updateTerminal(desired);
        fail("Expected definitive failure");
      } catch (RestApiException expected) {
        assertTrue(exceptionText(expected).contains(String.valueOf(statusCode)));
      }
    }

    verifyOnePostAndGetCount(0);
  }

  private static void assertRejectedBeforePost(FakeChecks checks, CheckInput desired)
      throws Exception {
    try {
      checks.updateTerminal(desired);
      fail("Expected terminal input validation to fail");
    } catch (IllegalArgumentException expected) {
      assertEquals(0, checks.updateCount);
      assertEquals(0, checks.readCount);
    }
  }

  private static void assertReconciles(CheckInput desired, CheckInfo observed) throws Exception {
    FakeChecks checks = new FakeChecks(0L);
    checks.reads.add(observed);
    checks.updateFailure = ambiguousFailure();

    assertSame(observed, checks.updateTerminal(desired));
    assertEquals(1, checks.updateCount);
    assertEquals(1, checks.readCount);
  }

  private static void assertDoesNotReconcile(CheckInput desired, CheckInfo observed)
      throws Exception {
    FakeChecks checks = new FakeChecks(0L);
    checks.reads.add(observed);
    checks.updateFailure = ambiguousFailure();

    expectUnreconciled(checks, desired);
    assertEquals(1, checks.updateCount);
    assertEquals(1, checks.readCount);
  }

  private static RestApiException expectUnreconciled(FakeChecks checks, CheckInput desired)
      throws Exception {
    try {
      checks.updateTerminal(desired);
      fail("Expected unreconciled ambiguous update");
      return null;
    } catch (RestApiException expected) {
      return expected;
    }
  }

  private Checks newChecks(CloseableHttpClient client, long... delays) throws Exception {
    Checks checks = new Checks(checksBaseUrl(), client, false, delays);
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

  private void expectPost(int statusCode, String body) {
    HttpResponse response = HttpResponse.response().withStatusCode(statusCode);
    if (body != null) {
      response.withBody(body);
    }
    g.getClient()
        .when(HttpRequest.request(checksCollectionPath()).withMethod("POST"))
        .respond(response);
  }

  private void expectReadback(int statusCode, String body) {
    HttpResponse response = HttpResponse.response().withStatusCode(statusCode);
    if (body != null) {
      response.withBody(body);
    }
    g.getClient()
        .when(HttpRequest.request(checkReadbackPath()).withMethod("GET"))
        .respond(response);
  }

  private void verifyOnePostAndGetCount(int getCount) {
    g.getClient()
        .verify(
            HttpRequest.request(checksCollectionPath()).withMethod("POST"),
            VerificationTimes.once());
    verifyGetCount(getCount);
  }

  private void verifyGetCount(int count) {
    g.getClient()
        .verify(
            HttpRequest.request(checkReadbackPath()).withMethod("GET"),
            VerificationTimes.exactly(count));
  }

  private static String checksCollectionPath() {
    return String.format("/changes/%d/revisions/%d/checks/", CHANGE_NUMBER, PATCH_SET_NUMBER);
  }

  private static String checkReadbackPath() {
    return checksCollectionPath() + "checker:uuid";
  }

  private static String responseBody(CheckInfo info) {
    return ")]}'\n"
        + AbstractEndpoint.JsonBodyParser.createRequestBody(
            info, new TypeToken<CheckInfo>() {}.getType());
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
    info.started = desired.started;
    info.finished = finished;
    return info;
  }

  private static Checks.AmbiguousCheckUpdateException ambiguousFailure() {
    return new Checks.AmbiguousCheckUpdateException("response lost", new IOException());
  }

  private static String exceptionText(Throwable failure) {
    StringBuilder text = new StringBuilder();
    appendExceptionText(text, failure);
    return text.toString();
  }

  private static void appendExceptionText(StringBuilder text, Throwable failure) {
    if (failure == null) {
      return;
    }
    text.append(failure.getClass().getName()).append(':').append(failure.getMessage()).append('\n');
    for (Throwable suppressed : failure.getSuppressed()) {
      appendExceptionText(text, suppressed);
    }
    appendExceptionText(text, failure.getCause());
  }

  private static class FakeChecks extends Checks {
    private final Queue<Object> reads = new LinkedList<>();
    private int readCount;
    private int updateCount;
    private boolean interruptAfterRead;
    private boolean interruptAfterUpdate;
    private CheckInput lastUpdate;
    private CheckInfo updateResult;
    private RestApiException updateFailure;

    FakeChecks(long... delays) throws Exception {
      super(new URIish("https://gerrit.example"), null, false, delays);
    }

    @Override
    CheckInfo getIfPresent(String checkerUuid) throws RestApiException {
      readCount++;
      Object result = reads.remove();
      if (result instanceof RestApiException) {
        throw (RestApiException) result;
      }
      if (interruptAfterRead) {
        Thread.currentThread().interrupt();
      }
      return (CheckInfo) result;
    }

    @Override
    CheckInfo performTerminalUpdate(CheckInput input) throws RestApiException {
      updateCount++;
      lastUpdate = input;
      if (interruptAfterUpdate) {
        Thread.currentThread().interrupt();
      }
      if (updateFailure != null) {
        throw updateFailure;
      }
      return updateResult;
    }
  }
}

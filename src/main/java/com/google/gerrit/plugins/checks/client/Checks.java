// Copyright (C) 2019 The Android Open Source Project
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

import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.plugins.checks.api.CheckInfo;
import com.google.gerrit.plugins.checks.api.CheckInput;
import com.google.gerrit.plugins.checks.api.RerunInput;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.apache.http.HttpStatus;
import org.apache.http.ParseException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.eclipse.jgit.transport.URIish;

public class Checks extends AbstractEndpoint {
  private static final long[] DEFAULT_TERMINAL_READBACK_DELAYS_MILLIS =
      new long[] {0L, 250L, 1_000L, 4_000L, 10_000L};

  private int changeNumber;
  private int patchSetNumber;
  private final long[] terminalReadbackDelaysMillis;

  public Checks(URIish gerritBaseUrl, CloseableHttpClient client, boolean isAuthenticated)
      throws URISyntaxException {
    this(gerritBaseUrl, client, isAuthenticated, DEFAULT_TERMINAL_READBACK_DELAYS_MILLIS);
  }

  Checks(
      URIish gerritBaseUrl,
      CloseableHttpClient client,
      boolean isAuthenticated,
      long... terminalReadbackDelaysMillis)
      throws URISyntaxException {
    super(gerritBaseUrl, client, isAuthenticated);
    if (terminalReadbackDelaysMillis == null || terminalReadbackDelaysMillis.length == 0) {
      throw new IllegalArgumentException("Terminal readback delay schedule must not be empty");
    }
    for (long delayMillis : terminalReadbackDelaysMillis) {
      if (delayMillis < 0) {
        throw new IllegalArgumentException("Terminal readback delays must not be negative");
      }
    }
    this.terminalReadbackDelaysMillis = terminalReadbackDelaysMillis.clone();
  }

  public Checks change(int changeNumber) {
    this.changeNumber = changeNumber;
    return this;
  }

  public Checks patchSet(int patchSetNumber) {
    this.patchSetNumber = patchSetNumber;
    return this;
  }

  public CheckInfo create(CheckInput input) throws RestApiException {
    try {
      return performCreateOrUpdate(input);
    } catch (Exception e) {
      throw new RestApiException("Could not create check", e);
    }
  }

  public CheckInfo get(String checkerUuid) throws RestApiException {
    try {
      HttpGet request = new HttpGet(buildRequestUrl(checkerUuid));
      try (CloseableHttpResponse response = client.execute(request)) {
        if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
          return JsonBodyParser.parseResponse(
              EntityUtils.toString(response.getEntity()), new TypeToken<CheckInfo>() {}.getType());
        }
        throw new RestApiException(
            String.format(
                "Request failed with status: %d", response.getStatusLine().getStatusCode()));
      }
    } catch (Exception e) {
      throw new RestApiException("Failed to get check info: ", e);
    }
  }

  /** Returns the check, or {@code null} when Gerrit reports that it does not exist. */
  CheckInfo getIfPresent(String checkerUuid) throws RestApiException {
    try {
      HttpGet request = new HttpGet(buildRequestUrl(checkerUuid));
      try (CloseableHttpResponse response = client.execute(request)) {
        int statusCode = response.getStatusLine().getStatusCode();
        if (statusCode == HttpStatus.SC_OK) {
          return JsonBodyParser.parseResponse(
              EntityUtils.toString(response.getEntity()), new TypeToken<CheckInfo>() {}.getType());
        }
        if (statusCode == HttpStatus.SC_NOT_FOUND) {
          return null;
        }
        throw new RestApiException(String.format("Request failed with status: %d", statusCode));
      }
    } catch (RestApiException e) {
      throw e;
    } catch (Exception e) {
      throw new RestApiException("Failed to get check info: ", e);
    }
  }

  public List<CheckInfo> list() throws RestApiException {
    try {
      HttpGet request = new HttpGet(buildRequestUrl());
      try (CloseableHttpResponse response = client.execute(request)) {
        if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
          return JsonBodyParser.parseResponse(
              EntityUtils.toString(response.getEntity()),
              new TypeToken<List<CheckInfo>>() {}.getType());
        }
        throw new RestApiException(
            String.format(
                "Request failed with status: %d", response.getStatusLine().getStatusCode()));
      }
    } catch (Exception e) {
      throw new RestApiException("Failed to list checks: ", e);
    }
  }

  public CheckInfo rerun(String checkerUuid) throws RestApiException {
    return rerun(checkerUuid, new RerunInput());
  }

  public CheckInfo rerun(String checkerUuid, RerunInput input) throws RestApiException {
    try {
      HttpPost request = new HttpPost(buildRequestUrl(checkerUuid + "/rerun"));
      String inputString =
          JsonBodyParser.createRequestBody(input, new TypeToken<RerunInput>() {}.getType());
      request.setEntity(new StringEntity(inputString));
      request.setHeader("Content-type", "application/json");
      try (CloseableHttpResponse response = client.execute(request)) {
        if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
          return JsonBodyParser.parseResponse(
              EntityUtils.toString(response.getEntity()), new TypeToken<CheckInfo>() {}.getType());
        }
        throw new RestApiException(
            String.format(
                "Request failed with status: %d", response.getStatusLine().getStatusCode()));
      }
    } catch (Exception e) {
      throw new RestApiException("Could not rerun check", e);
    }
  }

  public CheckInfo update(CheckInput input) throws RestApiException {
    try {
      return performCreateOrUpdate(input);
    } catch (Exception e) {
      throw new RestApiException("Could not update check", e);
    }
  }

  /**
   * Publishes one terminal check update with bounded read-after reconciliation.
   *
   * <p>The Checks endpoint has no compare-and-set or idempotency-key contract. Consequently this
   * method never issues another POST after an ambiguous outcome and does not provide generation
   * fencing across Jenkins controllers or other writers. The caller must supply one frozen payload,
   * including its finished timestamp, for the complete logical call.
   */
  public CheckInfo updateTerminal(CheckInput input) throws RestApiException, InterruptedException {
    if (input == null || input.state == null || input.state.isInProgress()) {
      throw new IllegalArgumentException("updateTerminal requires a terminal check state");
    }
    if (input.checkerUuid == null || input.checkerUuid.trim().isEmpty()) {
      throw new IllegalArgumentException("updateTerminal requires a nonempty checker UUID");
    }
    if (input.finished == null || input.finished.getTime() == 0L) {
      throw new IllegalArgumentException(
          "updateTerminal requires a frozen, non-epoch finished timestamp");
    }

    throwIfInterrupted();
    try {
      return performTerminalUpdate(input);
    } catch (AmbiguousCheckUpdateException ambiguousFailure) {
      return reconcileAmbiguousTerminalUpdate(input, ambiguousFailure);
    }
  }

  private CheckInfo reconcileAmbiguousTerminalUpdate(
      CheckInput desired, AmbiguousCheckUpdateException ambiguousFailure)
      throws RestApiException, InterruptedException {
    List<RestApiException> readFailures = new ArrayList<>();
    try {
      for (long delayMillis : terminalReadbackDelaysMillis) {
        throwIfInterrupted();
        if (delayMillis > 0L) {
          Thread.sleep(delayMillis);
        }
        throwIfInterrupted();

        try {
          CheckInfo observed = getIfPresent(desired.checkerUuid);
          throwIfInterrupted();
          if (matchesAppliedTerminalUpdate(observed, desired)) {
            return observed;
          }
        } catch (RestApiException readFailure) {
          readFailures.add(readFailure);
          throwIfInterrupted();
        }
      }
    } catch (InterruptedException interrupted) {
      interrupted.initCause(ambiguousFailure);
      for (RestApiException readFailure : readFailures) {
        interrupted.addSuppressed(readFailure);
      }
      throw interrupted;
    }

    RestApiException unreconciled =
        new RestApiException(
            "Terminal check POST was ambiguous and bounded readback did not reconcile it",
            ambiguousFailure);
    for (RestApiException readFailure : readFailures) {
      unreconciled.addSuppressed(readFailure);
    }
    throw unreconciled;
  }

  private static void throwIfInterrupted() throws InterruptedException {
    if (Thread.currentThread().isInterrupted()) {
      throw new InterruptedException("Interrupted during terminal check reconciliation");
    }
  }

  private CheckInfo performCreateOrUpdate(CheckInput input)
      throws RestApiException, URISyntaxException, ParseException, IOException {
    HttpPost request = new HttpPost(buildRequestUrl());
    String inputString =
        JsonBodyParser.createRequestBody(input, new TypeToken<CheckInput>() {}.getType());
    request.setEntity(new StringEntity(inputString));
    request.setHeader("Content-type", "application/json");

    try (CloseableHttpResponse response = client.execute(request)) {
      if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
        return JsonBodyParser.parseResponse(
            EntityUtils.toString(response.getEntity()), new TypeToken<CheckInfo>() {}.getType());
      }
      throw new RestApiException(
          String.format(
              "POST %s with body '%s' returned status %d (%s)",
              request.getURI(),
              inputString,
              response.getStatusLine().getStatusCode(),
              EntityUtils.toString(response.getEntity())));
    }
  }

  CheckInfo performTerminalUpdate(CheckInput input) throws RestApiException {
    final HttpPost request;
    try {
      request = new HttpPost(buildRequestUrl());
      String inputString =
          JsonBodyParser.createRequestBody(input, new TypeToken<CheckInput>() {}.getType());
      request.setEntity(new StringEntity(inputString));
      request.setHeader("Content-type", "application/json");
    } catch (Exception e) {
      throw new RestApiException("Could not prepare terminal check POST", e);
    }

    try (CloseableHttpResponse response = client.execute(request)) {
      int statusCode = response.getStatusLine().getStatusCode();
      if (statusCode == HttpStatus.SC_OK || statusCode == HttpStatus.SC_CREATED) {
        try {
          if (response.getEntity() == null) {
            throw new IllegalStateException("Successful check POST response had no entity");
          }
          CheckInfo parsed =
              JsonBodyParser.parseResponse(
                  EntityUtils.toString(response.getEntity()),
                  new TypeToken<CheckInfo>() {}.getType());
          if (!matchesAppliedTerminalUpdate(parsed, input)) {
            throw new IllegalStateException(
                "Successful check POST response did not confirm the terminal update");
          }
          return parsed;
        } catch (RuntimeException | IOException e) {
          throw new AmbiguousCheckUpdateException(
              "Check POST succeeded but its response could not be parsed", e);
        }
      }
      if (isAmbiguousTerminalStatus(statusCode)) {
        throw new AmbiguousCheckUpdateException(
            String.format("Check POST returned ambiguous status %d", statusCode), null);
      }
      throw new RestApiException(String.format("Check POST returned status %d", statusCode));
    } catch (RestApiException e) {
      throw e;
    } catch (IOException | RuntimeException e) {
      throw new AmbiguousCheckUpdateException(
          "Check POST transport failed without a definitive server result", e);
    }
  }

  private static boolean matchesAppliedTerminalUpdate(CheckInfo existing, CheckInput desired) {
    // Requests use milliseconds; Gerrit pads that precision to nine fractional response digits.
    return existing != null
        && Objects.equals(existing.checkerUuid, desired.checkerUuid)
        && existing.state == desired.state
        && matchesPatchedString(existing.message, desired.message)
        && matchesPatchedString(existing.url, desired.url)
        && matchesStarted(existing.started, desired.started)
        && existing.finished != null
        && existing.finished.getTime() == desired.finished.getTime();
  }

  private static boolean matchesPatchedString(String observed, String desired) {
    return desired == null || Objects.equals(canonicalize(observed), canonicalize(desired));
  }

  private static String canonicalize(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  private static boolean matchesStarted(Timestamp observed, Timestamp desired) {
    if (desired == null) {
      return true;
    }
    if (desired.getTime() == 0L) {
      return observed == null;
    }
    return observed != null && observed.getTime() == desired.getTime();
  }

  private static boolean isAmbiguousTerminalStatus(int statusCode) {
    return (statusCode >= 200 && statusCode < 300)
        || statusCode == HttpStatus.SC_REQUEST_TIMEOUT
        || (statusCode >= HttpStatus.SC_INTERNAL_SERVER_ERROR && statusCode < 600);
  }

  /** Indicates that Gerrit may have applied a POST before the client observed a failure. */
  static class AmbiguousCheckUpdateException extends RestApiException {
    private static final long serialVersionUID = 1L;

    AmbiguousCheckUpdateException(String message, Throwable cause) {
      super(message, cause);
    }
  }

  private URI buildRequestUrl() throws URISyntaxException {
    return buildRequestUrl("");
  }

  private URI buildRequestUrl(String suffixPath) throws URISyntaxException {
    return uriBuilder
        .setPath(
            String.format(
                "%schanges/%d/revisions/%d/checks/%s",
                getPrefix(), changeNumber, patchSetNumber, suffixPath))
        .build();
  }
}

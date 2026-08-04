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
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.eclipse.jgit.transport.URIish;

public class Checks extends AbstractEndpoint {
  private static final Logger LOGGER = Logger.getLogger(Checks.class.getName());

  private int changeNumber;
  private int patchSetNumber;

  public Checks(URIish gerritBaseUrl, CloseableHttpClient client, boolean isAuthenticated)
      throws URISyntaxException {
    super(gerritBaseUrl, client, isAuthenticated);
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
    return get(checkerUuid, false);
  }

  /** Returns the check, or {@code null} when Gerrit reports that it does not exist. */
  public CheckInfo getIfPresent(String checkerUuid) throws RestApiException {
    return get(checkerUuid, true);
  }

  private CheckInfo get(String checkerUuid, boolean allowMissing) throws RestApiException {
    try {
      HttpGet request = new HttpGet(buildRequestUrl(checkerUuid));
      try (CloseableHttpResponse response = client.execute(request)) {
        int statusCode = response.getStatusLine().getStatusCode();
        if (statusCode == HttpStatus.SC_OK) {
          return JsonBodyParser.parseResponse(
              EntityUtils.toString(response.getEntity()), new TypeToken<CheckInfo>() {}.getType());
        }
        if (allowMissing && statusCode == HttpStatus.SC_NOT_FOUND) {
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
    } catch (AmbiguousCheckUpdateException e) {
      throw e;
    } catch (Exception e) {
      throw new RestApiException("Could not update check", e);
    }
  }

  /**
   * Publishes one terminal check update with read-before and read-after reconciliation.
   *
   * <p>The Checks endpoint has no compare-and-set or idempotency-key contract. Consequently this
   * method never issues another POST after an ambiguous outcome and does not provide generation
   * fencing across Jenkins controllers or other writers. The caller must supply one frozen payload,
   * including its finished timestamp, for the complete logical call.
   */
  public CheckInfo updateTerminal(CheckInput input) throws RestApiException {
    if (input == null || input.state == null || input.state.isInProgress()) {
      throw new IllegalArgumentException("updateTerminal requires a terminal check state");
    }
    if (input.finished == null) {
      throw new IllegalArgumentException("updateTerminal requires a frozen finished timestamp");
    }

    try {
      CheckInfo existing = getIfPresent(input.checkerUuid);
      if (matchesForPreWriteNoOp(existing, input)) {
        return existing;
      }
    } catch (RestApiException e) {
      // A failed optimization must not prevent the one intended POST.
      LOGGER.log(Level.FINE, "Could not read check before terminal update", e);
    }

    try {
      return update(input);
    } catch (AmbiguousCheckUpdateException ambiguousFailure) {
      final CheckInfo observed;
      try {
        observed = getIfPresent(input.checkerUuid);
      } catch (RestApiException readFailure) {
        RestApiException unreconciled =
            new RestApiException(
                "Terminal check POST failed ambiguously and readback could not reconcile it",
                ambiguousFailure);
        unreconciled.addSuppressed(readFailure);
        throw unreconciled;
      }
      if (matchesAfterAmbiguousWrite(observed, input)) {
        return observed;
      }
      throw new RestApiException(
          "Terminal check POST failed ambiguously and readback did not match the frozen payload",
          ambiguousFailure);
    }
  }

  private CheckInfo performCreateOrUpdate(CheckInput input) throws RestApiException {
    final HttpPost request;
    final String inputString;
    try {
      request = new HttpPost(buildRequestUrl());
      inputString =
          JsonBodyParser.createRequestBody(input, new TypeToken<CheckInput>() {}.getType());
      request.setEntity(new StringEntity(inputString, ContentType.APPLICATION_JSON));
    } catch (Exception e) {
      throw new RestApiException("Could not prepare check POST", e);
    }

    try (CloseableHttpResponse response = client.execute(request)) {
      int statusCode = response.getStatusLine().getStatusCode();
      if (isSuccessful(statusCode)) {
        if (response.getEntity() == null) {
          return null;
        }
        try {
          return JsonBodyParser.parseResponse(
              EntityUtils.toString(response.getEntity()), new TypeToken<CheckInfo>() {}.getType());
        } catch (RuntimeException | IOException e) {
          throw new AmbiguousCheckUpdateException(
              "Check POST succeeded but its response could not be parsed", e);
        }
      }
      if (isAmbiguousStatus(statusCode)) {
        throw new AmbiguousCheckUpdateException(
            String.format(
                "Check POST returned ambiguous status %d (%s)",
                statusCode, EntityUtils.toString(response.getEntity())),
            null);
      }
      throw new RestApiException(
          String.format(
              "POST %s with body '%s' returned status %d (%s)",
              request.getURI(),
              inputString,
              statusCode,
              EntityUtils.toString(response.getEntity())));
    } catch (RestApiException e) {
      throw e;
    } catch (IOException | RuntimeException e) {
      throw new AmbiguousCheckUpdateException(
          "Check POST transport failed without a definitive server result", e);
    }
  }

  private static boolean matchesForPreWriteNoOp(CheckInfo existing, CheckInput desired) {
    return matchesAfterAmbiguousWrite(existing, desired);
  }

  private static boolean matchesAfterAmbiguousWrite(CheckInfo existing, CheckInput desired) {
    // Requests use milliseconds; Gerrit's response may express the same instant with 3-9 digits.
    return matchesStateMessageAndUrl(existing, desired)
        && existing.finished != null
        && existing.finished.getTime() == desired.finished.getTime();
  }

  private static boolean matchesStateMessageAndUrl(CheckInfo existing, CheckInput desired) {
    return existing != null
        && Objects.equals(existing.checkerUuid, desired.checkerUuid)
        && Objects.equals(existing.state, desired.state)
        && Objects.equals(existing.message, desired.message)
        && Objects.equals(existing.url, desired.url);
  }

  private static boolean isSuccessful(int statusCode) {
    return statusCode >= HttpStatus.SC_OK && statusCode < HttpStatus.SC_MULTIPLE_CHOICES;
  }

  private static boolean isAmbiguousStatus(int statusCode) {
    return statusCode == HttpStatus.SC_REQUEST_TIMEOUT
        || (statusCode >= HttpStatus.SC_INTERNAL_SERVER_ERROR && statusCode < 600);
  }

  /** Indicates that Gerrit may have applied a POST before the client observed a failure. */
  public static class AmbiguousCheckUpdateException extends RestApiException {
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

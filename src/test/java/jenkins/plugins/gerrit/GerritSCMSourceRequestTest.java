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

package jenkins.plugins.gerrit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.plugins.checks.api.PendingChecksInfo;
import com.google.gerrit.plugins.checks.client.GerritChecksApi;
import com.google.gerrit.plugins.checks.client.PendingChecks;
import hudson.model.TaskListener;
import hudson.util.StreamTaskListener;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import jenkins.plugins.gerrit.traits.FilterChecksTrait.ChecksQueryOperator;
import jenkins.scm.api.SCMHeadObserver;
import org.junit.Test;

public class GerritSCMSourceRequestTest {

  @Test
  public void normalizesNullTaskListener() {
    TaskListener listener = mock(TaskListener.class);

    assertSame(listener, GerritSCMSourceRequest.normalizeListener(listener));
    assertSame(TaskListener.NULL, GerritSCMSourceRequest.normalizeListener(null));
  }

  @Test
  public void pendingCheckFailureIncludesStackTraceInBuildLog() {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    IOException failure = new IOException("query failed");

    GerritSCMSourceRequest.logPendingChecksFailure(new StreamTaskListener(output), failure);

    String log = new String(output.toByteArray(), StandardCharsets.UTF_8);
    assertTrue(log, log.contains("Unable to query for pending checks: java.io.IOException"));
    assertTrue(log, log.contains("query failed"));
    assertTrue(log, log.contains("at " + getClass().getName()));
  }

  @Test
  public void pendingCheckScanClosesOwnedChecksClient() throws Exception {
    GerritChecksApi checksApi = mock(GerritChecksApi.class);
    PendingChecks pendingChecks = mock(PendingChecks.class);
    when(checksApi.pendingChecks()).thenReturn(pendingChecks);
    when(pendingChecks.checker("checker:uuid")).thenReturn(pendingChecks);
    when(pendingChecks.list()).thenReturn(Collections.emptyList());

    GerritSCMSourceContext context =
        new GerritSCMSourceContext(null, SCMHeadObserver.none())
            .wantFilterForPendingChecks(true)
            .withChecksQueryOperator(ChecksQueryOperator.ID)
            .withChecksQueryString("checker:uuid");

    GerritSCMSourceRequest.queryPendingChecks(checksApi, context);

    verify(checksApi).close();
  }

  @Test
  public void closeFailureDoesNotDiscardSuccessfulPendingCheckQuery() throws Exception {
    GerritChecksApi checksApi = mock(GerritChecksApi.class);
    PendingChecks pendingChecks = mock(PendingChecks.class);
    PendingChecksInfo pendingCheck = mock(PendingChecksInfo.class);
    List<PendingChecksInfo> expected = Arrays.asList(pendingCheck);
    when(checksApi.pendingChecks()).thenReturn(pendingChecks);
    when(pendingChecks.checker("checker:uuid")).thenReturn(pendingChecks);
    when(pendingChecks.list()).thenReturn(expected);
    doThrow(new IOException("close failed")).when(checksApi).close();

    GerritSCMSourceContext context =
        new GerritSCMSourceContext(null, SCMHeadObserver.none())
            .wantFilterForPendingChecks(true)
            .withChecksQueryOperator(ChecksQueryOperator.ID)
            .withChecksQueryString("checker:uuid");

    List<PendingChecksInfo> actual = GerritSCMSourceRequest.queryPendingChecks(checksApi, context);

    assertSame(expected, actual);
    verify(checksApi).close();
  }

  @Test
  public void closeFailureIsSuppressedByPendingCheckQueryFailure() throws Exception {
    GerritChecksApi checksApi = mock(GerritChecksApi.class);
    PendingChecks pendingChecks = mock(PendingChecks.class);
    RestApiException queryFailure = new RestApiException("query failed");
    IOException closeFailure = new IOException("close failed");
    when(checksApi.pendingChecks()).thenReturn(pendingChecks);
    when(pendingChecks.checker("checker:uuid")).thenReturn(pendingChecks);
    when(pendingChecks.list()).thenThrow(queryFailure);
    doThrow(closeFailure).when(checksApi).close();

    GerritSCMSourceContext context =
        new GerritSCMSourceContext(null, SCMHeadObserver.none())
            .wantFilterForPendingChecks(true)
            .withChecksQueryOperator(ChecksQueryOperator.ID)
            .withChecksQueryString("checker:uuid");

    try {
      GerritSCMSourceRequest.queryPendingChecks(checksApi, context);
      fail("Expected the pending-check query failure");
    } catch (RestApiException actual) {
      assertSame(queryFailure, actual);
      assertEquals(1, actual.getSuppressed().length);
      assertSame(closeFailure, actual.getSuppressed()[0]);
    }
    verify(checksApi).close();
  }
}

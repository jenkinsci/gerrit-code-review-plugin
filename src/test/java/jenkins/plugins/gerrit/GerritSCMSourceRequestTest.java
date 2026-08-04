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

import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.gerrit.plugins.checks.api.PendingChecksInfo;
import com.google.gerrit.plugins.checks.client.GerritChecksApi;
import com.google.gerrit.plugins.checks.client.PendingChecks;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import jenkins.plugins.gerrit.traits.FilterChecksTrait.ChecksQueryOperator;
import jenkins.scm.api.SCMHeadObserver;
import org.junit.Test;

public class GerritSCMSourceRequestTest {

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
}

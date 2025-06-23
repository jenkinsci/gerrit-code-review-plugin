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

package jenkins.plugins.gerrit;

import static org.junit.Assert.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

public class GerritChangeTest {

  @Test
  public void testNone() throws Exception {
    GerritChange change = new GerritChange(Collections.emptyMap(), System.out);
    assertFalse(change.valid());
  }

  @Test
  public void testGerritCodeReviewPlugin() throws Exception {
    Integer changeId = 1234;
    Integer revision = 1;
    Map<String, String> env = new HashMap<>();
    env.put("BRANCH_NAME", String.format("%02d/%d/%d", changeId % 100, changeId, revision));
    GerritChange change = new GerritChange(env, System.out);
    assertTrue(change.valid());
    assertEquals(changeId, change.getChangeId());
    assertEquals(revision, change.getRevision());
  }

  @Test
  public void testGerritTriggerPlugin() throws Exception {
    Integer changeId = 1234;
    Integer revision = 1;
    Map<String, String> env = new HashMap<>();
    env.put("GERRIT_CHANGE_NUMBER", changeId.toString());
    env.put("GERRIT_PATCHSET_NUMBER", revision.toString());
    GerritChange change = new GerritChange(env, System.out);
    assertTrue(change.valid());
    assertEquals(changeId, change.getChangeId());
    assertEquals(revision, change.getRevision());
  }
}

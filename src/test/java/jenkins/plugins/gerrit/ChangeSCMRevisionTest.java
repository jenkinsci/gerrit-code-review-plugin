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

package jenkins.plugins.gerrit;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.HashSet;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.Before;
import org.junit.Test;

public class ChangeSCMRevisionTest {

  public HashMap<String, ObjectId> refs;

  @Before
  public void setup() {
    refs = new HashMap<String, ObjectId>();
    refs.put("/refs/changes/11/11111/1", ObjectId.zeroId());
  }

  @Test
  public void testEquivalentReturnsFalseIfPendingChecks() {
    HashSet<String> checkerUuids = new HashSet<String>();
    checkerUuids.add("checker");

    ChangeSCMRevision revision =
        new ChangeSCMRevision(
            new ChangeSCMHead(refs.entrySet().iterator().next(), "master", checkerUuids), "1234");
    assertFalse(revision.equivalent(revision));
  }

  @Test
  public void testEquivalentReturnsFalseIfNoPendingChecks() {
    HashSet<String> checkerUuids = new HashSet<String>();

    ChangeSCMRevision revision =
        new ChangeSCMRevision(
            new ChangeSCMHead(refs.entrySet().iterator().next(), "master", checkerUuids), "1234");
    assertTrue(revision.equivalent(revision));
  }
}

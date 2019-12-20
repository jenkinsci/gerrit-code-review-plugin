// Copyright (C) 2019 GerritForge Ltd
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

import org.junit.Test;

public class ProjectChangesTest {

  @Test
  public void testisVersionBelow215() throws Exception {
    ProjectChanges pc = new ProjectChanges(null);

    assertTrue(pc.isVersionBelow215("1"));
    assertTrue(pc.isVersionBelow215("1.0"));
    assertTrue(pc.isVersionBelow215("1.1.1"));
    assertTrue(pc.isVersionBelow215("<2.8"));
    assertTrue(pc.isVersionBelow215("2"));
    assertTrue(pc.isVersionBelow215("2.0"));
    assertTrue(pc.isVersionBelow215("2.0.19"));
    assertTrue(pc.isVersionBelow215("2.14"));
    assertTrue(pc.isVersionBelow215("2.14.99"));

    assertFalse(pc.isVersionBelow215("2.15"));
    assertFalse(pc.isVersionBelow215("2.15.0"));
    assertFalse(pc.isVersionBelow215("2.15.99"));
    assertFalse(pc.isVersionBelow215("2.16"));
    assertFalse(pc.isVersionBelow215("3"));
    assertFalse(pc.isVersionBelow215("3.0"));
    assertFalse(pc.isVersionBelow215("3.0.0"));
    assertFalse(pc.isVersionBelow215("3.1"));

    assertFalse(pc.isVersionBelow215(null));
    assertFalse(pc.isVersionBelow215(""));
    assertFalse(pc.isVersionBelow215(" "));
    assertFalse(pc.isVersionBelow215("."));
    assertFalse(pc.isVersionBelow215(".."));
    assertFalse(pc.isVersionBelow215("Error"));
  }
}

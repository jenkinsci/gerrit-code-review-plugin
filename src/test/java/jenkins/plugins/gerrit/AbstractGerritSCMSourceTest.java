// Copyright (C) 2025 GerritForge Ltd
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
import static org.junit.Assert.assertNull;

import java.util.List;
import jenkins.plugins.git.GitSCMBuilder;
import jenkins.scm.api.SCMHead;
import jenkins.scm.impl.mock.MockSCMRevision;
import org.junit.Test;

public class AbstractGerritSCMSourceTest {

  @Test
  public void providedRefSpecsAreNotRemoved() {

    SCMHead head = new SCMHead("52/47452/3");
    GitSCMBuilder<?> gitSCMBuilder =
        new GitSCMBuilder<>(head, new MockSCMRevision(head, "foo"), "origin", "secret");
    gitSCMBuilder.withRefSpec("foo");

    MyGerritSCMSource gerritSCMSource = new MyGerritSCMSource();
    gerritSCMSource.decorate(gitSCMBuilder);

    List<String> refSpecs = gitSCMBuilder.refSpecs();
    assertEquals(2, refSpecs.size());
    assertEquals("foo", refSpecs.get(0));
    assertEquals("refs/changes/52/47452/3:refs/remotes/origin/52/47452/3", refSpecs.get(1));
  }

  @Test
  public void shouldGetExcludedFilesFromContext() {
    assertNull(AbstractGerritSCMSource.excludedFile("status:open"));
    assertEquals("foo", AbstractGerritSCMSource.excludedFile("-file:foo"));
  }

  @Test
  public void shouldGetExcludedPathsFromContext() {
    assertNull(AbstractGerritSCMSource.excludedPath("status:open"));
    assertEquals("bar", AbstractGerritSCMSource.excludedPath("-path:bar"));
  }

  private static class MyGerritSCMSource extends AbstractGerritSCMSource {

    @Override
    public String getCredentialsId() {
      throw new UnsupportedOperationException();
    }

    @Override
    public String getRemote() {
      throw new UnsupportedOperationException();
    }
  }
}

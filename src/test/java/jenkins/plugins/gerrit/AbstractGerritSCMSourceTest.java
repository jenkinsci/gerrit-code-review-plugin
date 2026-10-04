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

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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
    assertEquals(Collections.emptySet(), AbstractGerritSCMSource.excludedFiles("status:open"));
    assertEquals(
        Sets.newHashSet("foo", "bar", "^foobar.*$"),
        AbstractGerritSCMSource.excludedFiles("-file:foo -file:\"bar\" -file:^foobar.*$"));
  }

  @Test
  public void shouldGetExcludedPathsFromContext() {
    assertEquals(Collections.emptySet(), AbstractGerritSCMSource.excludedPaths("status:open"));
    assertEquals(
        Sets.newHashSet("foo", "bar", "^foobar.*$"),
        AbstractGerritSCMSource.excludedPaths("-path:foo -path:\"bar\" -path:^foobar.*$"));
  }

  @Test
  public void shouldFilterExcludedFilesAndPathsByName() {
    Stream<String> filesStream =
        Stream.of("path/to/dir/foo", "bar", "path/another/dir/bar", "another/file");
    Stream<String> filteredFiles =
        AbstractGerritSCMSource.filterMatchingExcludedFilesAndPaths(
            Sets.newHashSet("foo"), Sets.newHashSet("bar"), filesStream);
    assertEquals(
        Lists.newArrayList("path/to/dir/foo", "bar"), filteredFiles.collect(Collectors.toList()));
  }

  @Test
  public void shouldFilterExcludedFilesAndPathsByRegex() {
    Stream<String> filesStream = Stream.of("path/to/file", "foobar", "another/text");
    Stream<String> filteredFiles =
        AbstractGerritSCMSource.filterMatchingExcludedFilesAndPaths(
            Sets.newHashSet("^.*/file$"), Sets.newHashSet("^.*bar.*$"), filesStream);
    assertEquals(
        Lists.newArrayList("path/to/file", "foobar"), filteredFiles.collect(Collectors.toList()));
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

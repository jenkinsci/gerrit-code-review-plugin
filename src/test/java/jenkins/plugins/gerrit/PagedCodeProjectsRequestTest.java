// Copyright (C) 2022 Réda Housni Alaoui <reda-alaoui@hey.com>
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

import com.google.gerrit.extensions.common.ProjectInfo;
import java.net.URISyntaxException;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class PagedCodeProjectsRequestTest {

  private static final int CHUNK_MAX_SIZE = 4;

  @Rule public GerritMockServerRule g = new GerritMockServerRule(this);

  private PagedCodeProjectsRequest request;

  @Before
  public void beforeEach() throws URISyntaxException {
    request =
        new PagedCodeProjectsRequest(
            new GerritApiBuilder().gerritApiUrl(g.getUrl()).build(), CHUNK_MAX_SIZE);
  }

  @Test
  public void testSinglePartialChunk() {
    test(CHUNK_MAX_SIZE - 1);
  }

  @Test
  public void testSingleFullChunk() {
    test(CHUNK_MAX_SIZE);
  }

  @Test
  public void testAFullChunkAndAPartialOne() {
    test(CHUNK_MAX_SIZE + 1);
  }

  @Test
  public void test2FullChunksAndAPartialOne() {
    test(2 * CHUNK_MAX_SIZE + 1);
  }

  private void test(int numberOfProjects) {
    for (int i = 1; i <= numberOfProjects; i++) {
      g.addProject(createProject(String.valueOf(i)));
    }

    List<String> collectedProjectIds =
        StreamSupport.stream(request.spliterator(), false)
            .map(projectInfo -> projectInfo.id)
            .collect(Collectors.toList());
    List<String> projectRepositoryIds =
        g.getProjectRepository()
            .stream()
            .map(projectInfo -> projectInfo.id)
            .collect(Collectors.toList());

    assertEquals(projectRepositoryIds, collectedProjectIds);
  }

  private ProjectInfo createProject(String name) {
    ProjectInfo projectInfo = new ProjectInfo();
    projectInfo.id = name;
    return projectInfo;
  }
}

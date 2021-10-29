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

/** @author Réda Housni Alaoui */
public class PagedCodeProjectsRequestTest {

  private static final int CHUNK_MAX_SIZE = 4;

  @Rule public GerritMockServerRule g = new GerritMockServerRule(this);

  private PagedCodeProjectsRequest request;

  @Before
  public void beforeEach() throws URISyntaxException {
    request = new PagedCodeProjectsRequest(new GerritApiBuilder().gerritApiUrl(g.getUrl()).build());
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

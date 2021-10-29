package jenkins.plugins.gerrit;

import static java.util.Optional.ofNullable;
import static org.junit.Assert.assertEquals;

import com.google.gerrit.extensions.common.ProjectInfo;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.apache.commons.lang.StringUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockserver.junit.MockServerRule;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.JsonBody;

/** @author Réda Housni Alaoui */
public class PagedCodeProjectsRequestTest {

  private static final int CHUNK_MAX_SIZE = 4;

  @Rule public MockServerRule g = new MockServerRule(this);

  private List<ProjectInfo> projectRepository;

  private PagedCodeProjectsRequest request;

  @Before
  public void beforeEach() throws URISyntaxException {
    projectRepository = new ArrayList<>();

    request =
        new PagedCodeProjectsRequest(
            new GerritApiBuilder().gerritApiUrl("http://localhost:" + g.getPort()).build());

    g.getClient()
        .when(HttpRequest.request("/projects/").withMethod("GET"))
        .callback(
            httpRequest -> {
              int start =
                  ofNullable(httpRequest.getFirstQueryStringParameter("S"))
                      .filter(StringUtils::isNotBlank)
                      .map(Integer::parseInt)
                      .orElse(0);
              int limit =
                  ofNullable(httpRequest.getFirstQueryStringParameter("n"))
                      .filter(StringUtils::isNotBlank)
                      .map(Integer::parseInt)
                      .orElse(projectRepository.size());

              Map<String, ProjectInfo> projectSlice =
                  projectRepository
                      .subList(start, Math.min(start + limit, projectRepository.size()))
                      .stream()
                      .collect(
                          Collectors.toMap(projectInfo -> projectInfo.id, Function.identity()));
              return HttpResponse.response()
                  .withStatusCode(200)
                  .withBody(JsonBody.json(projectSlice));
            });
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
      projectRepository.add(createProject(String.valueOf(i)));
    }

    List<String> collectedProjectIds =
        StreamSupport.stream(request.spliterator(), false)
            .map(projectInfo -> projectInfo.id)
            .collect(Collectors.toList());
    List<String> projectRepositoryIds =
        projectRepository.stream().map(projectInfo -> projectInfo.id).collect(Collectors.toList());

    assertEquals(projectRepositoryIds, collectedProjectIds);
  }

  private ProjectInfo createProject(String name) {
    ProjectInfo projectInfo = new ProjectInfo();
    projectInfo.id = name;
    return projectInfo;
  }
}

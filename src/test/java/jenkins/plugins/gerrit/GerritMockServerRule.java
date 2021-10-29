package jenkins.plugins.gerrit;

import static java.util.Optional.ofNullable;

import com.google.gerrit.extensions.common.ProjectInfo;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.commons.lang.StringUtils;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.mockserver.junit.MockServerRule;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.JsonBody;

/** @author Réda Housni Alaoui */
public class GerritMockServerRule implements TestRule {

  private final MockServerRule serverRule;
  private final Map<String, ProjectInfo> projectRepository = new LinkedHashMap<>();

  public GerritMockServerRule(Object target) {
    this.serverRule = new MockServerRule(target);
  }

  public String getUrl() {
    return "http://localhost:" + serverRule.getPort();
  }

  public void addProject(ProjectInfo projectInfo) {
    projectRepository.put(projectInfo.id, projectInfo);
  }

  public List<ProjectInfo> getProjectRepository() {
    return new ArrayList<>(projectRepository.values());
  }

  @Override
  public Statement apply(Statement base, Description description) {
    return serverRule.apply(
        new Statement() {
          @Override
          public void evaluate() throws Throwable {
            projectRepository.clear();
            setupExpectations();
            base.evaluate();
          }
        },
        description);
  }

  private void setupExpectations() {
    serverRule
        .getClient()
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

              if (start >= projectRepository.size()) {
                return HttpResponse.response()
                    .withStatusCode(200)
                    .withBody(JsonBody.json(Collections.emptyMap()));
              }

              Map<String, ProjectInfo> projectSlice =
                  new ArrayList<>(projectRepository.values())
                      .subList(start, Math.min(start + limit, projectRepository.size()))
                      .stream()
                      .collect(
                          Collectors.toMap(
                              projectInfo -> projectInfo.id,
                              Function.identity(),
                              (u, v) -> {
                                throw new IllegalStateException(
                                    String.format("Duplicate key %s", u));
                              },
                              LinkedHashMap::new));
              return HttpResponse.response()
                  .withStatusCode(200)
                  .withBody(JsonBody.json(projectSlice));
            });
  }
}

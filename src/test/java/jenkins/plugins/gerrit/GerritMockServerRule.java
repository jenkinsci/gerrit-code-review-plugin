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
        .respond(
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

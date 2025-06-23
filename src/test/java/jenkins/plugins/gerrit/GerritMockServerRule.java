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

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import com.github.tomakehurst.wiremock.extension.ResponseTransformerV2;
import com.github.tomakehurst.wiremock.http.QueryParameter;
import com.github.tomakehurst.wiremock.http.Response;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import com.google.gerrit.extensions.common.ProjectInfo;
import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.commons.lang.StringUtils;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

public class GerritMockServerRule implements TestRule {

  private final WireMockRule wireMock;
  private final Map<String, ProjectInfo> projectRepository = new LinkedHashMap<>();

  public GerritMockServerRule(Object target) {
    this.wireMock =
        new WireMockRule(wireMockConfig().dynamicPort().extensions(new QueryParamTransformer()));
  }

  public String getUrl() {
    return "http://localhost:" + wireMock.port();
  }

  public void addProject(ProjectInfo projectInfo) {
    projectRepository.put(projectInfo.id, projectInfo);
  }

  public List<ProjectInfo> getProjectRepository() {
    return new ArrayList<>(projectRepository.values());
  }

  @Override
  public Statement apply(Statement base, Description description) {
    return wireMock.apply(
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
    wireMock.stubFor(
        get(urlPathEqualTo("/projects/"))
            .willReturn(aResponse().withTransformers("query-param-transformer")));
  }

  public class QueryParamTransformer implements ResponseTransformerV2 {

    @Override
    public String getName() {
      return "query-param-transformer";
    }

    @Override
    public Response transform(Response response, ServeEvent serveEvent) {
      int start =
          serveEvent
              .getRequest()
              .getQueryParams()
              .getOrDefault("S", QueryParameter.queryParam("S"))
              .getValues()
              .stream()
              .filter(StringUtils::isNotBlank)
              .map(Integer::parseInt)
              .findFirst()
              .orElse(0);
      int limit =
          serveEvent
              .getRequest()
              .getQueryParams()
              .getOrDefault("n", QueryParameter.queryParam("n"))
              .getValues()
              .stream()
              .filter(StringUtils::isNotBlank)
              .map(Integer::parseInt)
              .findFirst()
              .orElse(projectRepository.size());

      if (start >= projectRepository.size()) {
        return Response.response().status(200).body(new Gson().toJson(Map.of())).build();
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
                        throw new IllegalStateException(String.format("Duplicate key %s", u));
                      },
                      LinkedHashMap::new));
      return Response.response().status(200).body(new Gson().toJson(projectSlice)).build();
    }

    @Override
    public boolean applyGlobally() {
      return false;
    }
  }
}

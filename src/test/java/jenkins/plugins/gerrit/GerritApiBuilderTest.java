// Copyright (C) 2018 GerritForge Ltd
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

import com.google.gerrit.extensions.api.GerritApi;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import org.junit.*;
import org.junit.Rule;
import org.mockserver.junit.MockServerRule;
import org.mockserver.model.Header;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.JsonBody;
import org.mockserver.model.NottableString;
import org.mockserver.verify.VerificationTimes;

public class GerritApiBuilderTest {

  @Rule public MockServerRule g = new MockServerRule(this);

  @Test
  public void testShouldReturnValidGerritApiWithoutCredentials() throws URISyntaxException {
    GerritApi restApi = getGerritApiBuilderWithUri().credentials(null, null).build();
    assertNotNull(restApi);
  }

  private GerritApiBuilder getGerritApiBuilderWithUri() throws URISyntaxException {
    return new GerritApiBuilder().gerritApiUrl("http://gerrit.mycompany.com/a/project");
  }

  @Test
  public void testShouldReturnNullGerritApiWithoutCredentials() throws URISyntaxException {
    assertNull(getGerritApiBuilderWithUri().requireAuthentication().build());
  }

  @Test
  public void testShouldReturnNullGerritApiWithNullCredentials() throws URISyntaxException {
    assertNull(
        getGerritApiBuilderWithUri().credentials(null, null).requireAuthentication().build());
  }

  @Test
  public void testShouldReturnNullGerritApiWithEmptyCredentials() throws URISyntaxException {
    assertNull(getGerritApiBuilderWithUri().credentials("", "").requireAuthentication().build());
  }

  @Test
  public void testSanityAnonymous() throws Exception {
    int changeId = 4321;
    int revision = 1;

    GerritApi gerritApi =
        new GerritApiBuilder()
            .gerritApiUrl(
                String.format(
                    "http://%s:%s/a/project",
                    g.getClient().remoteAddress().getHostString(),
                    g.getClient().remoteAddress().getPort()))
            .build();
    assertNotNull(gerritApi);

    g.getClient()
        .when(HttpRequest.request("/a/project/login/").withMethod("POST"))
        .respond(HttpResponse.response().withStatusCode(200));

    g.getClient()
        .when(
            HttpRequest.request(
                    String.format("/a/project/changes/4321/revisions/1/files", changeId, revision))
                .withMethod("GET"))
        .respond(
            HttpResponse.response()
                .withStatusCode(200)
                .withBody(JsonBody.json(Collections.emptyMap())));

    gerritApi.changes().id(changeId).revision(revision).files();

    g.getClient()
        .verify(
            HttpRequest.request("/a/project/login/").withMethod("GET"), VerificationTimes.once());
    g.getClient()
        .verify(
            HttpRequest.request(
                    String.format("/a/project/changes/4321/revisions/1/files", changeId, revision))
                .withHeader(Header.header(NottableString.not("Authorization"))),
            VerificationTimes.once());
  }

  @Test
  public void testSanityAuthenticated() throws Exception {
    String user = "USERNAME";
    String password = "PASSWORD";
    int changeId = 4321;
    int revision = 1;

    GerritApi gerritApi =
        new GerritApiBuilder()
            .gerritApiUrl(
                String.format(
                    "http://%s:%s/a/project",
                    g.getClient().remoteAddress().getHostString(),
                    g.getClient().remoteAddress().getPort()))
            .credentials(user, password)
            .build();
    assertNotNull(gerritApi);

    g.getClient()
        .when(HttpRequest.request("/a/project/login/").withMethod("POST"))
        .respond(HttpResponse.response().withStatusCode(200));

    g.getClient()
        .when(
            HttpRequest.request(
                    String.format(
                        "/a/project/a/changes/4321/revisions/1/files", changeId, revision))
                .withMethod("GET"))
        .respond(
            HttpResponse.response()
                .withStatusCode(200)
                .withBody(JsonBody.json(Collections.emptyMap())));

    gerritApi.changes().id(changeId).revision(revision).files();

    g.getClient()
        .verify(
            HttpRequest.request("/a/project/login/").withMethod("POST"), VerificationTimes.once());
    g.getClient()
        .verify(
            HttpRequest.request(
                    String.format(
                        "/a/project/a/changes/4321/revisions/1/files", changeId, revision))
                .withHeader(
                    Header.header(
                        "Authorization",
                        String.format(
                            "Basic %s",
                            Base64.getEncoder()
                                .encodeToString(
                                    String.format("%s:%s", user, password)
                                        .getBytes(StandardCharsets.UTF_8))))),
            VerificationTimes.once());
  }
}

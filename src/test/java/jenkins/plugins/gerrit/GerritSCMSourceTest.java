// Copyright (C) 2019 SAP SE
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

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import java.util.Collections;
import jenkins.plugins.gerrit.checks.api.CheckerInput;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockserver.junit.MockServerRule;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.JsonBody;
import org.mockserver.verify.VerificationTimes;

public class GerritSCMSourceTest {

  @Rule public MockServerRule g = new MockServerRule(this);
  @Rule public JenkinsRule j = new JenkinsRule();

  @Test
  public void testCreateOrUpdateCheckerCreatesChecker() throws Exception {
    String hostUrl =
        String.format(
            "https://%s:%s",
            g.getClient().remoteAddress().getHostString(), g.getClient().remoteAddress().getPort());
    String remote = String.format("%s/a/test", hostUrl);
    String expectedUrl = "/a/plugins/checks/checkers/";

    CheckerInput checker = new CheckerInput();
    checker.uuid = "test:checker";
    checker.name = "test";

    UsernamePasswordCredentialsImpl c =
        new UsernamePasswordCredentialsImpl(
            CredentialsScope.GLOBAL, "cid", "cid", "USERNAME", "PASSWORD");
    CredentialsProvider.lookupStores(j.jenkins)
        .iterator()
        .next()
        .addCredentials(Domain.global(), c);
    GerritSCMSource gerritSCMSource = new GerritSCMSource(remote);
    gerritSCMSource.setCredentialsId(c.getId());
    gerritSCMSource.setInsecureHttps(true);

    g.getClient()
        .when(HttpRequest.request(expectedUrl + checker.uuid).withMethod("GET"))
        .respond(HttpResponse.response().withStatusCode(404));

    g.getClient()
        .when(HttpRequest.request(expectedUrl).withMethod("POST").withBody(JsonBody.json(checker)))
        .respond(
            HttpResponse.response()
                .withStatusCode(201)
                .withBody(JsonBody.json(Collections.emptyMap())));

    gerritSCMSource.createOrUpdateChecker(checker);

    g.getClient()
        .verify(HttpRequest.request(expectedUrl + checker.uuid))
        .clear(HttpRequest.request(expectedUrl + checker.uuid))
        .verify(HttpRequest.request(expectedUrl), VerificationTimes.once());
  }

  @Test
  public void testCreateOrUpdateCheckerUpdatesChecker() throws Exception {
    String hostUrl =
        String.format(
            "https://%s:%s",
            g.getClient().remoteAddress().getHostString(), g.getClient().remoteAddress().getPort());
    String remote = String.format("%s/a/test", hostUrl);

    CheckerInput checker = new CheckerInput();
    checker.uuid = "test:checker";
    checker.name = "test";

    String expectedUrl = "/a/plugins/checks/checkers/" + checker.uuid;

    UsernamePasswordCredentialsImpl c =
        new UsernamePasswordCredentialsImpl(
            CredentialsScope.GLOBAL, "cid", "cid", "USERNAME", "PASSWORD");
    CredentialsProvider.lookupStores(j.jenkins)
        .iterator()
        .next()
        .addCredentials(Domain.global(), c);
    GerritSCMSource gerritSCMSource = new GerritSCMSource(remote);
    gerritSCMSource.setCredentialsId(c.getId());
    gerritSCMSource.setInsecureHttps(true);

    g.getClient()
        .when(HttpRequest.request(expectedUrl).withMethod("GET"))
        .respond(HttpResponse.response().withStatusCode(200));

    g.getClient()
        .when(HttpRequest.request(expectedUrl).withMethod("POST").withBody(JsonBody.json(checker)))
        .respond(
            HttpResponse.response()
                .withStatusCode(200)
                .withBody(JsonBody.json(Collections.emptyMap())));

    gerritSCMSource.createOrUpdateChecker(checker);

    g.getClient().verify(HttpRequest.request(expectedUrl), VerificationTimes.exactly(2));
  }
}

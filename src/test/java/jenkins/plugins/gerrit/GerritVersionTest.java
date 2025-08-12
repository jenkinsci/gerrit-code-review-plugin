// Copyright (C) 2019 GerritForge Ltd
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

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.reset;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static org.junit.Assert.*;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.google.gerrit.extensions.api.GerritApi;
import org.junit.Rule;
import org.junit.Test;

public class GerritVersionTest {

  @Rule public WireMockRule wireMock = new WireMockRule();

  @Test
  public void testisVersionBelow215() throws Exception {
    GerritApi gerritApi =
        new GerritApiBuilder()
            .gerritApiUrl("http://localhost:" + wireMock.port())
            .credentials(null, null)
            .build();

    assertTrue(GerritVersion.isVersionBelow215(gerritApi));
    setGerritServerVersion("1.0");
    assertTrue(GerritVersion.isVersionBelow215(gerritApi));
    setGerritServerVersion("1.1.1");
    assertTrue(GerritVersion.isVersionBelow215(gerritApi));
    setGerritServerVersion("2");
    assertTrue(GerritVersion.isVersionBelow215(gerritApi));
    setGerritServerVersion("2.0");
    assertTrue(GerritVersion.isVersionBelow215(gerritApi));
    setGerritServerVersion("2.0.19");
    assertTrue(GerritVersion.isVersionBelow215(gerritApi));
    setGerritServerVersion("2.14");
    assertTrue(GerritVersion.isVersionBelow215(gerritApi));
    setGerritServerVersion("2.14.99");
    assertTrue(GerritVersion.isVersionBelow215(gerritApi));

    setGerritServerVersion("2.15");
    assertFalse(GerritVersion.isVersionBelow215(gerritApi));
    setGerritServerVersion("2.15.0");
    assertFalse(GerritVersion.isVersionBelow215(gerritApi));
    setGerritServerVersion("2.15.99");
    assertFalse(GerritVersion.isVersionBelow215(gerritApi));
    setGerritServerVersion("2.16");
    assertFalse(GerritVersion.isVersionBelow215(gerritApi));
    setGerritServerVersion("3");
    assertFalse(GerritVersion.isVersionBelow215(gerritApi));
    setGerritServerVersion("3.0");
    assertFalse(GerritVersion.isVersionBelow215(gerritApi));
    setGerritServerVersion("3.0.0");
    assertFalse(GerritVersion.isVersionBelow215(gerritApi));
    setGerritServerVersion("3.1");
    assertFalse(GerritVersion.isVersionBelow215(gerritApi));
    setGerritServerVersion("null");
    assertFalse(GerritVersion.isVersionBelow215(gerritApi));
    setGerritServerVersion("");
    assertFalse(GerritVersion.isVersionBelow215(gerritApi));
    setGerritServerVersion(" ");
    assertFalse(GerritVersion.isVersionBelow215(gerritApi));
    setGerritServerVersion(".");
    assertFalse(GerritVersion.isVersionBelow215(gerritApi));
    setGerritServerVersion("..");
    assertFalse(GerritVersion.isVersionBelow215(gerritApi));
    setGerritServerVersion("Error");
    assertFalse(GerritVersion.isVersionBelow215(gerritApi));
  }

  private void setGerritServerVersion(String version) {
    reset();
    stubFor(get("/config/server/version").willReturn(ok(")]}'\n" + "\"" + version + "\"")));
  }
}

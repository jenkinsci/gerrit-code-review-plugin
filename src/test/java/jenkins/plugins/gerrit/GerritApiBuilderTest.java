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
import com.google.gerrit.plugins.checks.client.GerritChecksApi;
import java.net.URISyntaxException;
import org.junit.*;
import org.mockserver.junit.MockServerRule;

public class GerritApiBuilderTest {

  @Rule public MockServerRule g = new MockServerRule(this);

  @Test
  public void testShouldReturnValidGerritApiWithoutCredentials() throws URISyntaxException {
    GerritApi restApi = getGerritApiBuilderWithUri().credentials(null, null).build();
    assertNotNull(restApi);
  }

  @Test
  public void testShouldReturnValidGerritChecksApiWithoutCredentials() throws URISyntaxException {
    GerritChecksApi restApi = getGerritApiBuilderWithUri().credentials(null, null).buildChecksApi();
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
  public void testShouldReturnNullGerritChecksApiWithoutCredentials() throws URISyntaxException {
    assertNull(getGerritApiBuilderWithUri().requireAuthentication().buildChecksApi());
  }

  @Test
  public void testShouldReturnNullGerritApiWithNullCredentials() throws URISyntaxException {
    assertNull(
        getGerritApiBuilderWithUri().credentials(null, null).requireAuthentication().build());
  }

  @Test
  public void testShouldReturnNullGerritChecksApiWithNullCredentials() throws URISyntaxException {
    assertNull(
        getGerritApiBuilderWithUri()
            .credentials(null, null)
            .requireAuthentication()
            .buildChecksApi());
  }

  @Test
  public void testShouldReturnNullGerritApiWithEmptyCredentials() throws URISyntaxException {
    assertNull(getGerritApiBuilderWithUri().credentials("", "").requireAuthentication().build());
  }

  @Test
  public void testShouldReturnNullGerritChecksApiWithEmptyCredentials() throws URISyntaxException {
    assertNull(
        getGerritApiBuilderWithUri().credentials("", "").requireAuthentication().buildChecksApi());
  }
}

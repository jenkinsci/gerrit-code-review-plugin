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

import java.net.URISyntaxException;
import org.eclipse.jgit.transport.URIish;
import org.junit.Test;

public class GerritURITest {

  @Test
  public void projectNameWithSlashesIsExtractedFromHTTPURI() throws URISyntaxException {
    GerritURI gerritURI = new GerritURI(new URIish("http://host/a/project/with/slashes"));

    assertEquals("project/with/slashes", gerritURI.getProject());
  }

  @Test
  public void anonymousAccessProjectNameIsExtractedFromHTTPURI() throws URISyntaxException {
    GerritURI gerritURI = new GerritURI(new URIish("http://host/project"));

    assertEquals("project", gerritURI.getProject());
  }

  @Test
  public void projectNameEndingWithDotGitIsExtractedFromHTTPURI() throws URISyntaxException {
    GerritURI gerritURI = new GerritURI(new URIish("http://host/project.git"));

    assertEquals("project", gerritURI.getProject());
  }

  @Test
  public void firstAInURIIsConsideredTheProjectMarker() throws URISyntaxException {
    GerritURI gerritURI = new GerritURI(new URIish("http://host/prefix/a/project/a/suffix"));

    assertEquals("/prefix", gerritURI.getPrefix());
    assertEquals("project/a/suffix", gerritURI.getProject());
  }

  @Test
  public void projectNameWithSlashesIsExtractedFromHTTPSURI() throws URISyntaxException {
    GerritURI gerritURI = new GerritURI(new URIish("https://host/a/project/with/slashes"));

    assertEquals("project/with/slashes", gerritURI.getProject());
  }

  @Test
  public void projectNameWithSlashesIsExtractedFromSSHURI() throws URISyntaxException {
    GerritURI gerritURI =
        new GerritURI(new URIish("ssh://username@host:29418/project/with/slashes"));

    assertEquals("project/with/slashes", gerritURI.getProject());
  }

  @Test
  public void prefixWithSlashesIsExtractedFromHTTPURI() throws URISyntaxException {
    GerritURI gerritURI = new GerritURI(new URIish("http://host/prefix/with/slashes/a/project"));

    assertEquals("/prefix/with/slashes", gerritURI.getPrefix());
  }

  @Test
  public void prefixWithSlashesIsExtractedFromHTTPSURI() throws URISyntaxException {
    GerritURI gerritURI = new GerritURI(new URIish("https://host/prefix/with/slashes/a/project"));

    assertEquals("/prefix/with/slashes", gerritURI.getPrefix());
  }

  @Test
  public void prefixOfSSHURIIsAlwaysEmpty() throws URISyntaxException {
    GerritURI gerritURI =
        new GerritURI(new URIish("ssh://username@host:29418/project/with/slashes"));

    assertEquals("", gerritURI.getPrefix());
  }
}

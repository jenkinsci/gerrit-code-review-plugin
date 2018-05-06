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
    GerritURI gerritURI = new GerritURI(new URIish("ssh://username@host:29418/project/with/slashes"));

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
    GerritURI gerritURI = new GerritURI(new URIish("ssh://username@host:29418/project/with/slashes"));

    assertEquals("", gerritURI.getPrefix());
  }

}

package jenkins.plugins.gerrit;

import java.net.URISyntaxException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.eclipse.jgit.transport.URIish;

/**
 * A GerritURI encapsulates a Gerrit remote URI and is able to extract certain properties like the
 * project name and URI prefix.
 */
public class GerritURI {

  private static enum Scheme {
    HTTP,
    HTTPS,
    SSH
  }

  /** Pattern which matches the URI prefix and the project name of a Gerrit HTTP URI. */
  private static final Pattern GERRIT_AUTH_HTTP_URI_PATTERN =
      Pattern.compile("(.*?)/a/(.*)(\\.git)?");

  private static final Pattern GERRIT_ANON_HTTP_URI_PATTERN =
      Pattern.compile("(/)(.*(?<!\\.git))(\\.git)?");

  private final URIish remoteURI;

  /**
   * Create a new Gerrit URI from the given remote {@link URIish URI}.
   *
   * @param remoteURI the remote URI, e.g. https://host/a/project
   */
  public GerritURI(URIish remoteURI) {
    this.remoteURI = remoteURI;
  }

  /**
   * Get the full Gerrit remote URI.
   *
   * @return the remote URI
   */
  public URIish getRemoteURI() {
    return remoteURI;
  }

  /**
   * Get the Gerrit project name from the remote URI.
   *
   * @return the Gerrit project name
   */
  public String getProject() {
    Scheme scheme = getScheme(remoteURI.getScheme());
    switch (scheme) {
      case HTTP:
      case HTTPS:
        Matcher matcher = getMatcher();
        if (!matcher.matches()) {
          throw new IllegalArgumentException(
              "Unable to determine Gerrit project from remote " + remoteURI);
        }

        return matcher.group(2);
      case SSH:
        return remoteURI.getRawPath().substring(1);
      default:
        throw new IllegalStateException("Unknown scheme " + scheme);
    }
  }

  /**
   * Get the URI prefix from the remote URI.
   *
   * @return the URI prefix, e.g. /gerrit/ when Gerrit is configured with httpd.listenUrl
   *     protocol://host:port/gerrit/.
   */
  public String getPrefix() {
    Scheme scheme = getScheme(remoteURI.getScheme());
    switch (scheme) {
      case HTTP:
      case HTTPS:
        Matcher matcher = getMatcher();
        if (!matcher.matches()) {
          throw new IllegalArgumentException(
              "Unable to determine Gerrit prefix from remote " + remoteURI);
        }

        return matcher.group(1);
      case SSH:
        return "";
      default:
        throw new IllegalStateException("Unknown scheme " + scheme);
    }
  }

  /**
   * Get the Gerrit REST-API base URL, using the correct prefix.
   *
   * @return Gerrit base URL for calling REST-API
   * @throws URISyntaxException if URL is invalid.
   */
  public URIish getApiURI() throws URISyntaxException {
    return remoteURI.setRawPath(getPrefix());
  }

  /**
   * Set the Gerrit path.
   *
   * @return Gerrit URL with the path.
   */
  public URIish setPath(String path) {
    return remoteURI.setPath(getPrefix() + path);
  }

  /**
   * Set a Gerrit project
   *
   * @return Gerrit URL to the project
   */
  public URIish setProject(String projectName) throws URISyntaxException {
    return remoteURI.setRawPath(getPrefix() + projectName);
  }

  private Matcher getMatcher() {
    String rawPath = remoteURI.getRawPath();
    if ("".equals(rawPath)) {
      rawPath = "/";
    }
    Matcher authMatcher = GERRIT_AUTH_HTTP_URI_PATTERN.matcher(rawPath);
    if (authMatcher.matches()) {
      return authMatcher;
    }
    return GERRIT_ANON_HTTP_URI_PATTERN.matcher(rawPath);
  }

  private Scheme getScheme(String value) {
    for (Scheme scheme : Scheme.values()) {
      if (scheme.name().equalsIgnoreCase(value)) {
        return scheme;
      }
    }
    throw new IllegalArgumentException("Unknown scheme " + value);
  }
}

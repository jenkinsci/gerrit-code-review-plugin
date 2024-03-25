package jenkins.plugins.gerrit.rest;

import java.io.IOException;

import org.apache.http.impl.client.CloseableHttpClient;
import org.eclipse.jgit.transport.URIish;

public abstract class AbstractApi {

  protected URIish gerritBaseUrl;
  protected CloseableHttpClient client;
  protected final boolean isAuthenticated;

  public AbstractApi(
      URIish gerritBaseUrl, CloseableHttpClient client, boolean isAuthenticated) {
    this.gerritBaseUrl = gerritBaseUrl;
    this.client = client;
    this.isAuthenticated = isAuthenticated;
  }

  public void close() throws IOException {
    client.close();
  }
}

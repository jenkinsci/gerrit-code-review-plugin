package com.google.gitiles.client;

import java.net.URI;
import java.net.URISyntaxException;

import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gitiles.api.CommitJsonData;

import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.eclipse.jgit.transport.URIish;

import jenkins.plugins.gerrit.rest.AbstractEndpoint;

public class RevisionView extends AbstractEndpoint {

  private String project;
  private String rev;

  protected RevisionView(URIish gerritBaseUrl, CloseableHttpClient client, boolean isAuthenticated, String project, String rev)
      throws URISyntaxException {
    super(gerritBaseUrl, client, isAuthenticated);
    this.project = project;
    this.rev = rev;
  }

  public CommitJsonData.Commit get() throws RestApiException {
    try {
      return getJsonResponse(new HttpGet(buildUri("show", "JSON")));
    } catch (Exception e) {
      throw new RestApiException("Failed to get revision info: ", e);
    }
  }

  private URI buildUri(String command, String format) throws URISyntaxException {
    return getUriBuilder(command, format).build();
  }

  private URIBuilder getUriBuilder(String command, String format) {
    return uriBuilder.setPath(String.format("%splugins/gitiles/%s/+%s/%s", getPrefix(), project, command, rev))
        .addParameter("format", format);
  }
}

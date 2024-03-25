package com.google.gitiles.client;

import java.net.URISyntaxException;
import org.apache.http.impl.client.CloseableHttpClient;
import org.eclipse.jgit.transport.URIish;

import jenkins.plugins.gerrit.rest.AbstractApi;

public class GerritGitilesApi extends AbstractApi {

  public GerritGitilesApi(
      URIish gerritBaseUrl, CloseableHttpClient client, boolean isAuthenticated) {
      super(gerritBaseUrl, client, isAuthenticated);
  }

  public RevisionView revisionView(String project, String rev) throws URISyntaxException {
    return new RevisionView(gerritBaseUrl, client, isAuthenticated, project, rev);
  }

  public PathView pathView(String project, String rev, String path) throws URISyntaxException {
    return new PathView(gerritBaseUrl, client, isAuthenticated, project, rev, path);
  }
}

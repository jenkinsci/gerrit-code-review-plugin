package com.google.gitiles.client;

import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;

import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gitiles.api.CommitJsonData;
import com.google.gitiles.api.TreeJsonData;

import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.io.IOUtils;
import org.apache.commons.codec.binary.Base64InputStream;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.eclipse.jgit.transport.URIish;

import jenkins.plugins.gerrit.rest.AbstractEndpoint;

public class PathView extends AbstractEndpoint {

  private String project;
  private String rev;
  private String path;

  public PathView(URIish gerritBaseUrl, CloseableHttpClient client, boolean isAuthenticated, String project, String rev,
      String path) throws URISyntaxException {
    super(gerritBaseUrl, client, isAuthenticated);
    this.project = project;
    this.rev = rev;
    this.path = path;
  }

  public TreeJsonData.Tree tree() throws RestApiException {
    try {
      return getJsonResponse(new HttpGet(buildUri("", "JSON")));
    } catch (Exception e) {
      throw new RestApiException("Failed to get tree info: ", e);
    }
  }

  public CommitJsonData.Log log(int count) throws RestApiException {
    try {
      URI uri = getUriBuilder("log", "JSON").addParameter("n", count + "").build();
      return getJsonResponse(new HttpGet(uri));
    } catch (Exception e) {
      throw new RestApiException("Failed to get log info: ", e);
    }
  }

  public int mode() throws RestApiException {
    try {
      HttpHead request = new HttpHead(buildUri("", "text"));
      try (CloseableHttpResponse response = client.execute(request)) {
        if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
          return Integer.parseInt(response.getFirstHeader("X-Gitiles-Path-Mode").getValue(), 8);
        }
        throw new RestApiException(
            String.format("Request failed with status: %d", response.getStatusLine().getStatusCode()));
      }
    } catch (Exception e) {
      throw new RestApiException("Failed to get mode info: ", e);
    }
  }

  public InputStream content() throws RestApiException {
    try {
      HttpGet request = new HttpGet(buildUri("", "text"));
      try (CloseableHttpResponse response = client.execute(request)) {
        if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
          String str = EntityUtils.toString(response.getEntity());
          return new Base64InputStream(IOUtils.toInputStream(str));
        }
        throw new RestApiException(
            String.format("Request failed with status: %d", response.getStatusLine().getStatusCode()));
      }
    } catch (Exception e) {
      throw new RestApiException("Failed to get mode info: ", e);
    }
  }

  private URI buildUri(String command, String format) throws URISyntaxException {
    return getUriBuilder(command, format).build();
  }

  private URIBuilder getUriBuilder(String command, String format) {
    return uriBuilder.setPath(String.format("%splugins/gitiles/%s/+%s/%s/%s", getPrefix(), project, command, rev, path))
        .addParameter("format", format);
  }
}

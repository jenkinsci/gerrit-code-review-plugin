package com.google.gitiles.client;

import org.eclipse.jgit.transport.URIish;

import jenkins.plugins.gerrit.rest.AbstractApiBuilder;

public class GerritGitilesApiBuilder extends AbstractApiBuilder<GerritGitilesApi> {

  public GerritGitilesApiBuilder(URIish gerritBaseURL) {
    super(gerritBaseURL);
  }

  @Override
  public GerritGitilesApi build() {
    return new GerritGitilesApi(gerritBaseURL, clientBuilder.build(), isAuthenticated);
  }
}

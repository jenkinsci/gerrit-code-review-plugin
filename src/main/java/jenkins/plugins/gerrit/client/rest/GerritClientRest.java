package jenkins.plugins.gerrit.client.rest;

import com.google.gerrit.extensions.api.changes.Changes;
import com.urswolfer.gerrit.client.rest.GerritRestApi;
import jenkins.plugins.gerrit.client.GerritClient;

public class GerritClientRest implements GerritClient {

    private final String scheme;
    private final GerritRestApi delegate;

    public GerritClientRest(GerritRestApi gerritRestApi, String scheme) {
        this.delegate = gerritRestApi;
        this.scheme = scheme;
    }

    @Override
    public Changes changes() {
        return delegate.changes();
    }

    @Override
    public String getScheme() {
        return scheme;
    }
}

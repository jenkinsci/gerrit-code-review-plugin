package jenkins.plugins.gerrit.client;

import com.google.gerrit.extensions.api.changes.Changes;
import jenkins.plugins.gerrit.GerritURI;

public interface GerritClient {
    Changes changes();

    String getScheme();
}

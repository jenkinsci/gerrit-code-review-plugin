package jenkins.plugins.gerrit.api.ssh;

import com.google.gerrit.extensions.api.GerritApi;
import com.google.gerrit.extensions.api.changes.*;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.urswolfer.gerrit.client.rest.http.changes.ChangesParser;
import com.urswolfer.gerrit.client.rest.http.changes.ReviewResultParser;
import org.apache.commons.io.IOUtils;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.channel.ChannelExec;
import org.apache.sshd.client.future.ConnectFuture;
import org.apache.sshd.client.session.ClientSession;
import org.eclipse.jgit.transport.URIish;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class GerritApiSSH extends GerritApi.NotImplemented {

    private URIish gerritApiUrl;
    private final int timeout;
    private final Logger logger = LoggerFactory.getLogger(GerritApiSSH.class);
    private SshClient sshClient;
    private Gson gson = new Gson();
    private ChangesParser changesParser = new ChangesParser(gson);
    private ReviewResultParser reviewResultParser = new ReviewResultParser(gson);

    public GerritApiSSH(SshClient sshClient, URIish gerritApiUrl, int timeout) {
        this.sshClient = sshClient;
        this.gerritApiUrl = gerritApiUrl;
        this.timeout = timeout;
    }

    @Override
    public Changes changes() {
        return new ChangesSSH();
    }

    private JsonElement request(String request) throws IOException {
        return request(request, null);
    }

    private JsonElement request(String request, String stdin) throws IOException {
        logger.debug("Initiating request '{}'", request);
        sshClient.start();
        ConnectFuture connectFuture = sshClient.connect(gerritApiUrl.getUser(), gerritApiUrl.getHost(), gerritApiUrl.getPort());
        connectFuture.await(timeout, TimeUnit.MILLISECONDS);
        if (!connectFuture.isConnected()) {
            throw new IOException(String.format("Failed to connect to %s:%s", gerritApiUrl.getHost(), gerritApiUrl.getPort()));
        }
        try (ClientSession session = connectFuture.getSession()) {
            session.auth().await(timeout, TimeUnit.MILLISECONDS);
            if (!session.isAuthenticated()) {
                throw new IOException(String.format("Failed to authenticate to %s:%s", gerritApiUrl.getHost(), gerritApiUrl.getPort()));
            }
            String json;
            if (stdin != null) {
                try (ChannelExec channel = session.createExecChannel(request)) {
                    channel.open().await(timeout, TimeUnit.MILLISECONDS);
                    logger.debug("Writing to stdin: '{}'", stdin);
                    IOUtils.write(stdin, channel.getInvertedIn(),  StandardCharsets.UTF_8);
                    logger.debug("Finished writing to stdin. Closing");
                    channel.getInvertedIn().close();
                    json = IOUtils.toString(channel.getInvertedOut(),  StandardCharsets.UTF_8);
                }
            } else {
                json = session.executeRemoteCommand(request);
            }
            logger.debug("Got response: '{}'", json);
            return gson.fromJson(json, JsonElement.class);
        }
    }

    private class ChangesSSH extends Changes.NotImplemented {

        @Override
        public ChangeApi id(int id) {
            return new ChangeApiSSH(id);
        }

        @Override
        public QueryRequest query(String query) {
            return new QueryRequest() {
                @Override
                public List<ChangeInfo> get() throws RestApiException {
                    try {
                        return changesParser.parseChangeInfos(request("gerrit query --format=json" + query));
                    } catch (IOException e) {
                        throw new RestApiException("Failed to process query", e);
                    }
                }
            };
        }
    }

    private class ChangeApiSSH extends ChangeApi.NotImplemented {
        private final int changeId;

        ChangeApiSSH(int changeId) {
            this.changeId = changeId;
        }

        @Override
        public RevisionApi revision(int id) {
            return revision(Integer.toString(id));
        }

        @Override
        public RevisionApi revision(String id) {
            return new RevisionApiSSH(changeId, id);
        }
    }

    private class RevisionApiSSH extends RevisionApi.NotImplemented {
        private final String revisionId;
        private final int changeId;

        RevisionApiSSH(int changeId, String revisionId) {
            this.changeId = changeId;
            this.revisionId = revisionId;
        }

        @Override
        public ReviewResult review(ReviewInput reviewInput) throws RestApiException {
            String request = "gerrit review " + changeId + "," + revisionId + " --json ";
            String json = gson.toJson(reviewInput);
            JsonElement reviewResult;
            try {
                reviewResult = request(request, json);
            } catch (IOException e) {
                throw new RestApiException("Failed to process review", e);
            }
            return reviewResultParser.parseReviewResult(reviewResult);
        }

        @Override
        public DraftApi createDraft(DraftInput in) throws RestApiException {
            return super.createDraft(in);
        }
    }
}

package jenkins.plugins.gerrit.api.ssh;

import com.google.gerrit.extensions.api.GerritApi;
import com.google.gerrit.extensions.api.changes.ChangeApi;
import com.google.gerrit.extensions.api.changes.Changes;
import com.google.gerrit.extensions.api.changes.DraftApi;
import com.google.gerrit.extensions.api.changes.DraftInput;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.api.changes.ReviewResult;
import com.google.gerrit.extensions.api.changes.RevisionApi;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.CommentInfo;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.urswolfer.gerrit.client.rest.http.changes.ChangesParser;
import com.urswolfer.gerrit.client.rest.http.changes.CommentsParser;
import com.urswolfer.gerrit.client.rest.http.changes.ReviewResultParser;
import org.apache.commons.io.IOUtils;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.channel.ChannelExec;
import org.apache.sshd.client.channel.ClientChannelEvent;
import org.apache.sshd.client.future.ConnectFuture;
import org.apache.sshd.client.session.ClientSession;
import org.eclipse.jgit.transport.URIish;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class GerritApiSSH extends GerritApi.NotImplemented {

    private URIish gerritApiUrl;
    private final int timeout;
    private SshClient sshClient;
    private Gson gson = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create();
    private ChangesParser changesParser = new ChangesParser(gson);
    private ReviewResultParser reviewResultParser = new ReviewResultParser(gson);
    private CommentsParser commentsParser = new CommentsParser(gson);

    public GerritApiSSH(SshClient sshClient, URIish gerritApiUrl, int timeout) {
        this.sshClient = sshClient;
        this.gerritApiUrl = gerritApiUrl;
        this.timeout = timeout;
    }

    @Override
    public Changes changes() {
        return new ChangesSSH();
    }

    private Optional<JsonElement> request(String request) throws IOException {
        return request(request, null);
    }

    private Optional<JsonElement> request(String request, String stdin) throws IOException {
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
            if (stdin != null) {
                try (ChannelExec channel = session.createExecChannel(request)) {
                    channel.open().verify().await(timeout, TimeUnit.MILLISECONDS);
                    if (!channel.isOpen()) {
                        throw new IOException("Failed to open SSH channel");
                    }
                    IOUtils.write(stdin, channel.getInvertedIn(),  StandardCharsets.UTF_8);
                    channel.getInvertedIn().flush();
                    channel.getInvertedIn().close();
                    channel.waitFor(EnumSet.of(ClientChannelEvent.EOF), 0);
                    channel.close(false).await(timeout, TimeUnit.MILLISECONDS);
                    if (channel.getExitStatus() != null && channel.getExitStatus() != 0) {
                        String outputMessage = IOUtils.toString(channel.getInvertedOut(), StandardCharsets.UTF_8);
                        String errorMessage = IOUtils.toString(channel.getInvertedErr(), StandardCharsets.UTF_8);
                        throw new IOException(String.format("SSH exited with status '%s', stdout: '%s', stderr: '%s'", channel.getExitStatus(), outputMessage, errorMessage));
                    }
                    return Optional.ofNullable(gson.fromJson(IOUtils.toString(channel.getInvertedOut(), StandardCharsets.UTF_8), JsonElement.class));
                }
            } else {
                return Optional.ofNullable(gson.fromJson(session.executeRemoteCommand(request), JsonElement.class));
            }
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
                        Optional<JsonElement> result = request("gerrit query --format=json" + query);
                        if (result.isPresent()) {
                            return changesParser.parseChangeInfos(result.get());
                        }
                        return changesParser.parseChangeInfos(JsonNull.INSTANCE);
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
            try {
                String request = String.format("gerrit review %s,%s --json", changeId, revisionId);
                String json = gson.toJson(reviewInput);
                Optional<JsonElement> result = request(request, json);
                if (result.isPresent()) {
                    return reviewResultParser.parseReviewResult(result.get());
                } else {
                    return reviewResultParser.parseReviewResult(JsonNull.INSTANCE);
                }
            } catch (IOException e) {
                throw new RestApiException("Failed to process review", e);
            }
        }

        @Override
        public DraftApi createDraft(DraftInput in) throws RestApiException {
            try {
                String request = String.format("gerrit review %s,%s --json", changeId, revisionId);
                Map<String, Map<String, Set>> reviewInput = new HashMap<>();
                Map<String, Set> comments = new HashMap<>();
                comments.put(in.path, Collections.singleton(in));
                reviewInput.put("comments", comments);
                String json = gson.toJson(reviewInput);
                Optional<JsonElement> result = request(request, json);
                if (result.isPresent()) {
                    CommentInfo commentInfo = commentsParser.parseSingleCommentInfo(result.get().getAsJsonObject());
                    return new DraftApiSSH(commentInfo);
                } else {
                    return new DraftApiSSH(null);
                }
            } catch (IOException e) {
                throw new RestApiException("Failed to process draft", e);
            }
        }
    }

    private class DraftApiSSH extends DraftApi.NotImplemented {
        DraftApiSSH(CommentInfo commentInfo) {
            //Not in use for now
        }
    }
}

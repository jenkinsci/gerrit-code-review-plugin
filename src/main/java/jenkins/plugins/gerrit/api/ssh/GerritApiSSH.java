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
import org.eclipse.jgit.transport.RemoteSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class GerritApiSSH extends GerritApi.NotImplemented {

    private final int timeout;
    private final Logger logger = LoggerFactory.getLogger(GerritApiSSH.class);
    private RemoteSession delegate;
    private Gson gson = new Gson();
    private ChangesParser changesParser = new ChangesParser(gson);
    private ReviewResultParser reviewResultParser = new ReviewResultParser(gson);

    public GerritApiSSH(RemoteSession remoteSession, int timeout) {
        this.delegate = remoteSession;
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
        Process process = delegate.exec(request, timeout);
        if (stdin != null) {
            if (!isAlive(process)) {
                throw new IOException("Failed to initiate request '" + request + "'. Socket closed before we could send data");
            }
            logger.debug("Writing to stdin: '{}'", stdin);
            IOUtils.write(stdin, process.getOutputStream(), Charset.defaultCharset());
            process.getOutputStream().flush();
            logger.debug("Finished writing to stdin. Closing channel");
            process.getOutputStream().close();
        }
        boolean finished = false;
        try {
            finished = waitFor(process, timeout, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            throw new IOException("Interrupted while waiting for SSH process to finish", e);
        }
        if (!finished) {
            throw new IOException("Process did not finish in " + TimeUnit.MILLISECONDS.toSeconds(timeout) + " seconds");
        }
        if (isAlive(process)) { //Should hopefully not happen, but we'll destroy just in case.
            process.destroy();
        }
        if (process.exitValue() != 0) {
            String errorStream = IOUtils.toString(process.getErrorStream(), Charset.defaultCharset());
            throw new IOException("Failed to send request. Process exitValue was '" + process.exitValue() + "' Error stream: " + errorStream);
        }
        String json = IOUtils.toString(process.getInputStream(), Charset.defaultCharset());
        logger.debug("Got response: '{}'", json);
        return gson.fromJson(json, JsonElement.class);
    }

    //Workaround for bug in JschProcess where an IllegalStateException is thrown instead of IllegalThreadStateException
    private static boolean isAlive(Process process) {
        try {
            process.exitValue();
            return false;
        } catch (IllegalStateException ignore) {
            return true;
        }
    }

    //Workaround for bug in JschProcess where an IllegalStateException is thrown instead of IllegalThreadStateException
    private static boolean waitFor(Process process, long timeout, TimeUnit unit)
            throws InterruptedException
    {
        long startTime = System.nanoTime();
        long rem = unit.toNanos(timeout);

        do {
            try {
                process.exitValue();
                return true;
            } catch(IllegalStateException ex) {
                if (rem > 0)
                    Thread.sleep(
                            Math.min(TimeUnit.NANOSECONDS.toMillis(rem) + 1, 100));
            }
            rem = unit.toNanos(timeout) - (System.nanoTime() - startTime);
        } while (rem > 0);
        return false;
    }

    private class ChangesSSH extends Changes.NotImplemented {

        @Override
        public ChangeApi id(int id) throws RestApiException {
            return new ChangeApiSSH(id);
        }

        @Override
        public QueryRequest query(String query) {
            return new QueryRequest() {
                @Override
                public List<ChangeInfo> get() throws RestApiException {
                    JsonElement jsonElement = null;
                    try {
                        jsonElement = request("gerrit query --format=json" + query);
                    } catch (IOException e) {
                        throw new RestApiException("Failed to process query", e);
                    }
                    return changesParser.parseChangeInfos(jsonElement);
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
        public RevisionApi revision(int id) throws RestApiException {
            return revision(Integer.toString(id));
        }

        @Override
        public RevisionApi revision(String id) throws RestApiException {
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

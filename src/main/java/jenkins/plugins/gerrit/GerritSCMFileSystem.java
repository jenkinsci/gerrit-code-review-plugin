package jenkins.plugins.gerrit;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

import com.google.gitiles.api.CommitJsonData.Commit;
import com.google.gitiles.client.GerritGitilesApi;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Item;
import hudson.scm.SCM;
import hudson.scm.SCMDescriptor;
import jenkins.plugins.git.AbstractGitSCMSource.SCMRevisionImpl;
import jenkins.scm.api.SCMFile;
import jenkins.scm.api.SCMFileSystem;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.SCMSourceDescriptor;

public class GerritSCMFileSystem extends SCMFileSystem {

    private final GerritGitilesApi gitilesApi;
    private final String projectPath;
    private final String ref;

    protected GerritSCMFileSystem(
        GerritGitilesApi gitilesApi,
        String projectPath,
        @NonNull SCMRevisionImpl rev) {
        super(rev);
        this.gitilesApi = gitilesApi;
        this.projectPath = projectPath;
        this.ref = rev.getHash();
    }

    protected GerritSCMFileSystem(
        GerritGitilesApi gitilesApi,
        String projectPath,
        @NonNull SCMHead head) {
        super(null);
        this.gitilesApi = gitilesApi;
        this.projectPath = projectPath;
        this.ref = head.getName();
    }

    @Override
    public long lastModified() throws IOException, InterruptedException {
        try {
            Commit commit = gitilesApi.revisionView(projectPath, ref).get();
            SimpleDateFormat df = new SimpleDateFormat("EEE MMM dd HH:mm:ss yyyy Z");
            Date date = df.parse(commit.committer.time);
            return date.getTime();
        }
        catch (Exception e) {
            throw new IOException(e);
        }
    }

    @Override
    public SCMFile getRoot() {
        return new GerritSCMFile(gitilesApi, projectPath, ref);
    }
    
    @Extension
    public static class BuilderImpl extends Builder {

        @Override
        public boolean supports(SCM source) {
            return false;
        }

        @Override
        protected boolean supportsDescriptor(SCMDescriptor arg0) {
            return false;
        }

        @Override
        public boolean supports(SCMSource source) {
            return source instanceof GerritSCMSource;
        }

        @Override
        protected boolean supportsDescriptor(SCMSourceDescriptor arg0) {
            return false;
        }
        @Override
        public SCMFileSystem build(Item owner, SCM scm, SCMRevision rev) {
            return null;
        }

        @Override
        public SCMFileSystem build(@NonNull SCMSource source, @NonNull SCMHead head,
            @CheckForNull SCMRevision rev) throws IOException {
            if (!(source instanceof GerritSCMSource)) {
                return null;
            }
            GerritSCMSource gerritScmSource = (GerritSCMSource) source;
            GerritGitilesApi gitilesApi = gerritScmSource.createGerritGitilesApi();
            String projectPath = gerritScmSource.getGerritURI().getProject();
            if ((rev != null) && rev instanceof SCMRevisionImpl) {
                return new GerritSCMFileSystem(gitilesApi, projectPath, (SCMRevisionImpl)rev);
            }
            return new GerritSCMFileSystem(gitilesApi, projectPath, head);
        }
    }
}

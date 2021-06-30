package jenkins.plugins.gerrit;

import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.stream.Collectors;

import com.google.gitiles.api.CommitJsonData.Log;
import com.google.gitiles.api.TreeJsonData.Tree;
import com.google.gitiles.client.GerritGitilesApi;

import org.eclipse.jgit.lib.FileMode;

import edu.umd.cs.findbugs.annotations.NonNull;
import jenkins.scm.api.SCMFile;

public class GerritSCMFile extends SCMFile {

    private final GerritGitilesApi gitilesApi;
    private final String projectPath;
    private final String ref;

    public GerritSCMFile(GerritGitilesApi gitilesApi, String projectPath, String ref) {
        super();
        this.gitilesApi = gitilesApi;
        type(Type.DIRECTORY);
        this.projectPath = projectPath;
        this.ref = ref;
    }

    private GerritSCMFile(@NonNull GerritSCMFile parent, String name, boolean assumeIsDirectory) {
        super(parent, name);
        this.gitilesApi = parent.gitilesApi;
        this.projectPath = parent.projectPath;
        this.ref = parent.ref;
        if (assumeIsDirectory) {
            type(Type.DIRECTORY);
        }
    }

    private GerritSCMFile(GerritSCMFile parent, String name, Type type) {
        super(parent, name);
        this.gitilesApi = parent.gitilesApi;
        this.projectPath = parent.projectPath;
        this.ref = parent.ref;
        type(type);
    }

    @NonNull
    @Override
    protected SCMFile newChild(String name, boolean assumeIsDirectory) {
        return new GerritSCMFile(this, name, assumeIsDirectory);
    }

    @NonNull
    @Override
    public Iterable<SCMFile> children() throws IOException, InterruptedException {
        if (!this.isDirectory()) {
            throw new IOException("Cannot get children from a regular file");
        }

        try {
            Tree treeItems = gitilesApi.pathView(projectPath, ref, getPath()).tree();
            return treeItems.entries.stream().map(entry -> {
                return new GerritSCMFile(this, entry.name, modeToType(entry.mode));
            }).collect(Collectors.toList());
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    @Override
    public long lastModified() throws IOException, InterruptedException {
        try {
            Log log = gitilesApi.pathView(projectPath, ref, getPath()).log(1);
            SimpleDateFormat df = new SimpleDateFormat("EEE MMM dd HH:mm:ss yyyy Z");
            Date date = df.parse(log.log.get(0).committer.time);
            return date.getTime();
        }
        catch (Exception e) {
            throw new IOException(e);
        }
    }

    @Override
    protected Type type() throws IOException, InterruptedException {
        try {
            int mode = gitilesApi.pathView(projectPath, ref, getPath()).mode();
            return modeToType(mode);
        }
        catch (Exception e) {
            throw new IOException(e);
        }
    }

    @Override
    public InputStream content() throws IOException, InterruptedException {
        if (this.isDirectory()) {
            throw new IOException("Cannot get raw content from a directory");
        }

        try {
            return gitilesApi.pathView(projectPath, ref, getPath()).content();
        }
        catch (Exception e) {
            throw new IOException(e);
        }
    }

    private Type modeToType(int mode) {
        if (FileMode.TREE.equals(mode)) {
            return Type.DIRECTORY;
        } else if (FileMode.REGULAR_FILE.equals(mode) || FileMode.EXECUTABLE_FILE.equals(mode)) {
            return Type.REGULAR_FILE;
        } else if (FileMode.SYMLINK.equals(mode)) {
            return Type.LINK;
        } else if (FileMode.MISSING.equals(mode)) {
            return Type.NONEXISTENT;
        }
        return Type.OTHER;
    }
}

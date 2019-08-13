// Copyright (C) 2018 GerritForge Ltd
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package jenkins.plugins.gerrit;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.extensions.api.GerritApi;
import com.google.gerrit.extensions.api.changes.Changes;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.restapi.RestApiException;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.EnvVars;
import hudson.model.Action;
import hudson.model.Actionable;
import hudson.model.TaskListener;
import hudson.plugins.git.GitTool;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.stream.Collectors;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import jenkins.plugins.git.AbstractGitSCMSource;
import jenkins.plugins.git.GitRemoteHeadRefAction;
import jenkins.plugins.git.GitSCMSourceContext;
import jenkins.plugins.git.GitSCMSourceRequest;
import jenkins.scm.api.*;
import jenkins.scm.api.metadata.ObjectMetadataAction;
import jenkins.scm.api.trait.SCMSourceRequest;
import org.apache.commons.lang.StringUtils;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.jenkinsci.plugins.gitclient.FetchCommand;
import org.jenkinsci.plugins.gitclient.Git;
import org.jenkinsci.plugins.gitclient.GitClient;

public abstract class AbstractGerritSCMSource extends AbstractGitSCMSource {
  public static final String R_CHANGES = "refs/changes/";
  public static final String REF_SPEC_CHANGES = "+refs/changes/*:refs/remotes/@{remote}/*";

  private ProjectOpenChanges projectOpenChanges;

  public interface Retriever<T> {
    T run(
        GitClient client,
        GitSCMSourceContext context,
        String remoteName,
        Changes.QueryRequest changeQuery)
        throws IOException, InterruptedException;
  }

  public AbstractGerritSCMSource() {}

  @SuppressFBWarnings(value = "NP_BOOLEAN_RETURN_NULL", justification = "Overridden")
  public Boolean getInsecureHttps() {
    return null;
  }

  /** Return the Gerrit change information associated with a WorkflowJob branch */
  public Optional<ChangeInfo> getChangeInfo(int changeNum) throws IOException {
    return getProjectOpenChanges().get(changeNum);
  }

  /** {@inheritDoc} */
  @Override
  @SuppressFBWarnings(value = "SE_BAD_FIELD", justification = "Known non-serializable this")
  protected void retrieve(
      @CheckForNull SCMSourceCriteria criteria,
      @Nonnull SCMHeadObserver observer,
      @CheckForNull SCMHeadEvent<?> event,
      @Nonnull final TaskListener listener)
      throws IOException, InterruptedException {
    doRetrieve(
        new Retriever<Object>() {
          @SuppressWarnings("deprecation")
          @Override
          public Object run(
              GitClient client,
              GitSCMSourceContext context,
              String remoteName,
              Changes.QueryRequest changeQuery)
              throws IOException, InterruptedException {
            final Repository repository = client.getRepository();
            try (RevWalk walk = new RevWalk(repository);
                GitSCMSourceRequest request =
                    context.newRequest(AbstractGerritSCMSource.this, listener)) {
              Map<String, ObjectId> remoteReferences = null;
              if (context.wantBranches() || context.wantTags()) {
                listener.getLogger().println("Listing remote references...");
                remoteReferences =
                    client.getRemoteReferences(
                        client.getRemoteUrl(remoteName), null, false, context.wantTags());
              }
              if (context.wantBranches()) {
                discoverBranches(repository, walk, request, remoteReferences, changeQuery);
              }
              if (context.wantTags()) {
                // TODO
              }
            }
            return null;
          }

          private void discoverBranches(
              final Repository repository,
              final RevWalk walk,
              GitSCMSourceRequest request,
              final Map<String, ObjectId> remoteReferences,
              Changes.QueryRequest changeQuery)
              throws IOException, InterruptedException {

            try {
              listener.getLogger().println("Checking branches ...");
              listener.getLogger().println(remoteReferences);
              Map<String, ObjectId> filteredRefs = filterRemoteReferences(remoteReferences);
              listener.getLogger().println("Filtered branches ...");
              listener.getLogger().println(filteredRefs);
              walk.setRetainBody(false);
              int branchesCount = 0;
              int changesCount = 0;
              HashMap<Integer, ChangeInfo> openChanges = getOpenChanges(changeQuery);

              for (final Map.Entry<String, ObjectId> ref : filteredRefs.entrySet()) {
                String refKey = ref.getKey();
                if (!refKey.startsWith(Constants.R_HEADS) && !refKey.startsWith(R_CHANGES)) {
                  listener.getLogger().println("Skipping branches " + refKey);
                  continue;
                }

                String refName = ref.getKey();
                if (refName.startsWith(R_CHANGES)) {
                  ChangeInfo openChangeInfo = getOpenChange(refName, openChanges);
                  if (openChangeInfo != null) {
                    if (processChangeRequest(
                        repository, walk, request, ref, openChangeInfo, listener)) {
                      listener
                          .getLogger()
                          .format("Processed %d changes (query complete)%n", changesCount);
                      return;
                    }
                  }
                } else {
                  if (processBranchRequest(repository, walk, request, ref, listener)) {
                    listener
                        .getLogger()
                        .format("Processed %d branches (query complete)%n", branchesCount);
                    return;
                  }
                }
              }
              listener.getLogger().format("Processed %d branches%n", branchesCount);
              listener.getLogger().format("Processed %d changes%n", changesCount);
            } catch (RestApiException e) {
              throw new IOException(e);
            }
          }
        },
        new GitSCMSourceContext<>(criteria, observer).withTraits(getTraits()),
        listener,
        true);
  }

  private ChangeInfo getOpenChange(String refName, HashMap<Integer, ChangeInfo> openChanges) {
    String[] changeParts = refName.substring(R_CHANGES.length()).split("/");
    Integer changeNumber = Integer.valueOf(changeParts[1]);
    return openChanges.get(changeNumber);
  }

  private HashMap<Integer, ChangeInfo> getOpenChanges(Changes.QueryRequest changeQuery)
      throws RestApiException {
    HashMap<Integer, ChangeInfo> openChanges = new HashMap<>();

    for (ChangeInfo change : changeQuery.get()) {
      openChanges.put(Integer.valueOf(change._number), change);
    }

    return openChanges;
  }

  /** {@inheritDoc} */
  @Nonnull
  @Override
  protected List<Action> retrieveActions(
      @CheckForNull SCMSourceEvent event, @Nonnull TaskListener listener)
      throws IOException, InterruptedException {
    return doRetrieve(
        new Retriever<List<Action>>() {
          @Override
          public List<Action> run(
              GitClient client,
              GitSCMSourceContext context,
              String remoteName,
              Changes.QueryRequest queryRequest)
              throws IOException, InterruptedException {
            Map<String, String> symrefs = client.getRemoteSymbolicReferences(getRemote(), null);
            if (symrefs.containsKey(Constants.HEAD)) {
              // Hurrah! The Server is Git 1.8.5 or newer and our client has symref reporting
              String target = symrefs.get(Constants.HEAD);
              if (target.startsWith(Constants.R_HEADS)) {
                // shorten standard names
                target = target.substring(Constants.R_HEADS.length());
              }
              List<Action> result = new ArrayList<>();
              if (StringUtils.isNotBlank(target)) {
                result.add(new GitRemoteHeadRefAction(getRemote(), target));
              }
              result.add(new GerritLogo());
              return result;
            }

            // Give up, there's no way to get the primary branch
            return new ArrayList<>();
          }
        },
        new GitSCMSourceContext<>(null, SCMHeadObserver.none()).withTraits(getTraits()),
        listener,
        false);
  }

  /** {@inheritDoc} */
  @NonNull
  @Override
  protected List<Action> retrieveActions(
      @NonNull SCMHead head, @CheckForNull SCMHeadEvent event, @NonNull TaskListener listener)
      throws IOException, InterruptedException {
    final List<Action> actions =
        doRetrieve(
            (GitClient client,
                GitSCMSourceContext context,
                String remoteName,
                Changes.QueryRequest changeQuery) -> {
              SCMSourceOwner owner = getOwner();
              if (owner instanceof Actionable && head instanceof ChangeSCMHead) {
                final Actionable actionableOwner = (Actionable) owner;
                final ChangeSCMHead change = (ChangeSCMHead) head;
                String gerritBaseUrl = getGerritBaseUrl();

                return actionableOwner.getActions(GitRemoteHeadRefAction.class).stream()
                    .filter(action -> action.getRemote().equals(getRemote()))
                    .map(
                        action ->
                            new ObjectMetadataAction(
                                change.getName(),
                                change.getId(),
                                String.format("%s%d", gerritBaseUrl, change.getChangeNumber())))
                    .collect(Collectors.toList());
              } else {
                return Collections.emptyList();
              }
            },
            new GitSCMSourceContext<>(null, SCMHeadObserver.none()).withTraits(getTraits()),
            listener,
            false);

    final ImmutableList.Builder<Action> resultBuilder = new ImmutableList.Builder<>();
    resultBuilder.addAll(super.retrieveActions(head, event, listener));
    resultBuilder.addAll(actions);
    return resultBuilder.build();
  }

  private String getGerritBaseUrl() throws IOException {
    try {
      return getGerritURI().getApiURI().toASCIIString();
    } catch (URISyntaxException e) {
      throw new IOException(e);
    }
  }

  private boolean processBranchRequest(
      final Repository repository,
      final RevWalk walk,
      GitSCMSourceRequest request,
      final Map.Entry<String, ObjectId> ref,
      final TaskListener listener)
      throws IOException, InterruptedException {
    final String branchName =
        StringUtils.removeStart(
            StringUtils.removeStart(ref.getKey(), Constants.R_HEADS), R_CHANGES);
    return (request.process(
        new SCMHead(branchName),
        new SCMSourceRequest.IntermediateLambda<ObjectId>() {
          @Nullable
          @Override
          public ObjectId create() throws IOException, InterruptedException {
            listener.getLogger().println("  Checking branch " + branchName);
            return ref.getValue();
          }
        },
        new SCMSourceRequest.ProbeLambda<SCMHead, ObjectId>() {
          @Nonnull
          @Override
          public SCMSourceCriteria.Probe create(
              @Nonnull SCMHead head, @Nullable ObjectId revisionInfo)
              throws IOException, InterruptedException {
            RevCommit commit = walk.parseCommit(revisionInfo);
            final long lastModified = TimeUnit.SECONDS.toMillis(commit.getCommitTime());
            final RevTree tree = commit.getTree();
            return new SCMProbe() {
              @Override
              public void close() throws IOException {
                // no-op
              }

              @Override
              public String name() {
                return branchName;
              }

              @Override
              public long lastModified() {
                return lastModified;
              }

              @Override
              @Nonnull
              @SuppressFBWarnings(
                  value = "NP_LOAD_OF_KNOWN_NULL_VALUE",
                  justification =
                      "TreeWalk.forPath can return null, compiler "
                          + "generated code for try with resources handles it")
              public SCMProbeStat stat(@Nonnull String path) throws IOException {
                try (TreeWalk tw = TreeWalk.forPath(repository, path, tree)) {
                  if (tw == null) {
                    return SCMProbeStat.fromType(SCMFile.Type.NONEXISTENT);
                  }
                  FileMode fileMode = tw.getFileMode(0);
                  if (fileMode == FileMode.MISSING) {
                    return SCMProbeStat.fromType(SCMFile.Type.NONEXISTENT);
                  }
                  if (fileMode == FileMode.EXECUTABLE_FILE) {
                    return SCMProbeStat.fromType(SCMFile.Type.REGULAR_FILE);
                  }
                  if (fileMode == FileMode.REGULAR_FILE) {
                    return SCMProbeStat.fromType(SCMFile.Type.REGULAR_FILE);
                  }
                  if (fileMode == FileMode.SYMLINK) {
                    return SCMProbeStat.fromType(SCMFile.Type.LINK);
                  }
                  if (fileMode == FileMode.TREE) {
                    return SCMProbeStat.fromType(SCMFile.Type.DIRECTORY);
                  }
                  return SCMProbeStat.fromType(SCMFile.Type.OTHER);
                }
              }
            };
          }
        },
        new SCMSourceRequest.LazyRevisionLambda<SCMHead, SCMRevision, ObjectId>() {
          @Nonnull
          @Override
          public SCMRevision create(@Nonnull SCMHead head, @Nullable ObjectId intermediate)
              throws IOException, InterruptedException {
            return new SCMRevisionImpl(head, ref.getValue().name());
          }
        },
        new SCMSourceRequest.Witness() {
          @Override
          public void record(@Nonnull SCMHead head, SCMRevision revision, boolean isMatch) {
            if (isMatch) {
              listener.getLogger().println("    Met criteria");
            } else {
              listener.getLogger().println("    Does not meet criteria");
            }
          }
        }));
  }

  private boolean processChangeRequest(
      final Repository repository,
      final RevWalk walk,
      GitSCMSourceRequest request,
      final Map.Entry<String, ObjectId> ref,
      ChangeInfo changeInfo,
      final TaskListener listener)
      throws IOException, InterruptedException {
    final String branchName = StringUtils.removeStart(ref.getKey(), R_CHANGES);
    boolean succeeded =
        request.process(
            new ChangeSCMHead(ref, branchName),
            new SCMSourceRequest.IntermediateLambda<ObjectId>() {
              @Nullable
              @Override
              public ObjectId create() throws IOException, InterruptedException {
                listener.getLogger().println("  Checking change " + branchName);
                return ref.getValue();
              }
            },
            new SCMSourceRequest.ProbeLambda<ChangeSCMHead, ObjectId>() {
              @Nonnull
              @Override
              public SCMSourceCriteria.Probe create(
                  @Nonnull ChangeSCMHead head, @Nullable ObjectId revisionInfo)
                  throws IOException, InterruptedException {
                RevCommit commit = walk.parseCommit(revisionInfo);
                final long lastModified = TimeUnit.SECONDS.toMillis(commit.getCommitTime());
                final RevTree tree = commit.getTree();
                return new SCMProbe() {
                  @Override
                  public void close() throws IOException {
                    // no-op
                  }

                  @Override
                  public String name() {
                    return branchName;
                  }

                  @Override
                  public long lastModified() {
                    return lastModified;
                  }

                  @Override
                  @Nonnull
                  @SuppressFBWarnings(
                      value = "NP_LOAD_OF_KNOWN_NULL_VALUE",
                      justification =
                          "TreeWalk.forPath can return null, compiler "
                              + "generated code for try with resources handles it")
                  public SCMProbeStat stat(@Nonnull String path) throws IOException {
                    try (TreeWalk tw = TreeWalk.forPath(repository, path, tree)) {
                      if (tw == null) {
                        return SCMProbeStat.fromType(SCMFile.Type.NONEXISTENT);
                      }
                      FileMode fileMode = tw.getFileMode(0);
                      if (fileMode == FileMode.MISSING) {
                        return SCMProbeStat.fromType(SCMFile.Type.NONEXISTENT);
                      }
                      if (fileMode == FileMode.EXECUTABLE_FILE) {
                        return SCMProbeStat.fromType(SCMFile.Type.REGULAR_FILE);
                      }
                      if (fileMode == FileMode.REGULAR_FILE) {
                        return SCMProbeStat.fromType(SCMFile.Type.REGULAR_FILE);
                      }
                      if (fileMode == FileMode.SYMLINK) {
                        return SCMProbeStat.fromType(SCMFile.Type.LINK);
                      }
                      if (fileMode == FileMode.TREE) {
                        return SCMProbeStat.fromType(SCMFile.Type.DIRECTORY);
                      }
                      return SCMProbeStat.fromType(SCMFile.Type.OTHER);
                    }
                  }
                };
              }
            },
            new SCMSourceRequest.LazyRevisionLambda<ChangeSCMHead, SCMRevision, ObjectId>() {
              @Nonnull
              @Override
              public SCMRevision create(
                  @Nonnull ChangeSCMHead head, @Nullable ObjectId intermediate)
                  throws IOException, InterruptedException {
                return new ChangeSCMRevision(head, ref.getValue().toObjectId().name());
              }
            },
            new SCMSourceRequest.Witness<ChangeSCMHead, SCMRevision>() {
              @Override
              public void record(
                  @Nonnull ChangeSCMHead head, SCMRevision revision, boolean isMatch) {
                if (isMatch) {
                  listener.getLogger().println("    Met criteria");
                } else {
                  listener.getLogger().println("    Does not meet criteria");
                }
              }
            });

    if (succeeded) {
      projectOpenChanges.add(changeInfo);
    }
    return succeeded;
  }

  private Map<String, ObjectId> filterRemoteReferences(Map<String, ObjectId> gitRefs) {
    Map<Integer, Integer> changes = new HashMap<>();
    Map<String, ObjectId> filteredRefs = new HashMap<>();

    for (Map.Entry<String, ObjectId> gitRef : gitRefs.entrySet()) {
      if (gitRef.getKey().startsWith(R_CHANGES)) {
        String[] changeParts = gitRef.getKey().split("/");
        try {
          Integer changeNum = Integer.valueOf(changeParts[3]);
          Integer patchSet = Integer.valueOf(changeParts[4]);

          Integer latestPatchSet = changes.get(changeNum);
          if (latestPatchSet == null || latestPatchSet < patchSet) {
            changes.put(changeNum, patchSet);
          }
        } catch (NumberFormatException e) {
          // change or patch-set are not numbers => ignore refs
        }
      } else {
        filteredRefs.put(gitRef.getKey(), gitRef.getValue());
      }
    }

    for (Map.Entry<Integer, Integer> change : changes.entrySet()) {
      Integer changeNum = change.getKey();
      Integer patchSet = change.getValue();
      String refName =
          String.format("%s%02d/%d/%d", R_CHANGES, changeNum % 100, changeNum, patchSet);
      ObjectId changeObjectId = gitRefs.get(refName);
      filteredRefs.put(refName, changeObjectId);
    }

    return filteredRefs;
  }

  @Nonnull
  @SuppressWarnings("deprecation")
  protected <T, C extends GitSCMSourceContext<C, R>, R extends GitSCMSourceRequest> T doRetrieve(
      Retriever<T> retriever, @Nonnull C context, @Nonnull TaskListener listener, boolean prune)
      throws IOException, InterruptedException {

    String cacheEntry = getCacheEntry();
    Lock cacheLock = getCacheLock(cacheEntry);
    cacheLock.lock();
    try {
      File cacheDir = getCacheDir(cacheEntry);
      Git git = Git.with(listener, new EnvVars(EnvVars.masterEnvVars)).in(cacheDir);
      GitTool tool = resolveGitTool(context.gitTool());
      if (tool != null) {
        git.using(tool.getGitExe());
      }

      GitClient client = git.getClient();
      client.addDefaultCredentials(getCredentials());
      if (!client.hasGitRepo()) {
        listener.getLogger().println("Creating git repository in " + cacheDir);
        client.init();
      }
      String remoteName = context.remoteName();
      listener.getLogger().println("Setting " + remoteName + " to " + getRemote());
      client.setRemoteUrl(remoteName, getRemote());
      listener
          .getLogger()
          .println((prune ? "Fetching & pruning " : "Fetching ") + remoteName + "...");

      FetchCommand fetch = client.fetch_();
      if (prune) {
        fetch = fetch.prune();
      }
      URIish remoteURI = null;
      try {
        remoteURI = new URIish(remoteName);
      } catch (URISyntaxException ex) {
        listener.getLogger().println("URI syntax exception for '" + remoteName + "' " + ex);
      }

      GerritURI gerritURI = getGerritURI();
      GerritApi gerritApi = createGerritApi(listener, gerritURI);
      if (gerritApi == null) {
        throw new IllegalStateException("Invalid gerrit configuration");
      }

      Changes.QueryRequest changeQuery = getOpenChanges(gerritApi, gerritURI.getProject());

      fetch.from(remoteURI, context.asRefSpecs()).execute();
      return retriever.run(client, context, remoteName, changeQuery);
    } finally {
      cacheLock.unlock();
    }
  }

  private synchronized ProjectOpenChanges getProjectOpenChanges() throws IOException {
    if (projectOpenChanges == null) {
      GerritURI gerritURI = getGerritURI();
      GerritApi gerritApi = createGerritApi(FakeTaskListener.INSTANCE, gerritURI);
      projectOpenChanges = new ProjectOpenChanges(gerritApi);
    }

    return projectOpenChanges;
  }

  private GerritApi createGerritApi(@Nonnull TaskListener listener, GerritURI remoteUri)
      throws IOException {
    try {
      UsernamePasswordCredentialsProvider.UsernamePassword credentials =
          new UsernamePasswordCredentialsProvider(getCredentials())
              .getUsernamePassword(remoteUri.getRemoteURI());

      return new GerritApiBuilder()
          .logger(listener.getLogger())
          .gerritApiUrl(remoteUri.getApiURI())
          .insecureHttps(getInsecureHttps())
          .credentials(credentials.username, credentials.password)
          .build();
    } catch (URISyntaxException e) {
      throw new IOException(e);
    }
  }

  private Changes.QueryRequest getOpenChanges(GerritApi gerritApi, String project)
      throws UnsupportedEncodingException {
    String query = "p:" + project + " status:open";
    return gerritApi.changes().query(URLEncoder.encode(query, StandardCharsets.UTF_8.name()));
  }

  public GerritURI getGerritURI() throws IOException {
    try {
      return new GerritURI(new URIish(getRemote()));
    } catch (URISyntaxException e) {
      throw new IOException(e);
    }
  }

  @Override
  protected boolean isCategoryEnabled(@Nonnull SCMHeadCategory category) {
    return true;
  }
}

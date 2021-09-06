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
import com.google.gerrit.extensions.api.changes.Changes.QueryRequest;
import com.google.gerrit.extensions.client.ListChangesOption;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.plugins.checks.api.PendingChecksInfo;
import com.google.gerrit.plugins.checks.client.GerritChecksApi;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.EnvVars;
import hudson.model.Action;
import hudson.model.Actionable;
import hudson.model.TaskListener;
import hudson.plugins.git.Branch;
import hudson.plugins.git.GitTool;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import jenkins.plugins.git.AbstractGitSCMSource;
import jenkins.plugins.git.AbstractGitSCMSource.SCMRevisionImpl;
import jenkins.plugins.git.GitRemoteHeadRefAction;
import jenkins.plugins.git.GitSCMBuilder;
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
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.jenkinsci.plugins.gitclient.FetchCommand;
import org.jenkinsci.plugins.gitclient.Git;
import org.jenkinsci.plugins.gitclient.GitClient;

public abstract class AbstractGerritSCMSource extends AbstractGitSCMSource {
  public static final String R_CHANGES = "refs/changes/";
  public static final String OPEN_CHANGES_FILTER =
      System.getProperty("gerrit.open.changes.filter", "-age:24w");
  private static final String ORIGIN_REF_PREFIX = "origin/";
  private static final Pattern changePattern = Pattern.compile("(\\d\\d)/(\\d+)/(\\d+)");
  private transient ProjectChanges projectChanges;

  public interface Retriever<T> {
    T run(
        GitClient client,
        GerritSCMSourceContext context,
        String remoteName,
        Changes.QueryRequest changeQuery)
        throws IOException, InterruptedException;
  }

  public AbstractGerritSCMSource() {}

  @SuppressFBWarnings(value = "NP_BOOLEAN_RETURN_NULL", justification = "Overridden")
  public Boolean getInsecureHttps() {
    return null;
  }

  /** Return the Gerrit change information associated with a change number */
  public Optional<ChangeInfo> getChangeInfo(int changeNum) throws IOException {
    return getProjectChanges().get(changeNum);
  }

  /** {@inheritDoc} */
  @CheckForNull
  @Override
  protected SCMRevision retrieve(@NonNull final SCMHead head, @NonNull TaskListener listener)
      throws IOException, InterruptedException {
    return doRetrieve(
        head,
        new Retriever<SCMRevision>() {
          @Override
          public SCMRevision run(
              GitClient client,
              GerritSCMSourceContext context,
              String remoteName,
              Changes.QueryRequest changeQuery)
              throws IOException, InterruptedException {

            if (head instanceof ChangeSCMHead) {
              return new SCMRevisionImpl(head, ((ChangeSCMHead) head).getRev());
            }

            for (Branch b : client.getRemoteBranches()) {
              String branchName = StringUtils.removeStart(b.getName(), remoteName + "/");
              if (branchName.equals(head.getName())) {
                return new SCMRevisionImpl(head, b.getSHA1String());
              }
            }
            return null;
          }
        },
        new GerritSCMSourceContext(null, SCMHeadObserver.none()).withTraits(getTraits()),
        listener,
        false);
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
        null,
        new Retriever<Object>() {
          @SuppressWarnings("deprecation")
          @Override
          public Object run(
              GitClient client,
              GerritSCMSourceContext context,
              String remoteName,
              Changes.QueryRequest changeQuery)
              throws IOException, InterruptedException {
            final Repository repository = client.getRepository();
            try (RevWalk walk = new RevWalk(repository);
                GerritSCMSourceRequest request =
                    context.newRequest(AbstractGerritSCMSource.this, listener)) {
              if (context.wantBranches()) {
                discoverBranches(
                    repository,
                    walk,
                    context,
                    request,
                    client
                        .getRemoteBranches()
                        .stream()
                        .collect(
                            Collectors.toMap(
                                (Branch branch) -> branch.getName(),
                                (Branch branch) -> branch.getSHA1())));
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
              GerritSCMSourceContext context,
              GerritSCMSourceRequest request,
              final Map<String, ObjectId> remoteReferences)
              throws IOException, InterruptedException {

            listener.getLogger().println("Checking " + remoteReferences.size() + " branches ...");
            Map<String, ObjectId> filteredRefs = filterRemoteReferences(remoteReferences);
            listener.getLogger().println("Filtered " + filteredRefs.size() + " branches ...");
            walk.setRetainBody(false);
            int branchesCount = 0;
            int changesCount = 0;

            for (final Map.Entry<String, ObjectId> ref : filteredRefs.entrySet()) {
              String refKey = ref.getKey();
              if (!refKey.startsWith(Constants.R_HEADS) && !refKey.startsWith(R_CHANGES)) {
                continue;
              }

              if (refKey.startsWith(R_CHANGES)) {
                try {
                  if (processChangeRequest(repository, walk, request, ref, listener)) {
                    listener
                        .getLogger()
                        .format("Processed %d changes (query complete)%n", changesCount);
                    return;
                  }
                } catch (Exception e) {
                  listener.getLogger().format("Unable to process %s: %s", refKey, e.toString());
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
          }
        },
        new GerritSCMSourceContext(criteria, observer).withTraits(getTraits()),
        listener,
        true);
  }

  /** {@inheritDoc} */
  @Nonnull
  @Override
  protected List<Action> retrieveActions(
      @CheckForNull SCMSourceEvent event, @Nonnull TaskListener listener)
      throws IOException, InterruptedException {
    return doRetrieve(
        null,
        new Retriever<List<Action>>() {
          @Override
          public List<Action> run(
              GitClient client,
              GerritSCMSourceContext context,
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
        new GerritSCMSourceContext(null, SCMHeadObserver.none()).withTraits(getTraits()),
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
            head,
            (GitClient client,
                GerritSCMSourceContext context,
                String remoteName,
                Changes.QueryRequest changeQuery) -> {
              SCMSourceOwner owner = getOwner();
              if (owner instanceof Actionable && head instanceof ChangeSCMHead) {
                final Actionable actionableOwner = (Actionable) owner;
                final ChangeSCMHead change = (ChangeSCMHead) head;
                String gerritBaseUrl = getGerritBaseUrl();

                return actionableOwner
                    .getActions(GitRemoteHeadRefAction.class)
                    .stream()
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
            new GerritSCMSourceContext(null, SCMHeadObserver.none()).withTraits(getTraits()),
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
      GerritSCMSourceRequest request,
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
              listener.getLogger().println(head.getName() + " meets the criteria");
            }
          }
        }));
  }

  private boolean processChangeRequest(
      final Repository repository,
      final RevWalk walk,
      GerritSCMSourceRequest request,
      final Map.Entry<String, ObjectId> ref,
      final TaskListener listener)
      throws IOException, InterruptedException {
    final String branchName = StringUtils.removeStart(ref.getKey(), R_CHANGES);
    Set<String> pendingCheckerUuids = getPendingCheckerUuids(request, ref);
    boolean succeeded =
        request.process(
            new ChangeSCMHead(ref, branchName, pendingCheckerUuids),
            new SCMSourceRequest.IntermediateLambda<ObjectId>() {
              @Nullable
              @Override
              public ObjectId create() throws IOException, InterruptedException {
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

    return succeeded;
  }

  private Set<String> getPendingCheckerUuids(
      GerritSCMSourceRequest request, final Map.Entry<String, ObjectId> ref) {
    String[] refParts = ref.getKey().split("/");
    HashSet<PendingChecksInfo> pendingChecksInfos =
        request
            .getPatchsetWithPendingChecks()
            .get(
                String.format(
                    "%s/%s", refParts[refParts.length - 2], refParts[refParts.length - 1]));
    if (pendingChecksInfos != null) {
      Set<String> pendingCheckerUuids = new HashSet<String>();
      for (PendingChecksInfo pendingChecksInfo : pendingChecksInfos) {
        if (pendingChecksInfo.pendingChecks != null) {
          pendingCheckerUuids.addAll(pendingChecksInfo.pendingChecks.keySet());
        }
      }
      return pendingCheckerUuids;
    }

    return Collections.<String>emptySet();
  }

  private Map<String, ObjectId> filterRemoteReferences(Map<String, ObjectId> gitRefs) {
    Map<Integer, Integer> changes = new HashMap<>();
    Map<String, ObjectId> filteredRefs = new HashMap<>();

    for (Map.Entry<String, ObjectId> gitRef : gitRefs.entrySet()) {
      Matcher changeMatcher = getChangeRefMatcher(gitRef.getKey());
      if (changeMatcher.matches()) {
        try {
          Integer changeNum = Integer.parseInt(changeMatcher.group(2));
          Integer patchSet = Integer.parseInt(changeMatcher.group(3));

          Integer latestPatchSet = changes.get(changeNum);
          if (latestPatchSet == null || latestPatchSet < patchSet) {
            changes.put(changeNum, patchSet);
          }
        } catch (NumberFormatException e) {
          // change or patch-set are not numbers => ignore refs
        }
      } else {
        filteredRefs.put(gitRef.getKey().replace("origin", "refs/heads"), gitRef.getValue());
      }
    }

    for (Map.Entry<Integer, Integer> change : changes.entrySet()) {
      Integer changeNum = change.getKey();
      Integer patchSet = change.getValue();
      String refName =
          String.format("%s%02d/%d/%d", R_CHANGES, changeNum % 100, changeNum, patchSet);
      String originName = String.format("origin/%02d/%d/%d", changeNum % 100, changeNum, patchSet);
      ObjectId changeObjectId = gitRefs.get(originName);
      filteredRefs.put(refName, changeObjectId);
    }

    return filteredRefs;
  }

  private static Matcher getChangeRefMatcher(String gitRef) {
    String changeRef =
        gitRef.startsWith(ORIGIN_REF_PREFIX)
            ? gitRef.substring(ORIGIN_REF_PREFIX.length())
            : gitRef;
    return changePattern.matcher(changeRef);
  }

  @Nonnull
  @SuppressWarnings("deprecation")
  protected <T, C extends GerritSCMSourceContext, R extends GerritSCMSourceRequest> T doRetrieve(
      SCMHead head,
      Retriever<T> retriever,
      @Nonnull C context,
      @Nonnull TaskListener listener,
      boolean prune)
      throws IOException, InterruptedException {
    boolean doPrune = prune && head == null;
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
          .println((doPrune ? "Fetching & pruning " : "Fetching ") + remoteName + "...");

      FetchCommand fetch = client.fetch_();
      if (doPrune) {
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

      Changes.QueryRequest changeQuery =
          getOpenChanges(gerritApi, gerritURI.getProject(), context.changesQueryFilter());
      listener
          .getLogger()
          .println(
              "Looking for open changes with query '"
                  + URLDecoder.decode(changeQuery.getQuery(), StandardCharsets.UTF_8.name())
                  + "' ...");

      List<RefSpec> fetchRefSpecs;
      try {
        if (head == null) {
          Stream<RefSpec> refSpecs =
              context
                  .asRefSpecs()
                  .stream()
                  .filter((RefSpec refSpec) -> !refSpec.getSource().contains(R_CHANGES));
          Stream<RefSpec> openChangesRefSpecs = changeQueryToRefSpecs(changeQuery);
          fetchRefSpecs = Stream.concat(refSpecs, openChangesRefSpecs).collect(Collectors.toList());
        } else {
          String headName = head.getName();
          String refSpecPrefix = head instanceof ChangeSCMHead ? R_CHANGES : "+refs/heads/";
          fetchRefSpecs =
              Arrays.asList(
                  new RefSpec(refSpecPrefix + headName + ":refs/remotes/origin/" + headName));
        }
      } catch (RestApiException e) {
        throw new IOException("Unable to query Gerrit open changes", e);
      }

      if (!fetchRefSpecs.isEmpty()) {
        fetch.from(remoteURI, fetchRefSpecs).execute();
      }

      return retriever.run(client, context, remoteName, changeQuery);
    } finally {
      cacheLock.unlock();
    }
  }

  private Stream<RefSpec> changeQueryToRefSpecs(QueryRequest changeQuery) throws RestApiException {
    return changeQuery
        .get()
        .stream()
        .map(
            (ChangeInfo change) -> {
              String patchRef = change.revisions.entrySet().iterator().next().getValue().ref;
              return new RefSpec(
                  patchRef + ":" + patchRef.replace("refs/changes", "refs/remotes/origin"));
            });
  }

  private ProjectChanges getProjectChanges() throws IOException {
    if (projectChanges == null) {
      GerritURI gerritURI = getGerritURI();
      GerritApi gerritApi = createGerritApi(FakeTaskListener.INSTANCE, gerritURI);
      projectChanges = new ProjectChanges(gerritApi);
    }

    return projectChanges;
  }

  private GerritApiBuilder setupGerritApiBuilder(
      @Nonnull TaskListener listener, GerritURI remoteUri) throws IOException {
    try {
      UsernamePasswordCredentialsProvider.UsernamePassword credentials =
          new UsernamePasswordCredentialsProvider(getCredentials())
              .getUsernamePassword(remoteUri.getRemoteURI());

      return new GerritApiBuilder()
          .logger(listener.getLogger())
          .gerritApiUrl(remoteUri.getApiURI())
          .insecureHttps(getInsecureHttps())
          .credentials(credentials.username, credentials.password);
    } catch (URISyntaxException e) {
      throw new IOException(e);
    }
  }

  private GerritApi createGerritApi(@Nonnull TaskListener listener, GerritURI remoteUri)
      throws IOException {
    return setupGerritApiBuilder(listener, remoteUri).build();
  }

  protected GerritChecksApi createGerritChecksApi(
      @Nonnull TaskListener listener, GerritURI remoteUri) throws IOException {
    return setupGerritApiBuilder(listener, remoteUri).buildChecksApi();
  }

  private Changes.QueryRequest getOpenChanges(
      GerritApi gerritApi, String project, String changeQueryFilter)
      throws UnsupportedEncodingException {
    String query =
        "p:"
            + project
            + " status:open "
            + OPEN_CHANGES_FILTER
            + (changeQueryFilter == null ? "" : " " + changeQueryFilter);
    return gerritApi
        .changes()
        .query(URLEncoder.encode(query, StandardCharsets.UTF_8.name()))
        .withOption(ListChangesOption.CURRENT_REVISION);
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

  /** {@inheritDoc} */
  @Override
  protected void decorate(GitSCMBuilder<?> builder) {
    if (!getChangeRefMatcher(builder.head().getName()).matches()) {
      return;
    }

    builder
        .withoutRefSpecs()
        .withRefSpec(
            "refs/changes/"
                + builder.head().getName()
                + ":refs/remotes/"
                + builder.remoteName()
                + "/"
                + builder.head().getName());
  }
}

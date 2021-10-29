package jenkins.plugins.gerrit;

import com.google.gerrit.extensions.api.GerritApi;
import com.google.gerrit.extensions.api.projects.Projects;
import com.google.gerrit.extensions.common.ProjectInfo;
import com.google.gerrit.extensions.restapi.RestApiException;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public class PagedCodeProjectsRequest implements Iterable<ProjectInfo> {

  private static final int CHUNK_DEFAULT_MAX_SIZE = 20;

  private final GerritApi gerritApi;
  private final int chunkMaxSize;

  public PagedCodeProjectsRequest(GerritApi gerritApi) {
    this(gerritApi, CHUNK_DEFAULT_MAX_SIZE);
  }

  public PagedCodeProjectsRequest(GerritApi gerritApi, int chunkMaxSize) {
    this.gerritApi = gerritApi;
    this.chunkMaxSize = chunkMaxSize;
  }

  @Override
  public Iterator<ProjectInfo> iterator() {
    return new ProjectIterator(gerritApi, chunkMaxSize);
  }

  private static class ProjectIterator implements Iterator<ProjectInfo> {

    private final int chunkMaxSize;
    private final Projects.ListRequest listRequest;
    private Iterator<ProjectInfo> chunkIterator;
    private boolean moreChunkToFetch = true;

    public ProjectIterator(GerritApi gerritApi, int chunkMaxSize) {
      this.chunkMaxSize = chunkMaxSize;
      listRequest =
          gerritApi
              .projects()
              .list()
              // Setting the type even if it is not used by the implementation ...
              .withType(Projects.ListRequest.FilterType.CODE)
              .withStart(-1)
              .withLimit(chunkMaxSize);
    }

    @Override
    public boolean hasNext() {
      return getChunkIterator().hasNext();
    }

    @Override
    public ProjectInfo next() {
      return getChunkIterator().next();
    }

    private Iterator<ProjectInfo> getChunkIterator() {
      if (chunkIterator != null && chunkIterator.hasNext()) {
        return chunkIterator;
      }
      if (!moreChunkToFetch) {
        return Collections.emptyIterator();
      }

      List<ProjectInfo> nextChunk;
      try {
        nextChunk = listRequest.withStart(chunkMaxSize * (listRequest.getStart() + 1)).get();
      } catch (RestApiException e) {
        throw new IllegalStateException(e);
      }
      moreChunkToFetch = nextChunk.size() >= chunkMaxSize;
      chunkIterator = nextChunk.iterator();
      return chunkIterator;
    }
  }
}

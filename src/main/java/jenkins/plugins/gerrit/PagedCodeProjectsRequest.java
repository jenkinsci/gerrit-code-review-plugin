package jenkins.plugins.gerrit;

import com.google.gerrit.extensions.api.GerritApi;
import com.google.gerrit.extensions.api.projects.Projects;
import com.google.gerrit.extensions.common.ProjectInfo;
import com.google.gerrit.extensions.restapi.RestApiException;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public class PagedCodeProjectsRequest implements Iterable<ProjectInfo> {

  private final GerritApi gerritApi;

  public PagedCodeProjectsRequest(GerritApi gerritApi) {
    this.gerritApi = gerritApi;
  }

  @Override
  public Iterator<ProjectInfo> iterator() {
    return new ProjectIterator(gerritApi);
  }

  private static class ProjectIterator implements Iterator<ProjectInfo> {

    private static final int CHUNK_MAX_SIZE = 20;

    private final Projects.ListRequest listRequest;
    private Iterator<ProjectInfo> chunkIterator;
    private boolean noMoreChunkToFetch;

    public ProjectIterator(GerritApi gerritApi) {
      listRequest =
          gerritApi
              .projects()
              .list()
              .withType(Projects.ListRequest.FilterType.CODE)
              .withStart(-1)
              .withLimit(CHUNK_MAX_SIZE);
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
      if (noMoreChunkToFetch) {
        return Collections.emptyIterator();
      }

      List<ProjectInfo> nextChunk;
      try {
        nextChunk = listRequest.withStart(listRequest.getStart() + 1).get();
      } catch (RestApiException e) {
        throw new IllegalStateException(e);
      }
      noMoreChunkToFetch = nextChunk.size() < CHUNK_MAX_SIZE;
      chunkIterator = nextChunk.iterator();
      return chunkIterator;
    }
  }
}

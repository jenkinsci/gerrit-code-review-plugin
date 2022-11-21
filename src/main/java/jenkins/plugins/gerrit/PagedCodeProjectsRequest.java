// Copyright (C) 2022 Réda Housni Alaoui <reda-alaoui@hey.com>
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

import com.google.gerrit.extensions.api.GerritApi;
import com.google.gerrit.extensions.api.projects.Projects;
import com.google.gerrit.extensions.common.ProjectInfo;
import com.google.gerrit.extensions.restapi.RestApiException;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * Allows to iterate on a Gerrit instance's projects in a computation-wise and memory-wise
 * performant manner. Computation-wise because it will try to fetch as few projects as possible
 * considering the consumer needs. Memory-wise because it will not hold more in-memory projects than
 * the configured chunk size.
 *
 * <p>Those optimizations should also allow to reduce the pressure on the Gerrit instance resources.
 */
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

    private final GerritApi gerritApi;
    private final int chunkMaxSize;

    private Projects.ListRequest listRequest;
    private Iterator<ProjectInfo> chunkIterator;
    private boolean moreChunkToFetch = true;

    public ProjectIterator(GerritApi gerritApi, int chunkMaxSize) {
      this.gerritApi = gerritApi;
      this.chunkMaxSize = chunkMaxSize;
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
        if (listRequest != null) {
          listRequest.withStart(listRequest.getStart() + chunkMaxSize);
        } else {
          listRequest = createListRequest();
        }
        nextChunk = listRequest.get();
      } catch (RestApiException e) {
        throw new IllegalStateException(e);
      }
      moreChunkToFetch = nextChunk.size() >= chunkMaxSize;
      chunkIterator = nextChunk.iterator();
      return chunkIterator;
    }

    private Projects.ListRequest createListRequest() {
      return gerritApi
          .projects()
          .list()
          // Setting the type even if it is not used by the implementation ...
          .withType(Projects.ListRequest.FilterType.CODE)
          .withLimit(chunkMaxSize);
    }
  }
}

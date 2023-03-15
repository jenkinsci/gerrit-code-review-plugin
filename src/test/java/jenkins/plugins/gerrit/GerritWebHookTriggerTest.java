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

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cloudbees.hudson.plugins.folder.Folder;
import com.google.common.net.HttpHeaders;
import com.google.common.net.MediaType;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import hudson.model.Result;
import javax.servlet.http.HttpServletResponse;

import jenkins.branch.Branch;
import jenkins.branch.BranchSource;
import jenkins.plugins.git.GitSampleRepoRule;
import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.IOException;

public class GerritWebHookTriggerTest {
    private static final String API_KEY = "gerrit-api-key";

    @Rule public JenkinsRule j = new JenkinsRule();
  @Rule public GitSampleRepoRule g = new GitSampleRepoRule();

  String repoName = "somerepo";
  String gerritEventBody =
      String.format("{\"project\":{\"name\":\"%s\"}, \"type\":\"ref-updated\"}", repoName);
  private String projectName = "someproject";

  @Test
  public void gerritWebHookShouldTriggerMultiBranchPipelineProjectWithoutFolder() throws Exception {
    WorkflowMultiBranchProject mp = createMultiBranchPipelineProject(new BranchSource(getGerritSCMSource()));

    assertEquals(HttpServletResponse.SC_OK, httpStatusOfPostGerritEventBodyToWebhookURI());
    j.waitUntilNoActivity();

    assertEquals(Result.SUCCESS, mp.getIndexing().getResult());
  }

  @Test
  public void gerritWebHookShouldTriggerMultiBranchPipelineProjectWithFolder() throws Exception {
    WorkflowMultiBranchProject mp = createMultiBranchPipelineProject("folder", new BranchSource(getGerritSCMSource()));

    assertEquals(HttpServletResponse.SC_OK, httpStatusOfPostGerritEventBodyToWebhookURI());
    j.waitUntilNoActivity();

    assertEquals(Result.SUCCESS, mp.getIndexing().getResult());
  }



  @Test
  public void gerritWebHookShouldTriggerMultiBranchPipelineProjectWithApiKey() throws Exception {
    WorkflowMultiBranchProject mp = createMultiBranchPipelineProject(new BranchSource(getGerritSCMSource(API_KEY)));

    assertEquals(HttpServletResponse.SC_OK, httpStatusOfPostGerritEventBodyToWebhookURI(API_KEY));
    j.waitUntilNoActivity();

    assertEquals(Result.SUCCESS, mp.getIndexing().getResult());
  }

  @Test
  public void gerritWebHookWithoutApiKeyShouldNotTriggerMultiBranchPipelineProjectWithApiKey() throws Exception {
    createMultiBranchPipelineProject(new BranchSource(getGerritSCMSource(API_KEY)));
    assertEquals(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, httpStatusOfPostGerritEventBodyToWebhookURI());
  }

  @Test
  public void gerritWebHookWithInvalidApiKeyShouldNotTriggerMultiBranchPipelineProjectWithFolderWithApiKey() throws Exception {
    createMultiBranchPipelineProject(new BranchSource(getGerritSCMSource(API_KEY)));
    assertEquals(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, httpStatusOfPostGerritEventBodyToWebhookURI("bogus" + API_KEY));
  }

    private WorkflowMultiBranchProject createMultiBranchPipelineProject(BranchSource branchSource) throws IOException {
        WorkflowMultiBranchProject mp = j.jenkins.createProject(WorkflowMultiBranchProject.class, projectName);
        mp.getSourcesList().add(branchSource);
        return mp;
    }

    private WorkflowMultiBranchProject createMultiBranchPipelineProject(String folderName, BranchSource branchSource) throws IOException {
        Folder f = j.jenkins.createProject(Folder.class, folderName);
        WorkflowMultiBranchProject mp = f.createProject(WorkflowMultiBranchProject.class, projectName);
        mp.getSourcesList().add(branchSource);
        return mp;
    }

 private int httpStatusOfPostGerritEventBodyToWebhookURI() throws UnirestException {
    return httpStatusOfPostGerritEventBodyToWebhookURI(null);
 }
  private int httpStatusOfPostGerritEventBodyToWebhookURI(String apiKey) throws UnirestException {
    return Unirest.post(gerritPluginWebhookURI())
        .header(HttpHeaders.CONTENT_TYPE, MediaType.JSON_UTF_8.toString())
            .queryString("apiKey", apiKey)
        .body(gerritEventBody)
        .asString()
        .getStatus();
  }

  private GerritSCMSource getGerritSCMSource() {
    return getGerritSCMSource(null);
  }

  private GerritSCMSource getGerritSCMSource(String apiKey) {
    GerritSCMSource mockSource = mock(GerritSCMSource.class);
    when(mockSource.getRemote()).thenReturn("somerepo");
    when(mockSource.getApiKey()).thenReturn(apiKey);
    return mockSource;
  }

  private String gerritPluginWebhookURI() {
    return j.jenkins.getRootUrl() + "gerrit-webhook/";
  }
}

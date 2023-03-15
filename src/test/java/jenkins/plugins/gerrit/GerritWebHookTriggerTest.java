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
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cloudbees.hudson.plugins.folder.Folder;
import com.google.common.net.HttpHeaders;
import com.google.common.net.MediaType;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import com.mashape.unirest.request.HttpRequestWithBody;
import hudson.model.Result;
import hudson.util.Secret;
import hudson.util.TestSecret;
import java.io.IOException;
import javax.servlet.http.HttpServletResponse;
import jenkins.branch.BranchSource;
import jenkins.plugins.git.GitSampleRepoRule;
import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

public class GerritWebHookTriggerTest {
  private Secret apiKeySecret = TestSecret.newTestSecret();

  @Rule public JenkinsRule j = new JenkinsRule();
  @Rule public GitSampleRepoRule g = new GitSampleRepoRule();

  String repoName = "somerepo";
  String gerritEventBody =
      String.format("{\"project\":{\"name\":\"%s\"}, \"type\":\"ref-updated\"}", repoName);
  private String projectName = "someproject";

  @Test
  public void gerritWebHookShouldTriggerMultiBranchPipelineProjectWithoutFolder()
      throws Exception { // TODO OLD
    WorkflowMultiBranchProject mp =
        createMultiBranchPipelineProject(new BranchSource(getGerritSCMSource(apiKeySecret)));

    assertEquals(HttpServletResponse.SC_OK, httpStatusOfPostGerritEventBodyToWebhookURI());
    j.waitUntilNoActivity();

    assertEquals(Result.SUCCESS, mp.getIndexing().getResult());
  }

  @Test
  public void gerritWebHookShouldNotTriggerMultiBranchPipelineProjectWithoutApiKeySecret()
      throws Exception {
    WorkflowMultiBranchProject mp =
        createMultiBranchPipelineProject(new BranchSource(getGerritSCMSource()));

    assertEquals(HttpServletResponse.SC_OK, httpStatusOfPostGerritEventBodyToWebhookURI());
    j.waitUntilNoActivity();

    assertNull(mp.getIndexing().getResult());
  }

  @Test
  public void gerritWebHookWithoutApiKeyShouldFail() throws Exception {
    WorkflowMultiBranchProject mp =
        createMultiBranchPipelineProject(new BranchSource(getGerritSCMSource(apiKeySecret)));

    assertEquals(
        HttpServletResponse.SC_BAD_REQUEST,
        httpStatusOfPostGerritEventBodyToWebhookURIWithoutApiKey());
    j.waitUntilNoActivity();
    assertNull(mp.getIndexing().getResult());
  }

  @Test
  public void gerritWebHookWithEmptyApiKeyShouldFail() throws Exception {
    WorkflowMultiBranchProject mp =
        createMultiBranchPipelineProject(new BranchSource(getGerritSCMSource(apiKeySecret)));

    assertEquals(
        HttpServletResponse.SC_BAD_REQUEST,
        httpStatusOfPostGerritEventBodyToWebhookURIWithEmptyApiKey());
    j.waitUntilNoActivity();
    assertNull(mp.getIndexing().getResult());
  }

  @Test
  public void gerritWebHookWithJobNameShouldTriggerMultiBranchPipelineProjectWithoutFolder()
      throws Exception {
    WorkflowMultiBranchProject mp =
        createMultiBranchPipelineProject(new BranchSource(getGerritSCMSource(apiKeySecret)));

    assertEquals(
        HttpServletResponse.SC_OK,
        httpStatusOfPostGerritEventBodyToWebhookURI(TestSecret.TEST_CLEARTEXT_SECRET, projectName));
    j.waitUntilNoActivity();

    assertEquals(Result.SUCCESS, mp.getIndexing().getResult());
  }

  @Test
  public void gerritWebHookShouldTriggerMultiBranchPipelineProjectWithFolder()
      throws Exception { // TODO OLD
    WorkflowMultiBranchProject mp =
        createMultiBranchPipelineProject(
            "folder", new BranchSource(getGerritSCMSource(apiKeySecret)));

    assertEquals(HttpServletResponse.SC_OK, httpStatusOfPostGerritEventBodyToWebhookURI());
    j.waitUntilNoActivity();

    assertEquals(Result.SUCCESS, mp.getIndexing().getResult());
  }

  @Test
  public void gerritWebHookWithJobNameShouldTriggerMultiBranchPipelineProjectWithFolder()
      throws Exception {
    WorkflowMultiBranchProject mp =
        createMultiBranchPipelineProject(
            "folder", new BranchSource(getGerritSCMSource(apiKeySecret)));

    assertEquals(
        HttpServletResponse.SC_OK,
        httpStatusOfPostGerritEventBodyToWebhookURI(TestSecret.TEST_CLEARTEXT_SECRET, projectName));
    j.waitUntilNoActivity();

    assertEquals(Result.SUCCESS, mp.getIndexing().getResult());
  }

  @Test
  public void gerritWebHookWithInvalidProjectNameShouldFailMultiBranchPipelineProject()
      throws Exception {
    WorkflowMultiBranchProject mp =
        createMultiBranchPipelineProject(new BranchSource(getGerritSCMSource(apiKeySecret)));
    assertEquals(
        HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
        httpStatusOfPostGerritEventBodyToWebhookURI(
            TestSecret.TEST_CLEARTEXT_SECRET, "bogus" + projectName));
  }

  @Test
  public void gerritWebHookShouldTriggerMultiBranchPipelineProjectWithApiKey() throws Exception {
    WorkflowMultiBranchProject mp =
        createMultiBranchPipelineProject(new BranchSource(getGerritSCMSource(apiKeySecret)));

    assertEquals(
        HttpServletResponse.SC_OK,
        httpStatusOfPostGerritEventBodyToWebhookURI(TestSecret.TEST_CLEARTEXT_SECRET, null));
    j.waitUntilNoActivity();

    assertEquals(Result.SUCCESS, mp.getIndexing().getResult());
  }

  @Test
  public void gerritWebHookWithoutApiKeyShouldFailOnMultiBranchPipelineProjectWithApiKey()
      throws Exception {
    createMultiBranchPipelineProject(new BranchSource(getGerritSCMSource(apiKeySecret)));
    assertEquals(
        HttpServletResponse.SC_UNAUTHORIZED,
        httpStatusOfPostGerritEventBodyToWebhookURIWithInvalidApiKey());
  }

  @Test
  public void
      gerritWebHookWithInvalidApiKeyShouldFailOnMultiBranchPipelineProjectWithFolderWithApiKey()
          throws Exception {
    createMultiBranchPipelineProject(new BranchSource(getGerritSCMSource(apiKeySecret)));
    assertEquals(
        HttpServletResponse.SC_UNAUTHORIZED,
        httpStatusOfPostGerritEventBodyToWebhookURI("bogus" + apiKeySecret, null));
  }

  private WorkflowMultiBranchProject createMultiBranchPipelineProject(BranchSource branchSource)
      throws IOException {
    WorkflowMultiBranchProject mp =
        j.jenkins.createProject(WorkflowMultiBranchProject.class, projectName);
    mp.getSourcesList().add(branchSource);
    return mp;
  }

  private WorkflowMultiBranchProject createMultiBranchPipelineProject(
      String folderName, BranchSource branchSource) throws IOException {
    Folder f = j.jenkins.createProject(Folder.class, folderName);
    WorkflowMultiBranchProject mp = f.createProject(WorkflowMultiBranchProject.class, projectName);
    mp.getSourcesList().add(branchSource);
    return mp;
  }

  private int httpStatusOfPostGerritEventBodyToWebhookURI() throws UnirestException {
    return httpStatusOfPostGerritEventBodyToWebhookURI(TestSecret.TEST_CLEARTEXT_SECRET, null);
  }

  private int httpStatusOfPostGerritEventBodyToWebhookURIWithInvalidApiKey()
      throws UnirestException {
    return httpStatusOfPostGerritEventBodyToWebhookURI("invalid-api-key", null);
  }

  private int httpStatusOfPostGerritEventBodyToWebhookURIWithEmptyApiKey() throws UnirestException {
    return httpStatusOfPostGerritEventBodyToWebhookURI("", null);
  }

  private int httpStatusOfPostGerritEventBodyToWebhookURIWithoutApiKey() throws UnirestException {
    return httpStatusOfPostGerritEventBodyToWebhookURI(null, null);
  }

  private int httpStatusOfPostGerritEventBodyToWebhookURI(String apiKey, String jobName)
      throws UnirestException {
    HttpRequestWithBody request =
        Unirest.post(gerritPluginWebhookURI())
            .header(HttpHeaders.CONTENT_TYPE, MediaType.JSON_UTF_8.toString());

    if (apiKey != null) {
      request = request.queryString("apiKey", apiKey);
    }

    if (jobName != null) {
      request = request.queryString("jobName", jobName);
    }

    return request.body(gerritEventBody).asString().getStatus();
  }

  private GerritSCMSource getGerritSCMSource() {
    return getGerritSCMSource(null);
  }

  private GerritSCMSource getGerritSCMSource(Secret apiKey) {
    GerritSCMSource mockSource = mock(GerritSCMSource.class);
    when(mockSource.getRemote()).thenReturn("somerepo");
    when(mockSource.getApiKey()).thenReturn(apiKey);
    return mockSource;
  }

  private String gerritPluginWebhookURI() {
    return j.jenkins.getRootUrl() + "gerrit-webhook/";
  }
}

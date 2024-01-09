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
  public void shouldTriggerMultiBranchPipelineProjectWithoutFolderWithApiKeyParameter()
      throws Exception {
    WorkflowMultiBranchProject mp =
        createMultiBranchPipelineProject(new BranchSource(getGerritSCMSource(apiKeySecret)));

    assertEquals(
        HttpServletResponse.SC_OK, httpStatusOfPostGerritEventBodyToWebhookURIWithApiKey());
    j.waitUntilNoActivity();

    assertEquals(Result.SUCCESS, mp.getIndexing().getResult());
  }

  @Test
  public void shouldTriggerMultiBranchPipelineProjectWithoutApiKeySecret() throws Exception {
    WorkflowMultiBranchProject mp =
        createMultiBranchPipelineProject(new BranchSource(getGerritSCMSource()));

    assertEquals(
        HttpServletResponse.SC_OK, httpStatusOfPostGerritEventBodyToWebhookURIWithApiKey());
    j.waitUntilNoActivity();

    assertEquals(Result.SUCCESS, mp.getIndexing().getResult());
  }

  @Test
  public void shouldNotTriggerWithoutApiKeyParameter() throws Exception {
    WorkflowMultiBranchProject mp =
        createMultiBranchPipelineProject(new BranchSource(getGerritSCMSource(apiKeySecret)));

    assertEquals(
        HttpServletResponse.SC_OK, httpStatusOfPostGerritEventBodyToWebhookURIWithoutApiKey());
    j.waitUntilNoActivity();
    assertNull(mp.getIndexing().getResult());
  }

  @Test
  public void shouldNotTriggerWithEmptyApiKeyParameter() throws Exception {
    WorkflowMultiBranchProject mp =
        createMultiBranchPipelineProject(new BranchSource(getGerritSCMSource(apiKeySecret)));

    assertEquals(
        HttpServletResponse.SC_OK, httpStatusOfPostGerritEventBodyToWebhookURIWithEmptyApiKey());
    j.waitUntilNoActivity();
    assertNull(mp.getIndexing().getResult());
  }

  @Test
  public void
      shouldTriggerMultiBranchPipelineProjectWithoutFolderAndWithApiKeyParameterAndJobNameParameter()
          throws Exception {
    WorkflowMultiBranchProject mp =
        createMultiBranchPipelineProject(new BranchSource(getGerritSCMSource(apiKeySecret)));

    assertEquals(
        HttpServletResponse.SC_OK,
        httpStatusOfPostGerritEventBodyToWebhookURIWithApiKey(
            TestSecret.TEST_CLEARTEXT_SECRET, projectName));
    j.waitUntilNoActivity();

    assertEquals(Result.SUCCESS, mp.getIndexing().getResult());
  }

  @Test
  public void shouldTriggerMultiBranchPipelineProjectWithFolderWithApiKeyParameter()
      throws Exception {
    WorkflowMultiBranchProject mp =
        createMultiBranchPipelineProject(
            "folder", new BranchSource(getGerritSCMSource(apiKeySecret)));

    assertEquals(
        HttpServletResponse.SC_OK, httpStatusOfPostGerritEventBodyToWebhookURIWithApiKey());
    j.waitUntilNoActivity();

    assertEquals(Result.SUCCESS, mp.getIndexing().getResult());
  }

  @Test
  public void shouldTriggerMultiBranchPipelineProjectWithFolderAndWithJobNameParameter()
      throws Exception {
    WorkflowMultiBranchProject mp =
        createMultiBranchPipelineProject(
            "folder", new BranchSource(getGerritSCMSource(apiKeySecret)));

    assertEquals(
        HttpServletResponse.SC_OK,
        httpStatusOfPostGerritEventBodyToWebhookURIWithApiKey(
            TestSecret.TEST_CLEARTEXT_SECRET, projectName));
    j.waitUntilNoActivity();

    assertEquals(Result.SUCCESS, mp.getIndexing().getResult());
  }

  @Test
  public void shouldNotTriggerMultiBranchPipelineProjectWithInvalidJobNameParameter()
      throws Exception {
    WorkflowMultiBranchProject mp =
        createMultiBranchPipelineProject(new BranchSource(getGerritSCMSource(apiKeySecret)));
    assertEquals(
        HttpServletResponse.SC_OK,
        httpStatusOfPostGerritEventBodyToWebhookURIWithApiKey(null, "bogus" + projectName));
    assertNull(mp.getIndexing().getResult());
  }

  @Test
  public void shouldNotTriggerMultiBranchPipelineProjectWithoutApiKeyParameter() throws Exception {
    WorkflowMultiBranchProject mp =
        createMultiBranchPipelineProject(new BranchSource(getGerritSCMSource(apiKeySecret)));

    assertEquals(
        HttpServletResponse.SC_OK, httpStatusOfPostGerritEventBodyToWebhookURIWithInvalidApiKey());

    assertNull(mp.getIndexing().getResult());
  }

  @Test
  public void shouldNotTriggerMultiBranchPipelineProjectWithFolderAndWithInvalidApiKeyParameter()
      throws Exception {
    WorkflowMultiBranchProject mp =
        createMultiBranchPipelineProject(new BranchSource(getGerritSCMSource(apiKeySecret)));
    assertEquals(
        HttpServletResponse.SC_OK,
        httpStatusOfPostGerritEventBodyToWebhookURIWithApiKey("bogus" + apiKeySecret, null));

    assertNull(mp.getIndexing().getResult());
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

  private int httpStatusOfPostGerritEventBodyToWebhookURIWithApiKey() throws UnirestException {
    return httpStatusOfPostGerritEventBodyToWebhookURIWithApiKey(
        TestSecret.TEST_CLEARTEXT_SECRET, null);
  }

  private int httpStatusOfPostGerritEventBodyToWebhookURIWithInvalidApiKey()
      throws UnirestException {
    return httpStatusOfPostGerritEventBodyToWebhookURIWithApiKey("invalid-api-key", null);
  }

  private int httpStatusOfPostGerritEventBodyToWebhookURIWithEmptyApiKey() throws UnirestException {
    return httpStatusOfPostGerritEventBodyToWebhookURIWithApiKey("", null);
  }

  private int httpStatusOfPostGerritEventBodyToWebhookURIWithoutApiKey() throws UnirestException {
    return httpStatusOfPostGerritEventBodyToWebhookURIWithApiKey(null, null);
  }

  private int httpStatusOfPostGerritEventBodyToWebhookURIWithApiKey(String apiKey, String jobName)
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

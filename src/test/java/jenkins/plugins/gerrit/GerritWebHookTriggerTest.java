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
import jenkins.branch.BranchSource;
import jenkins.plugins.git.GitSampleRepoRule;
import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

public class GerritWebHookTriggerTest {

  @Rule public JenkinsRule j = new JenkinsRule();
  @Rule public GitSampleRepoRule g = new GitSampleRepoRule();

  String repoName = "somerepo";
  String gerritEventBody =
      String.format("{\"project\":{\"name\":\"%s\"}, \"type\":\"ref-updated\"}", repoName);
  private String projectName = "someproject";

  @Test
  public void gerritWebHookShouldTriggerMultiBranchPipelineProjectWithoutFolder() throws Exception {
    WorkflowMultiBranchProject mp =
        j.jenkins.createProject(WorkflowMultiBranchProject.class, projectName);
    mp.getSourcesList().add(new BranchSource(getGerritSCMSource()));

    assertEquals(httpStatusOfPostGerritEventBodyToWebhookURI(), HttpServletResponse.SC_OK);
    j.waitUntilNoActivity();

    assertEquals(Result.SUCCESS, mp.getIndexing().getResult());
  }

  @Test
  public void gerritWebHookShouldTriggerMultiBranchPipelineProjectWithFolder() throws Exception {
    Folder f = j.jenkins.createProject(Folder.class, "folder");
    WorkflowMultiBranchProject mp = f.createProject(WorkflowMultiBranchProject.class, projectName);
    mp.getSourcesList().add(new BranchSource(getGerritSCMSource()));

    assertEquals(httpStatusOfPostGerritEventBodyToWebhookURI(), HttpServletResponse.SC_OK);
    j.waitUntilNoActivity();

    assertEquals(Result.SUCCESS, mp.getIndexing().getResult());
  }

  private int httpStatusOfPostGerritEventBodyToWebhookURI() throws UnirestException {
    return Unirest.post(gerritPluginWebhookURI())
        .header(HttpHeaders.CONTENT_TYPE, MediaType.JSON_UTF_8.toString())
        .body(gerritEventBody)
        .asString()
        .getStatus();
  }

  private GerritSCMSource getGerritSCMSource() {
    GerritSCMSource mockSource = mock(GerritSCMSource.class);
    when(mockSource.getRemote()).thenReturn("somerepo");
    return mockSource;
  }

  private String gerritPluginWebhookURI() {
    return j.jenkins.getRootUrl() + "gerrit-webhook/";
  }
}

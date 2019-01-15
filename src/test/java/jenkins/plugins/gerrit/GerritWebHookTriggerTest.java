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

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import com.cloudbees.hudson.plugins.folder.Folder;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.Unirest;
import jenkins.branch.BranchSource;
import jenkins.plugins.git.GitSampleRepoRule;
import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

public class GerritWebHookTriggerTest {

  @Rule public JenkinsRule j = new JenkinsRule();
  @Rule public GitSampleRepoRule g = new GitSampleRepoRule();

  @Test
  public void gerritWebHookTriggerNoFolderTest() throws Exception {

    // setup multi-branch pipeline job at top level
    WorkflowMultiBranchProject mp = j.jenkins.createProject(WorkflowMultiBranchProject.class, "p");

    // now add a mocked-up GerritSCMSource and fire the gerrit-webhook
    assertNull(mp.getIndexing().getResult());
    GerritSCMSource mockSource = mock(GerritSCMSource.class);
    when(mockSource.getRemote()).thenReturn("somerepo");
    mp.getSourcesList().add(new BranchSource(mockSource));
    String source = "{\"project\":{\"name\":\"somerepo\"}}";
    HttpResponse<String> resp =
        Unirest.post(j.jenkins.getRootUrl() + "gerrit-webhook/")
            .header("Content-Type", "application/json")
            .body(source)
            .asString();

    // make sure it triggered the build
    j.waitUntilNoActivity();
    assertNotNull(mp.getIndexing().getResult());
  }

  @Test
  public void gerritWebHookTriggerFolderTest() throws Exception {

    // setup multi-branch pipeline job in a folder
    Folder f = j.jenkins.createProject(Folder.class, "folder" + j.jenkins.getItems().size());
    WorkflowMultiBranchProject mp = f.createProject(WorkflowMultiBranchProject.class, "p");

    // now add a mocked-up GerritSCMSource and fire the gerrit-webhook
    assertNull(mp.getIndexing().getResult());
    GerritSCMSource mockSource = mock(GerritSCMSource.class);
    when(mockSource.getRemote()).thenReturn("somerepo");
    mp.getSourcesList().add(new BranchSource(mockSource));
    String source = "{\"project\":{\"name\":\"somerepo\"}}";
    HttpResponse<String> resp =
        Unirest.post(j.jenkins.getRootUrl() + "gerrit-webhook/")
            .header("Content-Type", "application/json")
            .body(source)
            .asString();

    // make sure it triggered the build
    j.waitUntilNoActivity();
    assertNotNull(mp.getIndexing().getResult());
  }
}

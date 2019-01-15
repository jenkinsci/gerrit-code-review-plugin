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
import java.util.ArrayList;
import java.util.Arrays;
import jenkins.branch.BranchSource;
import jenkins.plugins.git.GitSCMSource;
import jenkins.plugins.git.GitSampleRepoRule;
import jenkins.plugins.git.traits.BranchDiscoveryTrait;
import jenkins.scm.api.trait.SCMSourceTrait;
import jenkins.scm.impl.trait.WildcardSCMHeadFilterTrait;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

public class GerritWebHookTriggerTest {

  @Rule public JenkinsRule j = new JenkinsRule();
  @Rule public GitSampleRepoRule g = new GitSampleRepoRule();

  @Test
  public void gerritWebHookTriggerNoFolderTest() throws Exception {

    // create test repo
    g.init();
    g.write("Jenkinsfile", "node { echo 'hello' }");
    g.git("add", "Jenkinsfile");
    g.git("commit", "--message=init");

    // setup multi-branch pipeline job at top level with GitSCMSource
    WorkflowMultiBranchProject mp = j.jenkins.createProject(WorkflowMultiBranchProject.class, "p");
    GitSCMSource gs = new GitSCMSource(g.toString());
    gs.setTraits(
        new ArrayList<SCMSourceTrait>(
            Arrays.asList(new BranchDiscoveryTrait(), new WildcardSCMHeadFilterTrait("*", ""))));
    mp.getSourcesList().add(new BranchSource(gs));

    // trigger the initial scan & build
    mp.scheduleBuild2(0).getFuture().get();
    mp.getIndexing().writeWholeLogTo(System.out);
    j.waitUntilNoActivity();
    WorkflowJob p = mp.getItem("master");
    assertNotNull(p);
    WorkflowRun b1 = p.getLastBuild();
    assertNotNull(b1);
    assertEquals(1, b1.getNumber());

    // make a change so the webhook triggers a build
    g.write("somefile", "blahblahblah");
    g.git("add", "somefile");
    g.git("commit", "--message=trigger");

    // now add a mocked-up GerritSCMSource and fire the gerrit-webhook
    GerritSCMSource mockSource = mock(GerritSCMSource.class);
    when(mockSource.getRemote()).thenReturn(g.toString());
    mp.getSourcesList().add(new BranchSource(mockSource));
    String source = "{\"project\":{\"name\":\"" + g.toString() + "\"}}";
    HttpResponse<String> resp =
        Unirest.post(j.jenkins.getRootUrl() + "gerrit-webhook/")
            .header("Content-Type", "application/json")
            .body(source)
            .asString();

    // make sure it triggered the build
    j.waitUntilNoActivity();
    WorkflowRun b2 = p.getLastBuild();
    assertNotNull(b2);
    assertEquals(2, b2.getNumber());
  }

  @Test
  public void gerritWebHookTriggerFolderTest() throws Exception {

    // create test repo
    g.init();
    g.write("Jenkinsfile", "node { echo 'hello' }");
    g.git("add", "Jenkinsfile");
    g.git("commit", "--message=init");

    // setup multi-branch pipeline job in a folder with GitSCMSource
    Folder f = j.jenkins.createProject(Folder.class, "folder" + j.jenkins.getItems().size());
    WorkflowMultiBranchProject mp = f.createProject(WorkflowMultiBranchProject.class, "p");
    GitSCMSource gs = new GitSCMSource(g.toString());
    gs.setTraits(
        new ArrayList<SCMSourceTrait>(
            Arrays.asList(new BranchDiscoveryTrait(), new WildcardSCMHeadFilterTrait("*", ""))));
    mp.getSourcesList().add(new BranchSource(gs));

    // trigger the initial scan & build
    mp.scheduleBuild2(0).getFuture().get();
    mp.getIndexing().writeWholeLogTo(System.out);
    j.waitUntilNoActivity();
    WorkflowJob p = mp.getItem("master");
    assertNotNull(p);
    WorkflowRun b1 = p.getLastBuild();
    assertNotNull(b1);
    assertEquals(1, b1.getNumber());

    // make a change so the webhook triggers a build
    g.write("somefile", "blahblahblah");
    g.git("add", "somefile");
    g.git("commit", "--message=trigger");

    // now add a mocked-up GerritSCMSource and fire the gerrit-webhook
    GerritSCMSource mockSource = mock(GerritSCMSource.class);
    when(mockSource.getRemote()).thenReturn(g.toString());
    mp.getSourcesList().add(new BranchSource(mockSource));
    String source = "{\"project\":{\"name\":\"" + g.toString() + "\"}}";
    HttpResponse<String> resp =
        Unirest.post(j.jenkins.getRootUrl() + "gerrit-webhook/")
            .header("Content-Type", "application/json")
            .body(source)
            .asString();

    // make sure it triggered the build
    j.waitUntilNoActivity();
    WorkflowRun b2 = p.getLastBuild();
    assertNotNull(b2);
    assertEquals(2, b2.getNumber());
  }
}

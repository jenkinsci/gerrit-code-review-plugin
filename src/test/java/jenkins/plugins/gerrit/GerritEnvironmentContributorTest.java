// Copyright (C) 2023 GerritForge Ltd
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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasEntry;

import com.google.gerrit.extensions.client.ChangeKind;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.RevisionInfo;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import jenkins.plugins.gerrit.GerritEnvironmentContributor.ChangeInfoInvisibleAction;
import org.eclipse.jgit.transport.URIish;
import org.junit.Before;
import org.junit.Test;

public class GerritEnvironmentContributorTest {

  public static final String TEST_PROJECT_NAME = "myproject";
  public static final AccountInfo TEST_ACCOUNT_INFO_JOHN_DOE =
      new AccountInfo("John Doe", "john.doe@mycompany.com");
  public static final String TEST_BRANCH = "mybranch";
  public static final String TEST_CHANGE_SUBJECT = "This is a test change";
  public static final String TEST_CHANGE_TOPIC = "test-topic";
  public static final String TEST_CHANGE_ID = "I2ff60b01ab0e2305fdf8739cd884038091f2b888";
  public static final String TEST_CHANGE_ID_TRIPLET =
      TEST_PROJECT_NAME + "~" + TEST_BRANCH + "~" + TEST_CHANGE_ID;
  public static final int TEST_PATCHSET_NUMBER = 2;
  public static final int TEST_CHANGE_NUMBER = 1;
  public static final String TEST_CHANGE_KIND = ChangeKind.TRIVIAL_REBASE.name();
  public static final int TEST_REVERTED_CHANGE_NUMBER = 3;
  public static final String TEST_CHANGE_REF_NAME =
      "refs/changes/"
          + String.format("%02d", TEST_CHANGE_NUMBER)
          + "/"
          + TEST_CHANGE_NUMBER
          + "/"
          + TEST_PATCHSET_NUMBER;
  public static final AccountInfo TEST_ACCOUNT_INFO_MATT_SMITH =
      new AccountInfo("Matt Smith", "matt.smith@mycompany.com");
  public static final String TEST_GERRIT_URL = "http://gerrit.mycompany.com";
  private ChangeInfo changeInfo;
  private GerritURI gerritURI;

  @Before
  public void setup() throws URISyntaxException {
    changeInfo =
        new ChangeInfo() {
          {
            this._number = TEST_CHANGE_NUMBER;
            this.project = TEST_PROJECT_NAME;
            this.owner = TEST_ACCOUNT_INFO_JOHN_DOE;
            this.branch = TEST_BRANCH;
            this.subject = TEST_CHANGE_SUBJECT;
            this.topic = TEST_CHANGE_TOPIC;
            this.changeId = TEST_CHANGE_ID;
            this.id = TEST_CHANGE_ID_TRIPLET;
            this.revisions =
                new HashMap<>() {
                  {
                    put(
                        Integer.toString(TEST_PATCHSET_NUMBER),
                        new RevisionInfo() {
                          {
                            this._number = TEST_PATCHSET_NUMBER;
                            this.uploader = TEST_ACCOUNT_INFO_MATT_SMITH;
                            this.ref = TEST_CHANGE_REF_NAME;
                            this.kind = ChangeKind.TRIVIAL_REBASE;
                          }
                        });
                  }
                };
            this.revertOf = TEST_REVERTED_CHANGE_NUMBER;
          }
        };
    gerritURI = new GerritURI(new URIish(TEST_GERRIT_URL));
  }

  @Test
  public void testBuildEnvironmentForChangeInfo() throws Exception {
    Map<String, String> changeEnvs =
        new ChangeInfoInvisibleAction(Optional.of(changeInfo), TEST_PATCHSET_NUMBER, gerritURI)
            .getChangeEnvs();
    Map<String, String> expectedMap =
        new HashMap<>() {
          {
            put("GERRIT_BRANCH", TEST_BRANCH);
            put("GERRIT_PATCHSET_UPLOADER_NAME", TEST_ACCOUNT_INFO_MATT_SMITH.name);
            put(
                "GERRIT_CHANGE_OWNER",
                TEST_ACCOUNT_INFO_JOHN_DOE.name + " <" + TEST_ACCOUNT_INFO_JOHN_DOE.email + ">");
            put("GERRIT_CHANGE_OWNER_NAME", TEST_ACCOUNT_INFO_JOHN_DOE.name);
            put("GERRIT_CHANGE_OWNER_EMAIL", TEST_ACCOUNT_INFO_JOHN_DOE.email);
            put(
                "GERRIT_PATCHSET_UPLOADER",
                TEST_ACCOUNT_INFO_MATT_SMITH.name
                    + " <"
                    + TEST_ACCOUNT_INFO_MATT_SMITH.email
                    + ">");
            put("GERRIT_PATCHSET_UPLOADER_NAME", TEST_ACCOUNT_INFO_MATT_SMITH.name);
            put("GERRIT_PATCHSET_UPLOADER_EMAIL", TEST_ACCOUNT_INFO_MATT_SMITH.email);
            put("GERRIT_CHANGE_SUBJECT", TEST_CHANGE_SUBJECT);
            put("GERRIT_TOPIC", TEST_CHANGE_TOPIC);
            put("GERRIT_REFNAME", TEST_CHANGE_REF_NAME);
            put("GERRIT_CHANGE_URL", TEST_GERRIT_URL + "/" + TEST_CHANGE_NUMBER);
            put("GERRIT_CHANGE_NUMBER", Integer.toString(TEST_CHANGE_NUMBER));
            put("GERRIT_PATCHSET_KIND", TEST_CHANGE_KIND);
            put("GERRIT_PATCHSET_REVISION", Integer.toString(TEST_PATCHSET_NUMBER));
            put("GERRIT_PATCHSET_NUMBER", Integer.toString(TEST_PATCHSET_NUMBER));
            put("GERRIT_CHANGE_WIP_STATE", "false");
            put("GERRIT_CHANGE_ID", TEST_CHANGE_ID_TRIPLET);
            put("GERRIT_CHANGE_PRIVATE_STATE", "false");
            put("GERRIT_REFSPEC", TEST_CHANGE_REF_NAME);
            put("GERRIT_REVERTED_CHANGE_NUMBER", Integer.toString(TEST_REVERTED_CHANGE_NUMBER));
          }
        };
    assertThat(changeEnvs, equalTo(expectedMap));
  }

  @Test
  public void testBuildEnvironmentForPrivateChange() {
    changeInfo.isPrivate = true;
    assertThat(
        new ChangeInfoInvisibleAction(Optional.of(changeInfo), TEST_PATCHSET_NUMBER, gerritURI)
            .getChangeEnvs(),
        hasEntry("GERRIT_CHANGE_PRIVATE_STATE", "true"));
  }

  @Test
  public void testBuildEnvironmentForWipChange() {
    changeInfo.workInProgress = true;
    assertThat(
        new ChangeInfoInvisibleAction(Optional.of(changeInfo), TEST_PATCHSET_NUMBER, gerritURI)
            .getChangeEnvs(),
        hasEntry("GERRIT_CHANGE_WIP_STATE", "true"));
  }
}

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

import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import org.eclipse.jgit.transport.CredentialItem;
import org.eclipse.jgit.transport.URIish;
import org.jenkinsci.plugins.gitclient.trilead.SmartCredentialsProvider;

public class UsernamePasswordCredentialsProvider extends SmartCredentialsProvider {
  private final StandardUsernameCredentials credentials;

  public static class UsernamePassword {
    public final String username;
    public final String password;

    public UsernamePassword(String username, String password) {
      this.username = username;
      this.password = password;
    }
  }

  public UsernamePasswordCredentialsProvider(StandardUsernameCredentials credentials) {
    super(FakeTaskListener.INSTANCE);

    this.credentials = credentials;
  }

  public UsernamePassword getUsernamePassword(URIish uri) {
    addDefaultCredentials(credentials);

    String username = uri.getUser();
    String password = uri.getPass();

    CredentialItem.Username u = new CredentialItem.Username();
    CredentialItem.Password p = new CredentialItem.Password();

    if (supports(u, p) && get(uri, u, p)) {
      username = u.getValue();
      char[] v = p.getValue();
      password = (v == null) ? null : new String(p.getValue());
      p.clear();
    }

    return new UsernamePassword(username, password);
  }
}

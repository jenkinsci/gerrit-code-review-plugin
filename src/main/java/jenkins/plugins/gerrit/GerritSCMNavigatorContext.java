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

import edu.umd.cs.findbugs.annotations.NonNull;
import jenkins.scm.api.SCMNavigator;
import jenkins.scm.api.SCMSourceObserver;
import jenkins.scm.api.trait.SCMNavigatorContext;

public class GerritSCMNavigatorContext
    extends SCMNavigatorContext<GerritSCMNavigatorContext, GerritSCMNavigatorRequest> {
  @NonNull
  @Override
  public GerritSCMNavigatorRequest newRequest(
      @NonNull SCMNavigator navigator, @NonNull SCMSourceObserver observer) {
    return new GerritSCMNavigatorRequest(navigator, this, observer);
  }
}

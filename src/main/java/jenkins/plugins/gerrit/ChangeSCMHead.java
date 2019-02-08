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

import java.util.Map;
import java.util.logging.Logger;
import javax.annotation.Nonnull;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.mixin.ChangeRequestCheckoutStrategy;
import jenkins.scm.api.mixin.ChangeRequestSCMHead2;
import org.eclipse.jgit.lib.ObjectId;

/** Head corresponding to a change. */
public class ChangeSCMHead extends SCMHead implements ChangeRequestSCMHead2 {

  private static final Logger LOGGER = Logger.getLogger(ChangeSCMHead.class.getName());

  private static final long serialVersionUID = 1;

  private final int changeNumber;

  private final int patchset;

  ChangeSCMHead(Map.Entry<String, ObjectId> ref, String branchName) {
    super(branchName);
    changeNumber = parseChangeNumber(ref);
    patchset = parsePatchset(ref);
  }

  private static int parseChangeNumber(Map.Entry<String, ObjectId> ref) {
    return parseIntPart(ref, 3);
  }

  private static int parsePatchset(Map.Entry<String, ObjectId> ref) {
    return parseIntPart(ref, 4);
  }

  private static int parseIntPart(Map.Entry<String, ObjectId> ref, int index) {
    String[] changeParts = ref.getKey().split("/");
    return Integer.parseInt(changeParts[index]);
  }

  /** {@inheritDoc} */
  @Override
  public String getPronoun() {
    return Messages.ChangeSCMHead_Pronoun();
  }

  /** {@inheritDoc} */
  @Nonnull
  @Override
  public ChangeRequestCheckoutStrategy getCheckoutStrategy() {
    return ChangeRequestCheckoutStrategy.HEAD;
  }

  @Nonnull
  @Override
  public String getOriginName() {
    return getName();
  }

  /** {@inheritDoc} */
  @Nonnull
  @Override
  public String getId() {
    return "C-" + changeNumber + "/" + patchset;
  }

  public int getChangeNumber() {
    return changeNumber;
  }

  @Nonnull
  @Override
  public SCMHead getTarget() {
    return new SCMHead(getName());
  }
}

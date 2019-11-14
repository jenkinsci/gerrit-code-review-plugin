// Copyright (C) 2019 The Android Open Source Project
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

package jenkins.plugins.gerrit.checks.api;

import com.google.common.base.MoreObjects;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.Objects;

/**
 * REST API representation of a pending check.
 *
 * <p>Checks are pending if they are in a non-final state and the external checker system intends to
 * post further updates on them. Which states these are depends on the external checker system, by
 * default we only consider checks in state {@link CheckState#NOT_STARTED} as pending.
 */
@SuppressFBWarnings("UWF_UNWRITTEN_PUBLIC_OR_PROTECTED_FIELD")
public class PendingCheckInfo {
  /** State of the check. */
  public CheckState state;

  public PendingCheckInfo(CheckState state) {
    this.state = state;
  }

  @Override
  public int hashCode() {
    return Objects.hash(state);
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof PendingCheckInfo)) {
      return false;
    }
    PendingCheckInfo o = (PendingCheckInfo) obj;
    return Objects.equals(state, o.state);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this).add("state", state).toString();
  }
}

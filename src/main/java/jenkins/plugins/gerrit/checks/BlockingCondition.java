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

package jenkins.plugins.gerrit.checks;

import java.util.Set;

/**
 * Conditions evaluated on a check in the context of a change that determine whether the check
 * blocks submission of a change.
 */
public enum BlockingCondition {
  /** Block submission unless all required checks on the change is passing. */
  STATE_NOT_PASSING;

  public static Boolean isRequired(Set<BlockingCondition> blocking) {
    return blocking.contains(STATE_NOT_PASSING);
  }
}

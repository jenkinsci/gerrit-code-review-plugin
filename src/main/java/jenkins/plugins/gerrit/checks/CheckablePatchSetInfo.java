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

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/** REST API representation of a patch set for which checks are pending. */
@SuppressFBWarnings(value = "UUF_UNUSED_PUBLIC_OR_PROTECTED_FIELD")
public class CheckablePatchSetInfo {
  /** Repository name. */
  public String repository;

  /** Change number. */
  public int changeNumber;

  /** Patch set ID. */
  public int patchSetId;
}

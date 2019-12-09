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

package com.google.gerrit.plugins.checks.api;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.Map;

/** REST API representation of pending checks on patch set. */
@SuppressFBWarnings(
    value = {"UUF_UNUSED_PUBLIC_OR_PROTECTED_FIELD", "UWF_UNWRITTEN_PUBLIC_OR_PROTECTED_FIELD"})
public class PendingChecksInfo {
  /** Patch set for which the checks are pending. */
  public CheckablePatchSetInfo patchSet;

  /** Pending checks on the patch set by checker UUID. */
  public Map<String, PendingCheckInfo> pendingChecks;
}

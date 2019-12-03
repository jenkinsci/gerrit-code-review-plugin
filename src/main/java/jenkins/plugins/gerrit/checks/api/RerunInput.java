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
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.extensions.api.changes.NotifyInfo;
import com.google.gerrit.extensions.api.changes.RecipientType;
import java.util.Map;
import java.util.Objects;

public class RerunInput {
  /**
   * Whom to send email notifications to when the combined check state changes due to rerunning this
   * check.
   */
  @Nullable public NotifyHandling notify;
  /** Additional information about whom to notify regardless of the {@link #notify} setting. */
  @Nullable public Map<RecipientType, NotifyInfo> notifyDetails;

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof RerunInput)) {
      return false;
    }
    RerunInput other = (RerunInput) o;
    return Objects.equals(other.notify, notify)
        && Objects.equals(other.notifyDetails, notifyDetails);
  }

  @Override
  public int hashCode() {
    return Objects.hash(notify, notifyDetails);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("notify", notify)
        .add("notifyDetails", notifyDetails)
        .toString();
  }
}

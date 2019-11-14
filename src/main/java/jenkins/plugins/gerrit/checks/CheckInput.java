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

import com.google.common.base.MoreObjects;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.extensions.api.changes.NotifyInfo;
import com.google.gerrit.extensions.api.changes.RecipientType;
import java.sql.Timestamp;
import java.util.Map;
import java.util.Objects;

/** Input to create or update a check. */
public class CheckInput {
  /** UUID of the checker. */
  @Nullable public String checkerUuid;
  /** State of the check. */
  @Nullable public CheckState state;
  /** Short message explaining the check state. */
  @Nullable public String message;
  /** Fully qualified URL to detailed result on the Checker's service. */
  @Nullable public String url;
  /** Date/Time at which the checker started processing this check. */
  @Nullable public Timestamp started;
  /** Date/Time at which the checker finished processing this check. */
  @Nullable public Timestamp finished;
  /**
   * Whom to send email notifications to when the combined check state changes due to posting this
   * check.
   */
  @Nullable public NotifyHandling notify;
  /** Additional information about whom to notify regardless of the {@link #notify} setting. */
  @Nullable public Map<RecipientType, NotifyInfo> notifyDetails;

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof CheckInput)) {
      return false;
    }
    CheckInput other = (CheckInput) o;
    return Objects.equals(other.checkerUuid, checkerUuid)
        && Objects.equals(other.state, state)
        && Objects.equals(other.message, message)
        && Objects.equals(other.url, url)
        && Objects.equals(other.started, started)
        && Objects.equals(other.finished, finished)
        && Objects.equals(other.notify, notify)
        && Objects.equals(other.notifyDetails, notifyDetails);
  }

  @Override
  public int hashCode() {
    return Objects.hash(checkerUuid, state, message, url, started, finished, notify, notifyDetails);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("checkerUuid", checkerUuid)
        .add("state", state)
        .add("message", message)
        .add("url", url)
        .add("started", started)
        .add("finished", finished)
        .add("notify", notify)
        .add("notifyDetails", notifyDetails)
        .toString();
  }
}

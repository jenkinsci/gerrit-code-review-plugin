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

import com.google.common.base.MoreObjects;
import com.google.gerrit.common.Nullable;
import java.sql.Timestamp;
import java.util.Objects;
import java.util.Set;

/** REST API representation of a Check. */
public class CheckInfo {
  /** Repository name that this check applies to. */
  public String repository;
  /** Change number that this check applies to. */
  public int changeNumber;
  /** Patch set ID that this check applies to. */
  public int patchSetId;
  /** UUID of the checker that posted this check. */
  public String checkerUuid;

  /** State that this check exited. */
  public CheckState state;
  /** Short message explaining the check state. */
  @Nullable public String message;
  /** Fully qualified URL to detailed result on the Checker's service. */
  @Nullable public String url;
  /** Timestamp of when this check was created. */
  @Nullable public Timestamp started;
  /** Timestamp of when this check was last updated. */
  @Nullable public Timestamp finished;

  /** Timestamp of when this check was created. */
  public Timestamp created;
  /** Timestamp of when this check was last updated. */
  public Timestamp updated;

  /** Name of the checker that produced this check. */
  public String checkerName;

  /** Status of the checker that produced this check. */
  public CheckerStatus checkerStatus;

  /** Blocking conditions that apply to this check. */
  public Set<BlockingCondition> blocking;

  /** Description of the checker that produced this check */
  public String checkerDescription;

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof CheckInfo)) {
      return false;
    }
    CheckInfo other = (CheckInfo) o;
    return Objects.equals(other.repository, repository)
        && Objects.equals(other.changeNumber, changeNumber)
        && Objects.equals(other.patchSetId, patchSetId)
        && Objects.equals(other.checkerUuid, checkerUuid)
        && Objects.equals(other.state, state)
        && Objects.equals(other.message, message)
        && Objects.equals(other.url, url)
        && Objects.equals(other.started, started)
        && Objects.equals(other.finished, finished)
        && Objects.equals(other.created, created)
        && Objects.equals(other.updated, updated)
        && Objects.equals(other.checkerName, checkerName)
        && Objects.equals(other.checkerStatus, checkerStatus)
        && Objects.equals(other.blocking, blocking)
        && Objects.equals(other.checkerDescription, checkerDescription);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        repository,
        changeNumber,
        patchSetId,
        checkerUuid,
        state,
        message,
        url,
        started,
        finished,
        created,
        updated,
        checkerName,
        checkerStatus,
        blocking,
        checkerDescription);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("repository", repository)
        .add("changeNumber", changeNumber)
        .add("patchSetId", patchSetId)
        .add("checkerUuid", checkerUuid)
        .add("state", state)
        .add("message", message)
        .add("url", url)
        .add("started", started)
        .add("finished", finished)
        .add("created", created)
        .add("updated", updated)
        .add("checkerName", checkerName)
        .add("checkerStatus", checkerStatus)
        .add("blocking", blocking)
        .add("checkerDescription", checkerDescription)
        .toString();
  }
}

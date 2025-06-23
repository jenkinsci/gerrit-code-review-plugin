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
import java.sql.Timestamp;
import java.util.Objects;
import java.util.Set;

public class CheckerInfo {
  public String uuid;
  public String name;
  public String description;
  public String url;
  public String repository;
  public CheckerStatus status;
  public Set<BlockingCondition> blocking;
  public String query;
  public Timestamp created;
  public Timestamp updated;

  @Override
  public int hashCode() {
    return Objects.hash(
        uuid, name, description, url, repository, status, blocking, query, created, updated);
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof CheckerInfo o)) {
      return false;
    }
    return Objects.equals(uuid, o.uuid)
        && Objects.equals(name, o.name)
        && Objects.equals(description, o.description)
        && Objects.equals(url, o.url)
        && Objects.equals(repository, o.repository)
        && Objects.equals(status, o.status)
        && Objects.equals(blocking, o.blocking)
        && Objects.equals(query, o.query)
        && Objects.equals(created, o.created)
        && Objects.equals(updated, o.updated);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("uuid", uuid)
        .add("name", name)
        .add("description", description)
        .add("repository", repository)
        .add("url", url)
        .add("status", status)
        .add("blockingConditions", blocking)
        .add("query", query)
        .add("created", created)
        .add("updated", updated)
        .toString();
  }
}

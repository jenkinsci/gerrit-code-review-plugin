// Copyright (C) 2013 Google Inc. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.gitiles.api;

import java.util.List;
import org.eclipse.jgit.diff.DiffEntry;

public class CommitJsonData {
  public static class Log {
    public List<Commit> log;
    public String previous;
    public String next;
  }

  public static class Ident {
    public String name;
    public String email;
    public String time;
  }

  public static class Commit {
    public String commit;
    public String tree;
    public List<String> parents;
    public Ident author;
    public Ident committer;
    public String message;

    public List<Diff> treeDiff;
  }

  /** @see DiffEntry */
  static class Diff {
    String type;
    String oldId;
    int oldMode;
    String oldPath;
    String newId;
    int newMode;
    String newPath;
    Integer score;
  }
}

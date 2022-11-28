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

import hudson.console.ConsoleNote;
import hudson.model.TaskListener;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;

public class FakeTaskListener implements TaskListener {

  public static final FakeTaskListener INSTANCE = new FakeTaskListener();

  @Override
  public PrintStream getLogger() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void annotate(ConsoleNote consoleNote) throws IOException {}

  @Override
  public void hyperlink(String s, String s1) throws IOException {}

  @Override
  public PrintWriter error(String s) {
    throw new UnsupportedOperationException();
  }

  @Override
  public PrintWriter error(String s, Object... objects) {
    throw new UnsupportedOperationException();
  }

  @Override
  public PrintWriter fatalError(String s) {
    throw new UnsupportedOperationException();
  }

  @Override
  public PrintWriter fatalError(String s, Object... objects) {
    throw new UnsupportedOperationException();
  }
}

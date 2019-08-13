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

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

public class NullPrintStream extends PrintStream {
  public static NullPrintStream INSTANCE = nullPrintStream();

  public NullPrintStream() throws UnsupportedEncodingException {
    super(
        new OutputStream() {
          @Override
          public void write(int b) throws IOException {
            // Do nothing
          }
        },
        true,
        StandardCharsets.UTF_8.toString());
  }

  private static NullPrintStream nullPrintStream() {
    try {
      return new NullPrintStream();
    } catch (UnsupportedEncodingException e) {
      // UTF_8 would always be supported
      return null;
    }
  }
}

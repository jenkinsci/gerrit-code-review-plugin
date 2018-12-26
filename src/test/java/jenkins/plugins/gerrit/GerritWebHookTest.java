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

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import javax.servlet.ReadListener;
import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import org.junit.Test;

public class GerritWebHookTest {
  String testRepoName = "testrepo";
  byte[] gerritEventBody = "{\"project\":{\"name\":\"testrepo\"}}".getBytes(StandardCharsets.UTF_8);
  ByteArrayInputStream bodyInputStream = new ByteArrayInputStream(gerritEventBody);

  @Test
  public void shouldExtractHttpPostBodyWhenLengthIsUnknown() throws Exception {
    GerritWebHook webHook = new GerritWebHook();

    GerritProjectEvent projectEvent = webHook.getBody(getInMemoryServletRequest(gerritEventBody));

    assertEquals(testRepoName, projectEvent.project.name);
  }

  private HttpServletRequest getInMemoryServletRequest(byte[] body) throws IOException {
    int gerritEventBodySize = body.length;
    HttpServletRequest mockedServletRequest = mock(HttpServletRequest.class);
    when(mockedServletRequest.getContentLength()).thenReturn(-1);
    when(mockedServletRequest.getInputStream())
        .thenReturn(
            new ServletInputStream() {
              private int readIdx = 0;

              @Override
              public boolean isFinished() {
                return readIdx >= gerritEventBodySize;
              }

              @Override
              public boolean isReady() {
                return true;
              }

              @Override
              public void setReadListener(ReadListener readListener) {}

              public int read() throws IOException {
                return bodyInputStream.read();
              }
            });
    return mockedServletRequest;
  }
}

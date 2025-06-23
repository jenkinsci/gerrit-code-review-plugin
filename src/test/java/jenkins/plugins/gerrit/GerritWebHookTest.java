// Copyright (C) 2019 GerritForge Ltd
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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.gson.JsonParseException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.Test;

public class GerritWebHookTest {
  String testRepoName = "testrepo";
  GerritWebHook webHook = new GerritWebHook();

  @Test
  public void shouldExtractHttpPostBodyWhenLengthIsUnknown() throws Exception {
    byte[] gerritEventBody =
        "{\"project\":{\"name\":\"testrepo\"}, \"type\":\"ref-updated\"}"
            .getBytes(StandardCharsets.UTF_8);
    Optional<GerritProjectEvent> projectEvent =
        webHook.getBody(getInMemoryServletRequest(gerritEventBody));

    assertTrue(projectEvent.isPresent());
    assertEquals(testRepoName, projectEvent.get().project.name);
  }

  @Test
  public void shouldIngoreNotInterestingEvents() throws Exception {
    assertFalse(
        webHook
            .getBody(getInMemoryServletRequest("{\"type\": \"dont-care\"}".getBytes()))
            .isPresent());
  }

  @Test(expected = JsonParseException.class)
  public void shouldThrowExceptionForInvalidJsonEvents() throws Exception {
    webHook.getBody(getInMemoryServletRequest("this-is-invalid-JSON".getBytes()));
  }

  private HttpServletRequest getInMemoryServletRequest(byte[] body) throws IOException {
    int gerritEventBodySize = body.length;
    ByteArrayInputStream bodyInputStream = new ByteArrayInputStream(body);
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

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
import static org.junit.Assert.assertNotNull;

import com.google.gerrit.plugins.checks.api.CheckInfo;
import com.google.gerrit.plugins.checks.api.CheckState;
import com.google.gerrit.plugins.checks.client.Checks;
import com.google.gson.reflect.TypeToken;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import org.junit.Test;

public class ChecksTest {

  @Test
  public void shouldDeserializeCheckInfo() throws ParseException {
    String testRepo = "test-repo";
    int changeNumber = 1;
    int patchSetId = 2;
    String checkerUuid = "test:my-checker";
    CheckState state = CheckState.NOT_STARTED;
    String url = "https://foo.corp.com/test-checker/results/123";
    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
    dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));

    Date created = dateFormat.parse("2021-09-05 10:11:12.5678");
    Date updated = dateFormat.parse("2021-09-06 11:12:13.6789");

    String checksJsonResponse =
        String.format(
            "{\n"
                + "      \"repository\": \"%s\",\n"
                + "      \"change_number\": %d,\n"
                + "      \"patch_set_id\": %d,\n"
                + "      \"checker_uuid\": \"%s\",\n"
                + "      \"state\": \"%s\",\n"
                + "      \"url\": \"%s\",\n"
                + "      \"created\": \"%s\",\n"
                + "      \"updated\": \"%s\"\n"
                + "    }",
            testRepo,
            changeNumber,
            patchSetId,
            checkerUuid,
            state,
            url,
            dateFormat.format(created),
            dateFormat.format(updated));

    CheckInfo checkInfo =
        Checks.JsonBodyParser.parseResponse(
            checksJsonResponse, new TypeToken<CheckInfo>() {}.getType());

    assertNotNull(checkInfo);
    assertEquals(testRepo, checkInfo.repository);
    assertEquals(changeNumber, checkInfo.changeNumber);
    assertEquals(patchSetId, checkInfo.patchSetId);
    assertEquals(checkerUuid, checkInfo.checkerUuid);
    assertEquals(state, checkInfo.state);
    assertEquals(url, checkInfo.url);
    assertEquals(created, checkInfo.created);
    assertEquals(updated, checkInfo.updated);
  }
}

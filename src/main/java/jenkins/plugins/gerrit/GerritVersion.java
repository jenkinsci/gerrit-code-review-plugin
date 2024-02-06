// Copyright (C) 2024 GerritForge Ltd
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

import com.google.gerrit.extensions.api.GerritApi;
import com.google.gerrit.extensions.restapi.RestApiException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class GerritVersion {
  private static final Logger LOGGER = Logger.getLogger(GerritVersion.class.getName());

  public static boolean isVersionBelow215(GerritApi gerritApi) throws RestApiException {
    String version = gerritApi.config().server().getVersion();

    if (version == null) {
      return false;
    }

    if (version.equals("<2.8")) {
      return true;
    }

    String[] versionSplit = version.split("\\.");
    if (versionSplit.length == 0) {
      return false;
    }
    try {
      int majorVersion = Integer.parseInt(versionSplit[0]);
      if (majorVersion < 2) {
        return true;
      }
      if (majorVersion > 2) {
        return false;
      }
      if (versionSplit.length < 2) {
        return true;
      }
      int minorVersion = Integer.parseInt(versionSplit[1]);
      if (minorVersion < 15) {
        return true;
      }
      return false;
    } catch (NumberFormatException e) {
      LOGGER.log(Level.SEVERE, "Unable to parse Gerrit version " + version, e);
      return false;
    }
  }
}

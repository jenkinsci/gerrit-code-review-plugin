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

import jenkins.scm.api.metadata.AvatarMetadataAction;
import org.jenkins.ui.icon.Icon;
import org.jenkins.ui.icon.IconSet;

/**
 * Link to GitHub
 *
 * @author Kohsuke Kawaguchi
 */
public class GerritLogo extends AvatarMetadataAction {

  static {
    IconSet.icons.addIcon(
        new Icon(
            "icon-gerrit-logo icon-sm",
            "plugin/gerrit/icons/gerrit-logo-16x16.png",
            Icon.ICON_SMALL_STYLE));
    IconSet.icons.addIcon(
        new Icon(
            "icon-gerrit-logo icon-md",
            "plugin/gerrit/icons/gerrit-logo-24x24.png",
            Icon.ICON_MEDIUM_STYLE));
    IconSet.icons.addIcon(
        new Icon(
            "icon-gerrit-logo icon-lg",
            "plugin/gerrit/icons/gerrit-logo-32x32.png",
            Icon.ICON_LARGE_STYLE));
    IconSet.icons.addIcon(
        new Icon(
            "icon-gerrit-logo icon-xlg",
            "plugin/gerrit/icons/gerrit-logo-48x48.png",
            Icon.ICON_XLARGE_STYLE));
  }

  /** {@inheritDoc} */
  @Override
  public String getAvatarIconClassName() {
    return "icon-gerrit-logo";
  }

  /** {@inheritDoc} */
  @Override
  public String getAvatarDescription() {
    return Messages.GerritAvatar_IconDescription();
  }

  /** {@inheritDoc} */
  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    return true;
  }

  /** {@inheritDoc} */
  @Override
  public int hashCode() {
    return 0;
  }

  /** {@inheritDoc} */
  @Override
  public String toString() {
    return "GerritLogo{}";
  }
}

package jenkins.plugins.gerrit;

import hudson.EnvVars;
import hudson.model.TaskListener;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.workflow.steps.StepContext;

public class GerritChange {

  private static final Pattern BRANCH_PATTERN =
      Pattern.compile("[0-9][0-9]/(?<changeId>[0-9]+)/(?<revision>[0-9]+)");

  private Integer changeId = null;
  private Integer revision = null;

  public GerritChange(Map<String, String> env, PrintStream logger)
      throws IOException, InterruptedException {

    if (StringUtils.isNotEmpty(env.get("GERRIT_CHANGE_NUMBER"))) {
      changeId = Integer.parseInt(env.get("GERRIT_CHANGE_NUMBER"));
      revision = Integer.parseInt(env.get("GERRIT_PATCHSET_NUMBER"));
    } else {
      if (StringUtils.isNotEmpty(env.get("BRANCH_NAME"))) {
        Matcher matcher = BRANCH_PATTERN.matcher(env.get("BRANCH_NAME"));
        if (matcher.matches()) {
          changeId = Integer.parseInt(matcher.group("changeId"));
          revision = Integer.parseInt(matcher.group("revision"));
        }
      }
    }
    if (changeId == null) {
      if (logger != null) {
        logger.println(
            "Gerrit Review is disabled, invalid reference at BRANCH_NAME or GERRIT_CHANGE_NUMBER/GERRIT_PATCHSET_NUMBER");
      }
    }
  }

  public GerritChange(StepContext context) throws IOException, InterruptedException {
    this(context.get(EnvVars.class), context.get(TaskListener.class).getLogger());
  }

  public boolean valid() {
    return changeId != null;
  }

  public Integer getChangeId() {
    return changeId;
  }

  public Integer getRevision() {
    return revision;
  }
}

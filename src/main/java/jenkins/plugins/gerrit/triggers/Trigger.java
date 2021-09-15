package jenkins.plugins.gerrit.triggers;

import hudson.security.ACL;
import hudson.security.ACLContext;
import java.util.List;
import javax.annotation.Nonnull;
import jenkins.model.Jenkins;
import jenkins.plugins.gerrit.GerritProjectEvent;
import jenkins.plugins.gerrit.GerritSCMSource;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.SCMSourceOwner;
import org.acegisecurity.Authentication;
import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

interface Trigger {
  Logger log = LoggerFactory.getLogger(Trigger.class);

  default void processEvent(GerritProjectEvent projectEvent) {
    String username = "anonymous";
    Authentication authentication = getJenkinsInstance().getAuthentication();
    if (authentication != null) {
      username = authentication.getName();
    }

    log.info("GerritWebHook invoked by user '{}' for event: {}", username, projectEvent);

    try (ACLContext acl = ACL.as(ACL.SYSTEM)) {
      List<WorkflowMultiBranchProject> jenkinsItems =
          getJenkinsInstance().getAllItems(WorkflowMultiBranchProject.class);
      log.info("Scanning {} Jenkins items", jenkinsItems.size());
      for (SCMSourceOwner scmJob : jenkinsItems) {
        log.info("Scanning job " + scmJob);
        List<SCMSource> scmSources = scmJob.getSCMSources();
        for (SCMSource scmSource : scmSources) {
          if (scmSource instanceof GerritSCMSource) {
            GerritSCMSource gerritSCMSource = (GerritSCMSource) scmSource;
            triggerSCMScan(projectEvent, gerritSCMSource, scmJob);
          }
        }
      }
    }
  }

  default void triggerSCMScan(
      GerritProjectEvent projectEvent, GerritSCMSource gerritSCMSource, SCMSourceOwner scmJob) {
    log.debug("Checking match for SCM source: " + gerritSCMSource.getRemote());
    if (projectEvent.matches(gerritSCMSource.getRemote())) {
      log.info("Triggering SCM event for source " + gerritSCMSource + " on job " + scmJob);
      scmJob.onSCMSourceUpdated(gerritSCMSource);
    }
  }

  @Nonnull
  static Jenkins getJenkinsInstance() throws IllegalStateException {
    return Jenkins.getInstance();
  }
}

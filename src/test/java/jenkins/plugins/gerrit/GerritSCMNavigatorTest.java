package jenkins.plugins.gerrit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import com.google.gerrit.extensions.common.ProjectInfo;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.model.TaskListener;
import hudson.util.LogTaskListener;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.SCMSourceObserver;
import jenkins.scm.api.SCMSourceOwner;
import org.apache.commons.lang3.StringUtils;
import org.junit.Rule;
import org.junit.Test;

/** @author Réda Housni Alaoui */
public class GerritSCMNavigatorTest {

  @Rule public GerritMockServerRule g = new GerritMockServerRule(this);

  @Test
  public void testId() {
    GerritSCMNavigator navigator =
        new GerritSCMNavigator(g.getUrl(), false, null, Collections.emptyList());
    assertEquals(
        GerritSCMNavigator.class.getName() + "::server-url=" + g.getUrl() + "::credentials-id=null",
        navigator.getId());
  }

  @Test
  public void visitSources() throws IOException, InterruptedException {
    g.addProject(createProject("foo"));
    g.addProject(createProject("bar"));

    GerritSCMNavigator navigator =
        new GerritSCMNavigator(g.getUrl(), false, null, Collections.emptyList());
    DummySCMSourceObserver sourceObserver = new DummySCMSourceObserver();
    navigator.visitSources(sourceObserver);

    assertEquals(2, sourceObserver.observedProjectNames.size());
    assertTrue(sourceObserver.observedProjectNames.contains("foo"));
    assertTrue(sourceObserver.observedProjectNames.contains("bar"));

    assertEquals(2, sourceObserver.addedSourceByProjectName.size());
    assertSCMSourceValidity(sourceObserver.addedSourceByProjectName.get("foo"), "foo", navigator);
    assertSCMSourceValidity(sourceObserver.addedSourceByProjectName.get("bar"), "bar", navigator);
  }

  private void assertSCMSourceValidity(
      SCMSource scmSource, String projectName, GerritSCMNavigator navigator) {
    GerritSCMSource source = (GerritSCMSource) scmSource;
    assertEquals(
        StringUtils.appendIfMissing(navigator.getServerUrl(), "/") + projectName,
        source.getRemote());
    assertEquals(navigator.isInsecureHttps(), source.getInsecureHttps());
    assertEquals(navigator.getCredentialsId(), source.getCredentialsId());
  }

  private ProjectInfo createProject(String name) {
    ProjectInfo projectInfo = new ProjectInfo();
    projectInfo.id = name;
    return projectInfo;
  }

  private static class DummySCMSourceObserver extends SCMSourceObserver {

    private final List<String> observedProjectNames = new ArrayList<>();
    private final Map<String, SCMSource> addedSourceByProjectName = new HashMap<>();

    @NonNull
    @Override
    public SCMSourceOwner getContext() {
      return mock(SCMSourceOwner.class);
    }

    @NonNull
    @Override
    public TaskListener getListener() {
      return new LogTaskListener(Logger.getAnonymousLogger(), Level.INFO);
    }

    @NonNull
    @Override
    public ProjectObserver observe(@NonNull String projectName) throws IllegalArgumentException {
      observedProjectNames.add(projectName);
      return new ProjectObserver() {

        @Override
        public void addSource(@NonNull SCMSource source) {
          addedSourceByProjectName.put(projectName, source);
        }

        @Override
        public void addAttribute(@NonNull String key, @Nullable Object value)
            throws IllegalArgumentException, ClassCastException {}

        @Override
        public void complete() throws IllegalStateException {}
      };
    }

    @Override
    public void addAttribute(@NonNull String key, @Nullable Object value)
        throws IllegalArgumentException, ClassCastException {}
  }
}

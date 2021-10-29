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
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.scm.api.SCMSourceObserver;
import jenkins.scm.api.SCMSourceOwner;
import jenkins.scm.impl.NoOpProjectObserver;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/** @author Réda Housni Alaoui */
public class GerritSCMNavigatorTest {

  @Rule public GerritMockServerRule g = new GerritMockServerRule(this);

  private GerritSCMNavigator navigator;
  private DummySCMSourceObserver sourceObserver;

  @Before
  public void beforeEach() {
    navigator = new GerritSCMNavigator(g.getUrl(), false, null, Collections.emptyList());
    sourceObserver = new DummySCMSourceObserver();
  }

  @Test
  public void testId() {
    assertEquals(
        GerritSCMNavigator.class.getName() + "::server-url=" + g.getUrl() + "::credentials-id=null",
        navigator.getId());
  }

  @Test
  public void visitSources() throws IOException, InterruptedException {
    g.addProject(createProject("foo"));
    g.addProject(createProject("bar"));

    navigator.visitSources(sourceObserver);

    assertTrue(sourceObserver.observedProjectNames.contains("foo"));
    assertTrue(sourceObserver.observedProjectNames.contains("bar"));
  }

  private ProjectInfo createProject(String name) {
    ProjectInfo projectInfo = new ProjectInfo();
    projectInfo.id = name;
    return projectInfo;
  }

  private static class DummySCMSourceObserver extends SCMSourceObserver {

    private final List<String> observedProjectNames = new ArrayList<>();

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
      return new NoOpProjectObserver();
    }

    @Override
    public void addAttribute(@NonNull String key, @Nullable Object value)
        throws IllegalArgumentException, ClassCastException {}
  }
}

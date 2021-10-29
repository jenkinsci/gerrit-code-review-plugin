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
import jenkins.plugins.git.traits.RefSpecsSCMSourceTrait;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.SCMSourceObserver;
import jenkins.scm.api.SCMSourceOwner;
import jenkins.scm.api.trait.SCMSourceTrait;
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
    RecordingSCMSourceObserver sourceObserver = new RecordingSCMSourceObserver();
    navigator.visitSources(sourceObserver);

    assertEquals(2, sourceObserver.observedProjectNames.size());
    assertTrue(sourceObserver.observedProjectNames.contains("foo"));
    assertTrue(sourceObserver.observedProjectNames.contains("bar"));

    assertEquals(2, sourceObserver.addedSourceByProjectName.size());
    assertSCMSourceValidity(
        sourceObserver.addedSourceByProjectName.get("foo"),
        "foo",
        navigator,
        Collections.emptyList());
    assertSCMSourceValidity(
        sourceObserver.addedSourceByProjectName.get("bar"),
        "bar",
        navigator,
        Collections.emptyList());
  }

  @Test
  public void sourcesShareCommonPropertiesWithTheNavigator()
      throws IOException, InterruptedException {
    ProjectInfo project = createProject("foo");
    g.addProject(project);
    RefSpecsSCMSourceTrait trait = new RefSpecsSCMSourceTrait();
    GerritSCMNavigator navigator =
        new GerritSCMNavigator(
            g.getUrl(), true, "my-credentials", Collections.singletonList(trait));

    RecordingSCMSourceObserver sourceObserver = new RecordingSCMSourceObserver();
    navigator.visitSources(sourceObserver);

    assertEquals(1, sourceObserver.observedProjectNames.size());
    assertEquals(project.id, sourceObserver.observedProjectNames.get(0));

    assertEquals(1, sourceObserver.addedSourceByProjectName.size());
    assertSCMSourceValidity(
        sourceObserver.addedSourceByProjectName.get(project.id),
        project.id,
        navigator,
        Collections.singletonList(trait));
  }

  private void assertSCMSourceValidity(
      SCMSource scmSource,
      String projectName,
      GerritSCMNavigator navigator,
      List<SCMSourceTrait> traits) {
    GerritSCMSource source = (GerritSCMSource) scmSource;
    assertEquals(
        StringUtils.appendIfMissing(navigator.getServerUrl(), "/") + projectName,
        source.getRemote());
    assertEquals(navigator.isInsecureHttps(), source.getInsecureHttps());
    assertEquals(navigator.getCredentialsId(), source.getCredentialsId());
    assertEquals(traits.size(), source.getTraits().size());
    traits.forEach(trait -> assertTrue(source.getTraits().contains(trait)));
  }

  private ProjectInfo createProject(String name) {
    ProjectInfo projectInfo = new ProjectInfo();
    projectInfo.id = name;
    return projectInfo;
  }

  private static class RecordingSCMSourceObserver extends SCMSourceObserver {

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
          if (addedSourceByProjectName.put(projectName, source) == null) {
            return;
          }
          throw new IllegalArgumentException("Duplicate source for project " + projectName);
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

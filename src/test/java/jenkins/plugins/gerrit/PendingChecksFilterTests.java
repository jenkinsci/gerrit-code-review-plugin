package jenkins.plugins.gerrit;

import static org.junit.Assert.*;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.google.gerrit.plugins.checks.api.CheckState;
import com.google.gerrit.plugins.checks.api.CheckablePatchSetInfo;
import com.google.gerrit.plugins.checks.api.PendingCheckInfo;
import com.google.gerrit.plugins.checks.api.PendingChecksInfo;
import hudson.util.StreamTaskListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import jenkins.plugins.gerrit.traits.FilterChecksTrait.ChecksQueryOperator;
import jenkins.scm.api.SCMHeadObserver;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockserver.junit.MockServerRule;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.JsonBody;

@SuppressWarnings("deprecation")
public class PendingChecksFilterTests {

  @Rule public MockServerRule g = new MockServerRule(this);
  @Rule public JenkinsRule j = new JenkinsRule();

  private static final String checkerUuid = "test:checker";

  private static GerritSCMSourceContext context;
  private static PendingChecksFilter filter;
  private static ArrayList<PendingChecksInfo> pendingChecksInfos;
  private static HashMap<String, List<String>> query;

  private GerritSCMSourceRequest request;

  @BeforeClass
  public static void setupClass() throws Exception {
    context = new GerritSCMSourceContext(null, SCMHeadObserver.none());
    context.wantFilterForPendingChecks(true);
    context.withChecksQueryOperator(ChecksQueryOperator.ID);
    context.withChecksQueryString(checkerUuid);

    pendingChecksInfos = new ArrayList<>();
    pendingChecksInfos.add(getPendingChecksInfo("test", 11111, 1, CheckState.NOT_STARTED));

    query = new HashMap<>();
    query.put("query", Arrays.asList("checker:test:checker"));

    filter = new PendingChecksFilter();
  }

  @Before
  public void setup() throws Exception {
    UsernamePasswordCredentialsImpl c =
        new UsernamePasswordCredentialsImpl(
            CredentialsScope.GLOBAL, "cid", "cid", "USERNAME", "PASSWORD");
    CredentialsProvider.lookupStores(j.jenkins)
        .iterator()
        .next()
        .addCredentials(Domain.global(), c);

    g.getClient()
        .when(
            HttpRequest.request("/a/plugins/checks/checks.pending/")
                .withQueryStringParameters(query)
                .withMethod("GET"))
        .respond(
            HttpResponse.response()
                .withStatusCode(200)
                .withBody(JsonBody.json(pendingChecksInfos)));

    GerritSCMSource source =
        new GerritSCMSource(
            String.format(
                "https://%s:%s/a/test",
                g.getClient().remoteAddress().getHostName(),
                g.getClient().remoteAddress().getPort()));
    source.setInsecureHttps(true);
    source.setCredentialsId("cid");
    request = context.newRequest(source, new StreamTaskListener());
  }

  @Test
  public void testPendingChecksFilterExcludesNonPending() throws Exception {
    HashMap<String, ObjectId> refs = new HashMap<>();
    refs.put("refs/changes/22/22222/2", ObjectId.zeroId());

    ChangeSCMHead head =
        new ChangeSCMHead(refs.entrySet().iterator().next(), "master", new HashSet<>());

    assertTrue(filter.isExcluded(request, head));
  }

  @Test
  public void testPendingChecksFilterIncludesPending() throws Exception {
    HashMap<String, ObjectId> refs = new HashMap<>();
    refs.put("refs/changes/11/11111/1", ObjectId.zeroId());

    ChangeSCMHead head =
        new ChangeSCMHead(refs.entrySet().iterator().next(), "master", new HashSet<>());

    assertFalse(filter.isExcluded(request, head));
  }

  private static PendingChecksInfo getPendingChecksInfo(
      String project, int changeNumber, int patchSetNumber, CheckState state) {
    CheckablePatchSetInfo checkablePatchSet = new CheckablePatchSetInfoMapper();
    checkablePatchSet.repository = project;
    checkablePatchSet.changeNumber = changeNumber;
    checkablePatchSet.patchSetId = patchSetNumber;

    PendingChecksInfo pendingChecksInfo = new PendingChecksInfoMapper();
    pendingChecksInfo.patchSet = checkablePatchSet;
    pendingChecksInfo.pendingChecks = new HashMap<>();
    pendingChecksInfo.pendingChecks.put(checkerUuid, new PendingCheckInfo(state));

    return pendingChecksInfo;
  }

  @JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
  private static class PendingChecksInfoMapper extends PendingChecksInfo {}

  @JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
  private static class CheckablePatchSetInfoMapper extends CheckablePatchSetInfo {}
}

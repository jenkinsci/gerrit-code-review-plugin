package jenkins.plugins.gerrit;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.Assert.*;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.github.tomakehurst.wiremock.matching.StringValuePattern;
import com.google.gerrit.plugins.checks.api.CheckState;
import com.google.gerrit.plugins.checks.api.CheckablePatchSetInfo;
import com.google.gerrit.plugins.checks.api.PendingCheckInfo;
import com.google.gerrit.plugins.checks.api.PendingChecksInfo;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.GsonBuilder;
import hudson.util.StreamTaskListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import jenkins.plugins.gerrit.traits.FilterChecksTrait.ChecksQueryOperator;
import jenkins.scm.api.SCMHeadObserver;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

@SuppressWarnings("deprecation")
public class PendingChecksFilterTests {

  @Rule
  public WireMockRule wireMock =
      new WireMockRule(wireMockConfig().dynamicHttpsPort().httpDisabled(true));

  @Rule public JenkinsRule j = new JenkinsRule();

  private static final String checkerUuid = "test:checker";

  private static GerritSCMSourceContext context;
  private static PendingChecksFilter filter;
  private static ArrayList<PendingChecksInfo> pendingChecksInfos;
  private static HashMap<String, StringValuePattern> query;

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
    query.put("query", equalTo("checker:test:checker"));

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

    stubFor(
        get(urlPathEqualTo("/a/plugins/checks/checks.pending/"))
            .withQueryParams(query)
            .willReturn(
                ok(
                    new GsonBuilder()
                        .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
                        .create()
                        .toJson(pendingChecksInfos))));

    GerritSCMSource source =
        new GerritSCMSource(
            String.format("https://%s:%s/a/test", "localhost", wireMock.httpsPort()));
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
    CheckablePatchSetInfo checkablePatchSet = new CheckablePatchSetInfo();
    checkablePatchSet.repository = project;
    checkablePatchSet.changeNumber = changeNumber;
    checkablePatchSet.patchSetId = patchSetNumber;

    PendingChecksInfo pendingChecksInfo = new PendingChecksInfo();
    pendingChecksInfo.patchSet = checkablePatchSet;
    pendingChecksInfo.pendingChecks = new HashMap<>();
    pendingChecksInfo.pendingChecks.put(checkerUuid, new PendingCheckInfo(state));

    return pendingChecksInfo;
  }
}

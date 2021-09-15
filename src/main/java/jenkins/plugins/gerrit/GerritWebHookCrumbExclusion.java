package jenkins.plugins.gerrit;

import hudson.Extension;
import hudson.security.csrf.CrumbExclusion;
import jenkins.plugins.gerrit.triggers.GerritWebHook;

import java.io.IOException;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Extension
public class GerritWebHookCrumbExclusion extends CrumbExclusion {

  @Override
  public boolean process(HttpServletRequest req, HttpServletResponse resp, FilterChain chain)
      throws IOException, ServletException {
    String pathInfo = req.getPathInfo();
    if (pathInfo == null || pathInfo.isEmpty()) {
      return false;
    }
    pathInfo = pathInfo.endsWith("/") ? pathInfo : pathInfo + '/';
    if (!pathInfo.equals(getExclusionPath())) {
      return false;
    }
    chain.doFilter(req, resp);
    return true;
  }

  public String getExclusionPath() {
    return "/" + GerritWebHook.URLNAME + "/";
  }
}

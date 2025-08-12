package jenkins.plugins.gerrit;

import hudson.Extension;
import hudson.security.csrf.CrumbExclusion;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

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

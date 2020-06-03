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

import com.urswolfer.gerrit.client.rest.GerritAuthData;
import com.urswolfer.gerrit.client.rest.http.HttpClientBuilderExtension;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Optional;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.http.*;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.protocol.HttpContext;

class UserAgentClientBuilderExtension extends HttpClientBuilderExtension {
  static final HttpClientBuilderExtension INSTANCE = new UserAgentClientBuilderExtension();
  private static final Logger LOGGER =
      Logger.getLogger(UserAgentClientBuilderExtension.class.getName());

  private static String pluginVersion;

  @Override
  public HttpClientBuilder extend(HttpClientBuilder httpClientBuilder, GerritAuthData authData) {
    HttpClientBuilder builder = super.extend(httpClientBuilder, authData);
    httpClientBuilder.addInterceptorLast(new UserAgentHttpRequestInterceptor());
    return builder;
  }

  private static class UserAgentHttpRequestInterceptor implements HttpRequestInterceptor {
    public void process(final HttpRequest request, final HttpContext context)
        throws HttpException, IOException {
      Header existingUserAgent = request.getFirstHeader(HttpHeaders.USER_AGENT);
      String userAgent = String.format("gerrit-code-review-plugin/%s", getCurrentVersion());
      userAgent += " using " + existingUserAgent.getValue();
      request.setHeader(HttpHeaders.USER_AGENT, userAgent);
    }
  }

  private static String getCurrentVersion() {
    if (pluginVersion != null) {
      return pluginVersion;
    }

    ClassLoader pluginClassLoader = UserAgentClientBuilderExtension.class.getClassLoader();
    Optional<Properties> jarProperties =
        Optional.ofNullable(
                pluginClassLoader.getResource(
                    "META-INF/maven/org.jenkins-ci.plugins/gerrit-code-review/pom.properties"))
            .map(UserAgentClientBuilderExtension::loadProperties);
    return jarProperties
        .flatMap(p -> Optional.ofNullable((String) p.get("version")))
        .orElse("(dev)");
  }

  private static Properties loadProperties(URL url) {
    Properties properties = new Properties();
    try {
      try (InputStream is = url.openStream()) {
        properties.load(is);
      }
      return properties;
    } catch (IOException ioe) {
      LOGGER.log(Level.WARNING, "Unable to extract the current plugin version", ioe);
      return properties;
    }
  }
}

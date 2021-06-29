package jenkins.plugins.gerrit.rest;

import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.net.ssl.SSLContext;

import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.ssl.TrustStrategy;
import org.eclipse.jgit.transport.URIish;

public abstract class AbstractApiBuilder <T extends AbstractApi> {
    
  public static final Logger LOGGER = Logger.getLogger(AbstractApiBuilder.class.getName());

  protected URIish gerritBaseURL;
  protected HttpClientBuilder clientBuilder;
  protected boolean isAuthenticated = false;

  public AbstractApiBuilder(URIish gerritBaseURL) {
    this.gerritBaseURL = gerritBaseURL;
    clientBuilder = HttpClientBuilder.create();
  }

  public AbstractApiBuilder<T> allowInsecureHttps() {
    try {
      SSLContext sslContext =
          new SSLContextBuilder()
              .loadTrustMaterial(
                  null,
                  new TrustStrategy() {
                    public boolean isTrusted(final X509Certificate[] chain, String authType)
                        throws CertificateException {
                      return true;
                    }
                  })
              .build();
      SSLConnectionSocketFactory sslsf =
          new SSLConnectionSocketFactory(sslContext, new NoopHostnameVerifier());
      clientBuilder.setSSLSocketFactory(sslsf);
    } catch (KeyStoreException | KeyManagementException | NoSuchAlgorithmException e) {
      LOGGER.log(Level.WARNING, "Could not disable SSL verification.", e);
    }
    return this;
  }

  public AbstractApiBuilder<T> setBasicAuthCredentials(String username, String password) {
    CredentialsProvider provider = new BasicCredentialsProvider();
    UsernamePasswordCredentials auth = new UsernamePasswordCredentials(username, password);
    provider.setCredentials(AuthScope.ANY, auth);
    clientBuilder.setDefaultCredentialsProvider(provider);
    isAuthenticated = true;
    return this;
  }

  public abstract T build();
}

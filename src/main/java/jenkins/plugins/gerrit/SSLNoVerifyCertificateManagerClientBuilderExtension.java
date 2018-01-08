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
import org.apache.http.impl.client.HttpClientBuilder;

import javax.net.ssl.*;
import java.security.SecureRandom;
import java.security.cert.CertificateException;

public class SSLNoVerifyCertificateManagerClientBuilderExtension extends HttpClientBuilderExtension {
    private static SSLContext trustAnyX509Certificate = null;
    private static HostnameVerifier acceptAnyX509Hostname = null;

    static {
        try {
            trustAnyX509Certificate = SSLContext.getInstance("SSL");

            trustAnyX509Certificate.init(null, new TrustManager[]{new X509TrustManager() {
                @Override
                public void checkClientTrusted(java.security.cert.X509Certificate[] x509Certificates, String s) throws CertificateException {
                }

                @Override
                public void checkServerTrusted(java.security.cert.X509Certificate[] x509Certificates, String s) throws CertificateException {
                }

                @Override
                public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                    return new java.security.cert.X509Certificate[0];
                }
            }}, new SecureRandom());

            acceptAnyX509Hostname = new HostnameVerifier() {
                @Override
                public boolean verify(String s, SSLSession sslSession) {
                    return true;
                }
            };
        } catch (Exception e) {
            System.err.println("Unable to initialize SSLContext for accepting any X.509 certificate");
            e.printStackTrace();
        }
    }

    public static final SSLNoVerifyCertificateManagerClientBuilderExtension INSTANCE = new SSLNoVerifyCertificateManagerClientBuilderExtension();

    @Override
    public HttpClientBuilder extend(HttpClientBuilder httpClientBuilder, GerritAuthData authData) {
        HttpClientBuilder builder = super.extend(httpClientBuilder, authData);
        builder.setSSLContext(trustAnyX509Certificate);
        builder.setSSLHostnameVerifier(acceptAnyX509Hostname);
        return builder;
    }
}
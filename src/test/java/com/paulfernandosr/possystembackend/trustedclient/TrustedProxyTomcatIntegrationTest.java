package com.paulfernandosr.possystembackend.trustedclient;

import com.paulfernandosr.possystembackend.devicecertificate.TestCertificates;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.connector.Connector;
import org.apache.tomcat.util.net.SSLHostConfig;
import org.apache.tomcat.util.net.SSLHostConfigCertificate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import jakarta.servlet.http.*;
import javax.net.ssl.*;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.*;
import org.bouncycastle.cert.jcajce.*;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import java.math.BigInteger;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.security.*;
import java.security.cert.X509Certificate;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** Real servlet TLS attributes, with ephemeral CA/keys and no application profiles or database. */
class TrustedProxyTomcatIntegrationTest {
    @TempDir Path temp;
    private static final char[] PASSWORD="synthetic-test-store".toCharArray();
    @Test void realTomcatRequiresClientTlsAndOnlyPinnedServiceMayForwardClientEvidence() throws Exception {
        var caKeys=TestCertificates.rsa(3072);var ca=TestCertificates.ca(caKeys,"Internal isolated CA");
        var proxyKeys=TestCertificates.rsa(3072);var proxy=TestCertificates.leaf(proxyKeys,ca,caKeys.getPrivate(),"urn:imbasac:service:nginx",true);
        var strangerKeys=TestCertificates.rsa(3072);var stranger=TestCertificates.leaf(strangerKeys,ca,caKeys.getPrivate(),"urn:imbasac:device:"+UUID.randomUUID(),true);
        var serverKeys=TestCertificates.rsa(3072);var server=server(serverKeys,ca,caKeys.getPrivate());
        var trusted=new TrustedClientResolver(true,TrustedClientResolver.fingerprint(proxy));
        Path identity=store("server.p12",serverKeys,server,ca);Path trust=trust(ca);
        Tomcat tomcat=new Tomcat();tomcat.setBaseDir(temp.resolve("tomcat").toString());
        Connector connector=new Connector("org.apache.coyote.http11.Http11NioProtocol");connector.setPort(0);connector.setScheme("https");connector.setSecure(true);connector.setProperty("SSLEnabled","true");
        SSLHostConfig ssl=new SSLHostConfig();ssl.setCertificateVerification("required");ssl.setTruststoreFile(trust.toString());ssl.setTruststoreType("PKCS12");ssl.setTruststorePassword(new String(PASSWORD));
        var cert=new SSLHostConfigCertificate(ssl,SSLHostConfigCertificate.Type.RSA);cert.setCertificateKeystoreFile(identity.toString());cert.setCertificateKeystoreType("PKCS12");cert.setCertificateKeystorePassword(new String(PASSWORD));ssl.addCertificate(cert);
        connector.addSslHostConfig(ssl);tomcat.getService().addConnector(connector);tomcat.setConnector(connector);
        var context=tomcat.addContext("",temp.toString());
        Tomcat.addServlet(context,"proof",new HttpServlet(){@Override protected void doGet(HttpServletRequest request,HttpServletResponse response) throws java.io.IOException {
            var result=trusted.resolve(request);response.setContentType("text/plain");response.getWriter().write(result.proxyAuthenticated()+"|"+result.clientIp()+"|"+result.certificateState());
        }});context.addServletMappingDecoded("/proof","proof");
        try {
            tomcat.start();URI uri=URI.create("https://localhost:"+connector.getLocalPort()+"/proof");
            var request=HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10)).header("X-Imbasac-Client-IP","203.0.113.7").header("X-Imbasac-Client-Verify","NONE").GET().build();
            var client=HttpClient.newBuilder().sslContext(sslContext(proxyKeys,proxy,ca)).connectTimeout(Duration.ofSeconds(5)).build();
            assertThat(client.send(request,HttpResponse.BodyHandlers.ofString()).body()).isEqualTo("true|203.0.113.7|NONE");
            var other=HttpClient.newBuilder().sslContext(sslContext(strangerKeys,stranger,ca)).connectTimeout(Duration.ofSeconds(5)).build();
            assertThat(other.send(request,HttpResponse.BodyHandlers.ofString()).body()).startsWith("false|127.0.0.1|UNTRUSTED");
            var anonymous=HttpClient.newBuilder().sslContext(sslContext(null,null,ca)).connectTimeout(Duration.ofSeconds(5)).build();
            assertThatThrownBy(()->anonymous.send(request,HttpResponse.BodyHandlers.ofString())).isInstanceOf(java.io.IOException.class);
        } finally { tomcat.stop();tomcat.destroy(); }
    }
    private X509Certificate server(KeyPair keys,X509Certificate issuer,PrivateKey issuerKey) throws Exception {
        var builder=new JcaX509v3CertificateBuilder(issuer,new BigInteger(120,new SecureRandom()).add(BigInteger.ONE),Date.from(Instant.now().minusSeconds(60)),Date.from(Instant.now().plusSeconds(3600)),new X500Name("CN=localhost"),keys.getPublic());
        builder.addExtension(Extension.basicConstraints,true,new BasicConstraints(false));builder.addExtension(Extension.keyUsage,true,new KeyUsage(KeyUsage.digitalSignature|KeyUsage.keyEncipherment));
        builder.addExtension(Extension.extendedKeyUsage,false,new ExtendedKeyUsage(KeyPurposeId.id_kp_serverAuth));builder.addExtension(Extension.subjectAlternativeName,false,new GeneralNames(new GeneralName(GeneralName.dNSName,"localhost")));
        return new JcaX509CertificateConverter().getCertificate(builder.build(new JcaContentSignerBuilder("SHA256withRSA").build(issuerKey)));
    }
    private Path store(String name,KeyPair keys,X509Certificate leaf,X509Certificate ca) throws Exception {
        var store=KeyStore.getInstance("PKCS12");store.load(null,PASSWORD);store.setKeyEntry("identity",keys.getPrivate(),PASSWORD,new java.security.cert.Certificate[]{leaf,ca});
        Path path=temp.resolve(name);try(var out=Files.newOutputStream(path)){store.store(out,PASSWORD);}return path;
    }
    private Path trust(X509Certificate ca) throws Exception {
        var store=KeyStore.getInstance("PKCS12");store.load(null,PASSWORD);store.setCertificateEntry("ca",ca);Path path=temp.resolve("trust.p12");try(var out=Files.newOutputStream(path)){store.store(out,PASSWORD);}return path;
    }
    private SSLContext sslContext(KeyPair keys,X509Certificate leaf,X509Certificate ca) throws Exception {
        KeyManager[] managers=null;
        if(keys!=null) {
            var store=KeyStore.getInstance("PKCS12");store.load(null,PASSWORD);store.setKeyEntry("identity",keys.getPrivate(),PASSWORD,new java.security.cert.Certificate[]{leaf,ca});
            var factory=KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());factory.init(store,PASSWORD);managers=factory.getKeyManagers();
        }
        var trust=KeyStore.getInstance("PKCS12");trust.load(null,PASSWORD);trust.setCertificateEntry("ca",ca);var factory=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());factory.init(trust);
        var ssl=SSLContext.getInstance("TLS");ssl.init(managers,factory.getTrustManagers(),null);return ssl;
    }
}

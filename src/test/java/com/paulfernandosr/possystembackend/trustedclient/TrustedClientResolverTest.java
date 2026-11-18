package com.paulfernandosr.possystembackend.trustedclient;

import com.paulfernandosr.possystembackend.accessaudit.infrastructure.AuditRequestMetadata;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.security.cert.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class TrustedClientResolverTest {
    private X509Certificate peer() throws Exception {
        var peer=mock(X509Certificate.class);
        when(peer.getEncoded()).thenReturn(new byte[]{1,2,3,4});when(peer.getBasicConstraints()).thenReturn(-1);
        when(peer.getExtendedKeyUsage()).thenReturn(List.of("1.3.6.1.5.5.7.3.2"));
        when(peer.getSubjectAlternativeNames()).thenReturn(List.of(List.of(6,"urn:imbasac:service:nginx")));
        return peer;
    }
    private MockHttpServletRequest request(X509Certificate peer) {
        var request=new MockHttpServletRequest();request.setRemoteAddr("127.0.0.1");request.setSecure(true);
        request.setAttribute(TrustedClientResolver.PEER_ATTRIBUTE,new X509Certificate[]{peer});
        request.addHeader("X-Imbasac-Client-IP","203.0.113.7");request.addHeader("X-Imbasac-Client-Verify","NONE");
        request.addHeader("X-Imbasac-Request-ID","0123456789abcdef0123456789abcdef");return request;
    }
    @Test void disabledFeatureDoesNotAcceptForgedHeadersEvenWithTls() throws Exception {
        var result=new TrustedClientResolver(false,"").resolve(request(peer()));
        assertThat(result.proxyAuthenticated()).isFalse();assertThat(result.clientIp()).isEqualTo("127.0.0.1");assertThat(result.certificate()).isNull();
    }
    @Test void onlyPinnedTlsPeerWithExpectedClientUsageAndServiceSanCanForwardEvidence() throws Exception {
        var peer=peer();var resolver=new TrustedClientResolver(true,TrustedClientResolver.fingerprint(peer));
        var result=resolver.resolve(request(peer));assertThat(result.proxyAuthenticated()).isTrue();
        assertThat(result.clientIp()).isEqualTo("203.0.113.7");assertThat(result.certificateState()).isEqualTo("NONE");
        assertThat(result.requestId()).isEqualTo(UUID.fromString("01234567-89ab-cdef-0123-456789abcdef"));
    }
    @Test void plainHttpCannotImpersonateProxyEvenWithClientCertificateAttribute() throws Exception {
        var peer=peer();var request=request(peer);request.setSecure(false);
        assertThat(new TrustedClientResolver(true,TrustedClientResolver.fingerprint(peer)).resolve(request).proxyAuthenticated()).isFalse();
    }
    @Test void differentCertificateCannotImpersonateProxy() throws Exception {
        assertThat(new TrustedClientResolver(true,"a".repeat(64)).resolve(request(peer())).proxyAuthenticated()).isFalse();
    }
    @Test void wrongServiceSanIsRejected() throws Exception {
        var peer=peer();when(peer.getSubjectAlternativeNames()).thenReturn(List.of(List.of(6,"urn:imbasac:device:any")));
        assertThat(new TrustedClientResolver(true,TrustedClientResolver.fingerprint(peer)).resolve(request(peer)).proxyAuthenticated()).isFalse();
    }
    @Test void missingClientUsageAndExpiredProxyAreRejected() throws Exception {
        var peer=peer();var resolver=new TrustedClientResolver(true,TrustedClientResolver.fingerprint(peer));
        when(peer.getExtendedKeyUsage()).thenReturn(List.of("1.3.6.1.5.5.7.3.1"));
        assertThat(resolver.resolve(request(peer)).proxyAuthenticated()).isFalse();
        when(peer.getExtendedKeyUsage()).thenReturn(List.of("1.3.6.1.5.5.7.3.2"));doThrow(new CertificateExpiredException()).when(peer).checkValidity();
        assertThat(resolver.resolve(request(peer)).proxyAuthenticated()).isFalse();
    }
    @Test void forwardedWrapperCannotFabricateTransportSecurity() throws Exception {
        var peer=peer();var raw=request(peer);raw.setSecure(false);
        var wrapped=new HttpServletRequestWrapper(raw) { @Override public boolean isSecure(){return true;} @Override public String getRemoteAddr(){return "203.0.113.8";} };
        var result=new TrustedClientResolver(true,TrustedClientResolver.fingerprint(peer)).resolve(wrapped);
        assertThat(result.proxyAuthenticated()).isFalse();assertThat(result.clientIp()).isEqualTo("127.0.0.1");
    }
    @Test void malformedDuplicateAddressesAreNotGeolocated() throws Exception {
        var peer=peer();var request=request(peer);request.addHeader("X-Imbasac-Client-IP","198.51.100.1");
        var result=new TrustedClientResolver(true,TrustedClientResolver.fingerprint(peer)).resolve(request);
        assertThat(result.clientIp()).isNull();assertThat(result.proxyAuthenticated()).isTrue();
    }
    @Test void successfulHeaderWithoutParseablePublicCertificateDoesNotBecomeProof() throws Exception {
        var peer=peer();var request=request(peer);request.removeHeader("X-Imbasac-Client-Verify");request.addHeader("X-Imbasac-Client-Verify","SUCCESS");
        request.addHeader("X-Imbasac-Client-Cert","arbitrary-browser-text");
        var result=new TrustedClientResolver(true,TrustedClientResolver.fingerprint(peer)).resolve(request);
        assertThat(result.certificate()).isNull();assertThat(result.certificateState()).isEqualTo("INVALID");
    }
    @Test void invalidEnabledConfigurationFailsStartupInsteadOfTrustingAllPeers() {
        assertThatThrownBy(()->new TrustedClientResolver(true,"")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new TrustedClientResolver(true,"not-a-certificate-pin")).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void numericIpParsingNeverAcceptsDnsListsPortsOrZoneIdentifiers() {
        for(String value:List.of("example.com","127.0.0.1:8091","203.0.113.1, 127.0.0.1","999.0.0.1","fe80::1%eth0","010.1.2.3","a")) assertThat(TrustedClientResolver.numericIp(value)).isNull();
        assertThat(TrustedClientResolver.numericIp("2001:db8::1")).isNotNull();
    }
    @Test void requestEvidenceIsStableWithinOneRequestAndUntrustedAddressesNeverReachGeoip() throws Exception {
        var resolver=new TrustedClientResolver(false,"");var geo=mock(GeoLocationService.class);var request=request(peer());
        var metadata=new AuditRequestMetadata(resolver,geo);
        assertThat(metadata.from(request)).isSameAs(metadata.from(request));assertThat(metadata.from(request).location()).isNull();verifyNoInteractions(geo);
    }
    @Test void geoipIsOptionalAndInvalidDatabaseCannotSilentlyPretendToBeActive() {
        var geo=new GeoLocationService(false,"");assertThat(geo.locate("203.0.113.1").status()).isEqualTo("DISABLED");
        assertThatThrownBy(()->new GeoLocationService(true,"this-file-does-not-exist.mmdb")).isInstanceOf(IllegalArgumentException.class);
    }
}

package com.paulfernandosr.possystembackend.devicecertificate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class ExternalCaSignerTest {
    @Test void defaultLinuxTransportPreservesTheProductionInvocation() {
        var invocation=ExternalCaSigner.invocationFor("linux-local","Linux",new String[]{"prod"},null);
        assertThat(invocation.command()).containsExactly("/usr/bin/sudo","-n","-u","imbasac-ca","/usr/local/lib/imbasac-ca/sign-request");
        assertThat(invocation.environment()).hasSize(1).containsEntry("PATH","/usr/bin:/bin");
        assertThat(invocation.directory()).isNull();
    }

    @Test void windowsLocalDemoUsesOnlyTheFixedWslInvocation() {
        var invocation=ExternalCaSigner.invocationFor("wsl-local-demo","Windows 11",new String[]{"local"},"C:\\Windows");
        assertThat(invocation.command()).containsExactly("C:\\Windows\\System32\\wsl.exe","--distribution","Ubuntu","--user","posapp","--cd","/","--exec",
                "/usr/bin/sudo","-n","-u","imbasac-ca","/usr/local/lib/imbasac-ca/sign-request");
        assertThat(invocation.environment()).hasSize(1).containsEntry("SystemRoot","C:\\Windows");
        assertThat(invocation.directory()).isEqualTo("C:\\Windows\\System32");
    }

    @Test void demoTransportCannotBeEnabledOutsideTheExplicitWindowsLocalProfile() {
        for(String[] profiles:new String[][]{{},{"prod"},{"local","prod"},{"local","production"}}) {
            assertThatIllegalArgumentException().isThrownBy(()->ExternalCaSigner.invocationFor("wsl-local-demo","Windows 11",profiles,"C:\\Windows"));
        }
        assertThatIllegalArgumentException().isThrownBy(()->ExternalCaSigner.invocationFor("wsl-local-demo","Linux",new String[]{"local"},"C:\\Windows"));
        assertThatIllegalArgumentException().isThrownBy(()->ExternalCaSigner.invocationFor("wsl-local-demo","Windows 11",new String[]{"local"},"Windows"));
        assertThatIllegalArgumentException().isThrownBy(()->ExternalCaSigner.invocationFor("http-remote","Linux",new String[]{"prod"},null));
    }

    @Test void alternateWindowsCsrLabelsAreNormalizedWithoutChangingTheSignedBytes() throws Exception {
        var mapper=new ObjectMapper();
        UUID request=UUID.randomUUID(),device=UUID.randomUUID();
        String syntheticCsr=TestCertificates.csr(TestCertificates.ec("secp256r1")).replace("\r\n","\n");
        for(String newline:List.of("\n","\r\n")) {
            String standard=syntheticCsr.replace("\n",newline);
            String alternate=standard.replace("CERTIFICATE REQUEST","NEW CERTIFICATE REQUEST");
            JsonNode standardPayload=mapper.valueToTree(ExternalCaSigner.signingRequest(request,device,standard));
            JsonNode alternatePayload=mapper.valueToTree(ExternalCaSigner.signingRequest(request,device,alternate));

            assertThat(standardPayload.path("csrPem").asText()).isEqualTo(standard);
            assertThat(alternatePayload).isEqualTo(standardPayload);
            assertThat(alternatePayload.path("requestId").asText()).isEqualTo(request.toString());
            assertThat(alternatePayload.path("deviceId").asText()).isEqualTo(device.toString());
            assertThat(csrDer(alternatePayload.path("csrPem").asText())).isEqualTo(csrDer(alternate));
        }
    }

    private static byte[] csrDer(String pem) throws Exception {
        try(var parser=new PEMParser(new StringReader(pem))) {
            return ((PKCS10CertificationRequest)parser.readObject()).getEncoded();
        }
    }
}

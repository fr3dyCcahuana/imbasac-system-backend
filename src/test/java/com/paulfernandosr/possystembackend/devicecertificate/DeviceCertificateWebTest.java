package com.paulfernandosr.possystembackend.devicecertificate;

import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class DeviceCertificateWebTest {
    private final DeviceCertificateService service=mock(DeviceCertificateService.class);
    private final MockMvc mvc=MockMvcBuilders.standaloneSetup(new DeviceCertificateRestController(service)).setControllerAdvice(new CertificateExceptionHandler()).build();
    private final UUID device=UUID.randomUUID();
    @AfterEach void cleanup(){SecurityContextHolder.clearContext();}
    private void permissions(String... names) { SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("admin",null,
            Arrays.stream(names).map(SimpleGrantedAuthority::new).toList())); }
    @Test void statusDistinguishesDeviceRegistrationFromDisabledCertificateOperations() throws Exception {
        permissions("VIEW_SECURITY_CONTROL");when(service.status()).thenReturn(new CertificateModels.Status(false,false,false,true,false,0,0,true,true));
        mvc.perform(get("/security/device-certificates/status")).andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false)).andExpect(jsonPath("$.registrationEnabled").value(true));
    }
    @Test void registrationStillRequiresItsIndependentAdministrativePermission() throws Exception {
        permissions("VIEW_SECURITY_CONTROL");mvc.perform(post("/security/device-certificates/devices").principal(()->"admin")
                .contentType("application/json").content("{\"displayName\":\"PC pendiente\",\"platform\":\"WINDOWS\",\"reason\":\"Registro solicitado por administrador\"}"))
                .andExpect(status().isForbidden());verifyNoInteractions(service);
    }
    @Test void deviceManagerCanRegisterAPendingComputerThroughTheExistingEndpoint() throws Exception {
        permissions("VIEW_SECURITY_CONTROL","MANAGE_SECURITY_DEVICES");
        when(service.createDevice(any(),eq("admin"))).thenReturn(new com.paulfernandosr.possystembackend.accesscontrol.domain.AccessControlModels.DeviceRow(device,"PC pendiente","WINDOWS","PENDING",java.time.Instant.now()));
        mvc.perform(post("/security/device-certificates/devices").principal(()->"admin").contentType("application/json")
                .content("{\"displayName\":\"PC pendiente\",\"platform\":\"WINDOWS\",\"reason\":\"Registro solicitado por administrador\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("PENDING"));
    }
    @Test void authenticationAloneCannotReadCertificateRegistry() throws Exception {
        permissions("MANAGE_USERS");mvc.perform(get("/security/device-certificates").param("deviceId",device.toString())).andExpect(status().isForbidden());verifyNoInteractions(service);
    }
    @Test void readerCanInspectOnlyPublicMetadataWithoutGettingCsrManagement() throws Exception {
        permissions("VIEW_SECURITY_CONTROL");when(service.certificates(device)).thenReturn(List.of());
        mvc.perform(get("/security/device-certificates").param("deviceId",device.toString())).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"));
        mvc.perform(get("/security/device-certificates/enrollments").param("deviceId",device.toString())).andExpect(status().isForbidden());
        verify(service,never()).enrollments(any());
    }
    @Test void deviceManagerCannotApproveHardwareOrChangeRolePolicy() throws Exception {
        permissions("VIEW_SECURITY_CONTROL","MANAGE_SECURITY_DEVICES");
        mvc.perform(put("/security/device-certificates/devices/"+device+"/hardware").principal(()->"admin").contentType("application/json")
                .content("{\"approved\":true,\"evidenceReference\":\"laboratorio\",\"reason\":\"Equipo homologado\"}")).andExpect(status().isForbidden());
        mvc.perform(put("/security/device-certificates/role-policies/5").principal(()->"admin").contentType("application/json")
                .content("{\"mode\":\"CERTIFICATE_REQUIRED\",\"reason\":\"Piloto de certificado\"}")).andExpect(status().isForbidden());verifyNoInteractions(service);
    }
    @Test void hardwarePermissionDoesNotGrantDeviceEnrollmentManagement() throws Exception {
        permissions("VIEW_SECURITY_CONTROL","MANAGE_SECURITY_HARDWARE");
        mvc.perform(post("/security/device-certificates/enrollments").principal(()->"admin").contentType("application/json")
                .content("{\"deviceId\":\""+device+"\",\"csrPem\":\"public csr\"}")).andExpect(status().isForbidden());verifyNoInteractions(service);
    }
    @Test void auditReaderCanReadLocationWithoutControlPermission() throws Exception {
        permissions("VIEW_SECURITY_AUDIT");UUID event=UUID.randomUUID();
        when(service.eventLocation(event)).thenReturn(new com.paulfernandosr.possystembackend.trustedclient.GeoLocationService.Location("UNKNOWN",null,null,null,null,null,null,null));
        mvc.perform(get("/security/device-certificates/event-location/"+event)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UNKNOWN"));
    }
    @Test void controlPermissionAloneDoesNotGrantAccessHistoryLocation() throws Exception {
        permissions("VIEW_SECURITY_CONTROL");mvc.perform(get("/security/device-certificates/event-location/"+UUID.randomUUID())).andExpect(status().isForbidden());verifyNoInteractions(service);
    }
    @Test void disabledAndUnavailableResponsesNeverReturnDatabaseOrCertificateSecrets() throws Exception {
        permissions("VIEW_SECURITY_CONTROL");when(service.certificates(device)).thenThrow(new CertificateException(CertificateException.Code.CERTIFICATE_UNAVAILABLE));
        mvc.perform(get("/security/device-certificates").param("deviceId",device.toString())).andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("CERTIFICATE_UNAVAILABLE"));
    }
    @Test void malformedIdentifierIs400AndCannotReachDatabase() throws Exception {
        permissions("VIEW_SECURITY_CONTROL");mvc.perform(get("/security/device-certificates").param("deviceId","not-a-device")).andExpect(status().isBadRequest());verifyNoInteractions(service);
    }
    @Test void managingDeviceMetadataDoesNotGrantCaSigningPermission() throws Exception {
        permissions("VIEW_SECURITY_CONTROL","MANAGE_SECURITY_DEVICES");
        mvc.perform(post("/security/device-certificates/enrollments/"+UUID.randomUUID()+"/issue").principal(()->"admin").contentType("application/json")
                .content("{\"reason\":\"Emision aprobada para piloto\"}")).andExpect(status().isForbidden());verifyNoInteractions(service);
    }
    @Test void readingCertificateMetadataDoesNotGrantCertificateDownloadManagement() throws Exception {
        permissions("VIEW_SECURITY_CONTROL");mvc.perform(get("/security/device-certificates/"+UUID.randomUUID()+"/download")).andExpect(status().isForbidden());verifyNoInteractions(service);
    }
}

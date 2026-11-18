package com.paulfernandosr.possystembackend.accesscontrol;

import com.paulfernandosr.possystembackend.accesscontrol.application.AccessControlService;
import com.paulfernandosr.possystembackend.accesscontrol.domain.*;
import com.paulfernandosr.possystembackend.accesscontrol.domain.AccessControlModels.Status;
import com.paulfernandosr.possystembackend.accesscontrol.infrastructure.*;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AccessControlWebTest {
    private final AccessControlService service=mock(AccessControlService.class);
    private MockMvc mvc;
    @BeforeEach void setup() { mvc=MockMvcBuilders.standaloneSetup(new AccessControlRestController(service)).setControllerAdvice(new AccessControlExceptionHandler()).build(); }
    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); }
    private void permissions(String... values) { SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("admin",null,Arrays.stream(values).map(SimpleGrantedAuthority::new).toList())); }
    @Test void unrelatedAdministratorCannotReadSecurityControl() throws Exception {
        permissions("MANAGE_USERS"); mvc.perform(get("/security/access-control/status")).andExpect(status().isForbidden()); verifyNoInteractions(service);
    }
    @Test void mutationPermissionWithoutReadDoesNotGrantReadOrWrite() throws Exception {
        permissions("MANAGE_SECURITY_BLOCKS"); mvc.perform(post("/security/access-control/users/1/unblock").contentType("application/json").content("{\"reason\":\"Revision administrativa\"}")).andExpect(status().isForbidden()); verifyNoInteractions(service);
    }
    @Test void readerCannotIncreaseQuotaOrReleaseBlocks() throws Exception {
        permissions("VIEW_SECURITY_CONTROL");
        mvc.perform(put("/security/access-control/users/1/quota").contentType("application/json").content("{\"deviceQuota\":2,\"reason\":\"Equipo adicional autorizado\"}")).andExpect(status().isForbidden());
        mvc.perform(post("/security/access-control/users/1/unblock").contentType("application/json").content("{\"reason\":\"Revision administrativa\"}")).andExpect(status().isForbidden()); verifyNoInteractions(service);
    }
    @Test void deviceManagerCannotModifyBlockSettings() throws Exception {
        permissions("VIEW_SECURITY_CONTROL","MANAGE_SECURITY_DEVICES");
        mvc.perform(put("/security/access-control/settings").contentType("application/json").content("{\"blockDays\":2,\"reason\":\"Nueva duracion aprobada\"}")).andExpect(status().isForbidden()); verifyNoInteractions(service);
    }
    @Test void statusAndReadsArePrivateAndNotCached() throws Exception {
        permissions("VIEW_SECURITY_CONTROL"); when(service.status()).thenReturn(new Status(false,3,1,1,"America/Lima"));
        mvc.perform(get("/security/access-control/status")).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andExpect(jsonPath("$.enabled").value(false));
    }
    @Test void malformedIdentifiersFailWithoutStorage() throws Exception {
        permissions("VIEW_SECURITY_CONTROL","MANAGE_SECURITY_DEVICES");
        mvc.perform(delete("/security/access-control/users/1/bindings/not-a-uuid").contentType("application/json").content("{\"reason\":\"Revision administrativa\"}")).andExpect(status().isBadRequest()); verifyNoInteractions(service);
    }
    @Test void readerCannotChangeAnyUsersIndividualCertificatePolicy() throws Exception {
        permissions("VIEW_SECURITY_CONTROL");
        mvc.perform(put("/security/access-control/users/3/certificate-policy").principal(()->"admin")
                .contentType("application/json").content("{\"certificateRequired\":true,\"reason\":\"Proteccion individual autorizada\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
    @Test void policyManagerTargetsOneAccountAndPreservesThePermissionBoundary() throws Exception {
        permissions("VIEW_SECURITY_CONTROL","MANAGE_SECURITY_POLICIES");
        when(service.certificateRequired(3L)).thenReturn(true);
        mvc.perform(put("/security/access-control/users/3/certificate-policy").principal(()->"admin")
                .contentType("application/json").content("{\"certificateRequired\":true,\"reason\":\"Proteccion individual autorizada\"}"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
                .andExpect(jsonPath("$.certificateRequired").value(true));
        verify(service).setCertificateRequired(3L,true,"admin","Proteccion individual autorizada");
    }
    @Test void quotaConflictIsReturnedWithoutLeakingSql() throws Exception {
        permissions("VIEW_SECURITY_CONTROL"); when(service.user(1)).thenThrow(new AccessControlException(AccessControlException.Code.ACCESS_CONTROL_UNAVAILABLE));
        mvc.perform(get("/security/access-control/users/1")).andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("ACCESS_CONTROL_UNAVAILABLE")).andExpect(header().string("Cache-Control","no-store"));
    }
}

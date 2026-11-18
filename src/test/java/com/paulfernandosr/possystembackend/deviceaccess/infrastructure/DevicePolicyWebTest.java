package com.paulfernandosr.possystembackend.deviceaccess.infrastructure;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paulfernandosr.possystembackend.deviceaccess.application.DeviceAccessPolicyService;
import com.paulfernandosr.possystembackend.deviceaccess.domain.*;
import com.paulfernandosr.possystembackend.deviceaccess.infrastructure.adapter.input.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
class DevicePolicyWebTest {
    private final DeviceAccessPolicyService policy=mock(DeviceAccessPolicyService.class);
    private final DevicePolicyFilter filter=new DevicePolicyFilter(policy,new ObjectMapper());
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    @Test void disabledFilterDoesNotChangeExistingRequests() throws Exception {
        authenticated(); var chain=new MockFilterChain();
        filter.doFilter(new MockHttpServletRequest(),new MockHttpServletResponse(),chain);
        assertThat(chain.getRequest()).isNotNull(); verify(policy,never()).checkUsername(anyString());
    }
    @Test void certificateDenialStopsRequestAndDoesNotAcceptSpoofedProxyHeaders() throws Exception {
        when(policy.isEnabled()).thenReturn(true); authenticated();
        doThrow(new DevicePolicyException(DevicePolicyException.Code.DEVICE_CERTIFICATE_CHANNEL_NOT_READY)).when(policy).checkUsername("user");
        var request=new MockHttpServletRequest();request.addHeader("X-SSL-Client-Verify","SUCCESS");request.addHeader("X-Client-Cert","fake");
        var response=new MockHttpServletResponse();var chain=new MockFilterChain();
        filter.doFilter(request,response,chain);
        assertThat(response.getStatus()).isEqualTo(403);assertThat(chain.getRequest()).isNull();
        assertThat(response.getContentAsString()).contains("DEVICE_CERTIFICATE_CHANNEL_NOT_READY");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
    @Test void repositoryOutageReturns503AndNeverContinuesToBusinessOperation() throws Exception {
        when(policy.isEnabled()).thenReturn(true);authenticated();
        doThrow(new DevicePolicyException(DevicePolicyException.Code.DEVICE_POLICY_UNAVAILABLE)).when(policy).checkUsername("user");
        var response=new MockHttpServletResponse();var chain=new MockFilterChain();
        filter.doFilter(new MockHttpServletRequest(),response,chain);
        assertThat(response.getStatus()).isEqualTo(503);assertThat(chain.getRequest()).isNull();
    }
    @Test void privateStatusUsesOnlyAuthenticatedIdentityAndCannotBeCached() throws Exception {
        when(policy.status("user")).thenReturn(new DevicePolicyStatus(DeviceAccessMode.LEGACY_BROWSER_HASH,false,false));
        var mvc=MockMvcBuilders.standaloneSetup(new DevicePolicyRestController(policy)).setControllerAdvice(new DevicePolicyExceptionHandler()).build();
        mvc.perform(get("/security/device-policy/me").principal(() -> "user").param("username","other"))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
            .andExpect(jsonPath("$.mode").value("LEGACY_BROWSER_HASH")).andExpect(jsonPath("$.certificateChannelReady").value(false));
        verify(policy).status("user");verify(policy,never()).status("other");
    }
    @Test void policyApiMapsDenialTo403InsteadOfTriggeringTokenRefresh() throws Exception {
        when(policy.status("user")).thenThrow(new DevicePolicyException(DevicePolicyException.Code.DEVICE_POLICY_NOT_CONFIGURED));
        var mvc=MockMvcBuilders.standaloneSetup(new DevicePolicyRestController(policy)).setControllerAdvice(new DevicePolicyExceptionHandler()).build();
        mvc.perform(get("/security/device-policy/me").principal(() -> "user"))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("DEVICE_POLICY_NOT_CONFIGURED"));
    }
    @Test void filterIsNotRegisteredTwiceOutsideSpringSecurity() {
        assertThat(new DevicePolicyWebConfig().devicePolicyServletRegistration(filter).isEnabled()).isFalse();
    }
    private void authenticated() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("user",null,List.of()));
    }
}

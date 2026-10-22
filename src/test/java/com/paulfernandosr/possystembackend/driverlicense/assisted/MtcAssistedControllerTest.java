package com.paulfernandosr.possystembackend.driverlicense.assisted;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static com.paulfernandosr.possystembackend.driverlicense.assisted.MtcAssistedDtos.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MtcAssistedControllerTest {
    @Test void requiresIdentityAndSendsUncacheableResponses() throws Exception {
        var service = mock(MtcAssistedService.class);
        when(service.status()).thenReturn(new Status(true, "https://licencias.mtc.gob.pe/assets/PoliticaPrivacidad.pdf", 180));
        var mvc = MockMvcBuilders.standaloneSetup(new MtcAssistedController(service)).build();
        mvc.perform(get("/driver-licenses/mtc/assisted/status")).andExpect(status().isUnauthorized());
        mvc.perform(get("/driver-licenses/mtc/assisted/status").principal(() -> "operator"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.payload.enabled").value(true));
        mvc.perform(post("/driver-licenses/mtc/assisted/sessions/id/answer").principal(() -> "operator")
                .contentType(MediaType.APPLICATION_JSON).content("{\"challengeId\":\"id\",\"imageIndex\":0,\"privacyAccepted\":false}"))
                .andExpect(status().isBadRequest());
        verify(service, never()).answer(anyString(), anyString(), any());
    }
}

package com.paulfernandosr.possystembackend.driverlicense.assisted;

import com.paulfernandosr.possystembackend.driverlicense.web.DriverLicenseMtcController;
import org.junit.jupiter.api.Test;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MtcRetiredPortalTest {
    @Test void oldClientsReceiveAMigrationMessageInsteadOfOpeningTheRetiredPortal() throws Exception {
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(new DriverLicenseMtcController()).build();
        mvc.perform(get("/driver-licenses/mtc/init")).andExpect(status().isGone()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.code").value("MTC_TEXT_CAPTCHA_RETIRED"));
        mvc.perform(post("/driver-licenses/mtc/check")).andExpect(status().isGone());
    }
}

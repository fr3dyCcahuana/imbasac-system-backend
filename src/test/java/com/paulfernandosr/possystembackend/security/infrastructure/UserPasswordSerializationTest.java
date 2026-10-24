package com.paulfernandosr.possystembackend.security.infrastructure;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paulfernandosr.possystembackend.user.domain.User;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class UserPasswordSerializationTest {
    private final ObjectMapper mapper = new ObjectMapper();
    @Test void userResponsesNeverExposePasswordButUpdatesStillAcceptIt() throws Exception {
        User user = User.builder().username("client").password("sensitive-hash").build();
        assertThat(mapper.readTree(mapper.writeValueAsString(user)).has("password")).isFalse();
        assertThat(mapper.readValue("{\"username\":\"client\",\"password\":\"new-hash\"}",User.class).getPassword()).isEqualTo("new-hash");
        assertThat(mapper.readValue("{\"username\":\"client\",\"password\":null}",User.class).getPassword()).isNull();
    }
}

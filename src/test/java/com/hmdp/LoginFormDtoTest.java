package com.hmdp;

import com.hmdp.dto.LoginFormDTO;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LoginFormDtoTest {

    @Test
    void jacksonCanDeserializeLoginRequest() throws Exception {
        LoginFormDTO form = new ObjectMapper().readValue(
                """
                        {
                          "phone": "13800138000",
                          "code": "123456",
                          "password": "secret"
                        }
                        """,
                LoginFormDTO.class);

        assertEquals("13800138000", form.getPhone());
        assertEquals("123456", form.getCode());
        assertEquals("secret", form.getPassword());
    }
}

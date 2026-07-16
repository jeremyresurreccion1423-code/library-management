package com.smartlibrary.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
class AdminLoginFailureRedirectTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void wrongAdminPassword_staysOnUnifiedLoginWithError() throws Exception {
        MockHttpSession session = new MockHttpSession();

        mockMvc.perform(get("/login").session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("login"));

        mockMvc.perform(post("/login")
                        .session(session)
                        .with(csrf())
                        .param("username", "admin")
                        .param("password", "definitely-wrong-password"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error=true"));
    }

    @Test
    void legacyAdminLoginUrl_redirectsToUnifiedLogin() throws Exception {
        mockMvc.perform(get("/admin/login"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }
}

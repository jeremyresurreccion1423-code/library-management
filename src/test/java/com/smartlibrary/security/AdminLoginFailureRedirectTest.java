package com.smartlibrary.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
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
    void wrongAdminPassword_staysOnAdminLoginWithError() throws Exception {
        MockHttpSession session = new MockHttpSession();

        mockMvc.perform(get("/admin/login").session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/login"));

        mockMvc.perform(post("/admin/login/process")
                        .session(session)
                        .with(csrf())
                        .param("username", "admin")
                        .param("password", "definitely-wrong-password")
                        .param("loginPortal", "admin"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/login?error=true"));
    }
}

package com.example.iam;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Checks the access rules without Keycloak running: the jwt() helper
 * pretends a valid token with the given role was already verified.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ApiSecurityTest {

    @Autowired
    MockMvc mvc;

    private static JwtRequestPostProcessor withRole(String role) {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Test
    void publicEndpointNeedsNoToken() throws Exception {
        mvc.perform(get("/api/public")).andExpect(status().isOk());
    }

    @Test
    void noTokenIs401() throws Exception {
        mvc.perform(get("/api/reports")).andExpect(status().isUnauthorized());
    }

    @Test
    void readerCanListReports() throws Exception {
        mvc.perform(get("/api/reports").with(withRole("reader"))).andExpect(status().isOk());
    }

    @Test
    void readerCannotCreateReports() throws Exception {
        mvc.perform(post("/api/reports").with(withRole("reader"))).andExpect(status().isForbidden());
    }

    @Test
    void editorCanCreateReports() throws Exception {
        mvc.perform(post("/api/reports").with(withRole("editor"))).andExpect(status().isCreated());
    }

    @Test
    void editorCannotDeleteReports() throws Exception {
        mvc.perform(delete("/api/reports/1").with(withRole("editor"))).andExpect(status().isForbidden());
    }

    @Test
    void adminCanDeleteReports() throws Exception {
        mvc.perform(delete("/api/reports/1").with(withRole("admin"))).andExpect(status().isOk());
    }
}

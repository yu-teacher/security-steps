package com.kdt.steps;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class StepTest {
    @Autowired MockMvc mvc;
    private final JsonMapper json = JsonMapper.builder().build();

    @Test
    void loginStoresMemberInSession() throws Exception {
        MockHttpSession session = login("kdt01");
        mvc.perform(get("/api/v1/auth/me").session(session))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.loginId").value("kdt01"));
    }

    @Test
    void wrongPasswordIs401() throws Exception {
        mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType("application/json")
                       .content("{\"loginId\":\"kdt01\",\"password\":\"wrong\"}"))
           .andExpect(status().isUnauthorized());
    }

    @Test
    void writeWithoutSessionIs401() throws Exception {
        mvc.perform(post("/api/v1/posts").with(csrf()).contentType("application/json")
                       .content("{\"title\":\"t\",\"body\":\"b\"}"))
           .andExpect(status().isUnauthorized());
    }

    @Test
    void authorComesFromSessionSoOtherMemberIs403() throws Exception {
        long id = createPost(login("kdt01"));
        mvc.perform(put("/api/v1/posts/" + id).session(login("kdt02")).with(csrf()).contentType("application/json")
                       .content("{\"loginId\":\"kdt01\",\"title\":\"x\",\"body\":\"x\"}"))
           .andExpect(status().isForbidden())
           .andExpect(jsonPath("$.message").value("not the owner"));
    }

    @Test
    void sessionCookieWithoutCsrfTokenIs403() throws Exception {
        mvc.perform(post("/api/v1/posts").session(login("kdt01")).contentType("application/json")
                       .content("{\"title\":\"t\",\"body\":\"b\"}"))
           .andExpect(status().isForbidden());
    }

    @Test
    void logoutEndsSession() throws Exception {
        MockHttpSession session = login("kdt01");
        mvc.perform(post("/api/v1/auth/logout").session(session).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/auth/me").session(session)).andExpect(status().isUnauthorized());
    }

    private MockHttpSession login(String loginId) throws Exception {
        return (MockHttpSession) mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType("application/json")
                                                .content("{\"loginId\":\"" + loginId + "\",\"password\":\"kdt1234\"}"))
                                    .andExpect(status().isOk())
                                    .andReturn().getRequest().getSession(false);
    }

    private long createPost(MockHttpSession session) throws Exception {
        String body = mvc.perform(post("/api/v1/posts").session(session).with(csrf()).contentType("application/json")
                                     .content("{\"title\":\"원본\",\"body\":\"본문\"}"))
                         .andExpect(status().isCreated())
                         .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asLong();
    }
}

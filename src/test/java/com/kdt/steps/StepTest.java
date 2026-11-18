package com.kdt.steps;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class StepTest {
    @Autowired MockMvc mvc;
    private final JsonMapper json = JsonMapper.builder().build();

    @Test
    void loginSetsHttpOnlySidCookie() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                       .content("{\"loginId\":\"kdt01\",\"password\":\"kdt1234\"}"))
           .andExpect(status().isOk())
           .andExpect(header().string("Set-Cookie", containsString("SID=")))
           .andExpect(header().string("Set-Cookie", containsString("HttpOnly")));
    }

    @Test
    void writeWithoutSidIs401() throws Exception {
        mvc.perform(post("/api/v1/posts").contentType("application/json").content("{\"title\":\"t\",\"body\":\"b\"}"))
           .andExpect(status().isUnauthorized());
    }

    @Test
    void authorComesFromServerMapSoOtherMemberIs403() throws Exception {
        long id = createPost(login("kdt01"));
        mvc.perform(put("/api/v1/posts/" + id).cookie(login("kdt02")).contentType("application/json")
                       .content("{\"loginId\":\"kdt01\",\"title\":\"x\",\"body\":\"x\"}"))
           .andExpect(status().isForbidden())
           .andExpect(jsonPath("$.message").value("not the owner"));
    }

    @Test
    void logoutRemovesSidFromServer() throws Exception {
        Cookie sid = login("kdt01");
        mvc.perform(post("/api/v1/auth/logout").cookie(sid))
           .andExpect(status().isNoContent())
           .andExpect(header().string("Set-Cookie", containsString("Max-Age=0")));
        mvc.perform(post("/api/v1/posts").cookie(sid).contentType("application/json")
                       .content("{\"title\":\"t\",\"body\":\"b\"}"))
           .andExpect(status().isUnauthorized());
    }

    private Cookie login(String loginId) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                              .content("{\"loginId\":\"" + loginId + "\",\"password\":\"kdt1234\"}"))
                  .andExpect(status().isOk())
                  .andReturn().getResponse().getCookie("SID");
    }

    private long createPost(Cookie sid) throws Exception {
        String body = mvc.perform(post("/api/v1/posts").cookie(sid).contentType("application/json")
                                     .content("{\"title\":\"원본\",\"body\":\"본문\"}"))
                         .andExpect(status().isCreated())
                         .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asLong();
    }
}

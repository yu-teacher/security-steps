package com.kdt.steps;

import com.kdt.steps.auth.JwtService;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class StepTest {
    @Autowired MockMvc mvc;
    @Autowired JwtService jwtService;
    private final JsonMapper json = JsonMapper.builder().build();

    @Test
    void tokenIdentifiesMemberWithoutSession() throws Exception {
        mvc.perform(get("/api/v1/auth/me").header("Authorization", bearer("kdt01")))
           .andExpect(status().isOk())
           .andExpect(header().doesNotExist("Set-Cookie"))
           .andExpect(jsonPath("$.loginId").value("kdt01"));
    }

    @Test
    void wrongPasswordIs401() throws Exception {
        mvc.perform(post("/api/v1/auth/token").contentType("application/json")
                       .content("{\"loginId\":\"kdt01\",\"password\":\"wrong\"}"))
           .andExpect(status().isUnauthorized());
    }

    @Test
    void missingOrBrokenBearerIs401() throws Exception {
        mvc.perform(post("/api/v1/posts").contentType("application/json").content("{\"title\":\"t\",\"body\":\"b\"}"))
           .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer broken"))
           .andExpect(status().isUnauthorized());
    }

    @Test
    void publicListIgnoresBrokenBearer() throws Exception {
        mvc.perform(get("/api/v1/posts").header("Authorization", "Bearer broken")).andExpect(status().isOk());
    }

    @Test
    void otherMemberIs403() throws Exception {
        long id = createPost(bearer("kdt01"));
        mvc.perform(put("/api/v1/posts/" + id).header("Authorization", bearer("kdt02")).contentType("application/json")
                       .content("{\"title\":\"x\",\"body\":\"x\"}"))
           .andExpect(status().isForbidden());
    }

    @Test
    void problem_singleTokenLivesForOneDay() throws Exception {
        String body = mvc.perform(post("/api/v1/auth/token").contentType("application/json")
                                     .content("{\"loginId\":\"kdt01\",\"password\":\"kdt1234\"}"))
                         .andExpect(status().isOk())
                         .andReturn().getResponse().getContentAsString();
        Claims claims = jwtService.parseAccess(json.readTree(body).get("accessToken").asString());
        long lifetime = (claims.getExpiration().getTime() - claims.getIssuedAt().getTime()) / 1000;
        assertEquals(86400, lifetime);
    }

    private String bearer(String loginId) {
        return "Bearer " + jwtService.createAccess(loginId);
    }

    private long createPost(String bearer) throws Exception {
        String body = mvc.perform(post("/api/v1/posts").header("Authorization", bearer).contentType("application/json")
                                     .content("{\"title\":\"원본\",\"body\":\"본문\"}"))
                         .andExpect(status().isCreated())
                         .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asLong();
    }
}

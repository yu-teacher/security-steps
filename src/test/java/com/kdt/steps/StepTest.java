package com.kdt.steps;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

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
    void loginChecksPassword() throws Exception {
        login("kdt01", "kdt1234").andExpect(status().isOk()).andExpect(jsonPath("$.loginId").value("kdt01"));
        login("kdt01", "wrong").andExpect(status().isUnauthorized());
    }

    @Test
    void honestOtherMemberIsForbidden() throws Exception {
        long id = createPost("kdt01");
        mvc.perform(put("/api/v1/posts/" + id).contentType("application/json")
                       .content("{\"loginId\":\"kdt02\",\"title\":\"x\",\"body\":\"x\"}"))
           .andExpect(status().isForbidden());
    }

    @Test
    void problem_anyoneCanClaimAnotherLoginId() throws Exception {
        long id = createPost("kdt01");
        mvc.perform(put("/api/v1/posts/" + id).contentType("application/json")
                       .content("{\"loginId\":\"kdt01\",\"title\":\"kdt02가 고침\",\"body\":\"비밀번호 없이\"}"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.title").value("kdt02가 고침"));
    }

    private org.springframework.test.web.servlet.ResultActions login(String loginId, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                              .content("{\"loginId\":\"" + loginId + "\",\"password\":\"" + password + "\"}"));
    }

    private long createPost(String loginId) throws Exception {
        String body = mvc.perform(post("/api/v1/posts").contentType("application/json")
                                     .content("{\"loginId\":\"" + loginId + "\",\"title\":\"원본\",\"body\":\"본문\"}"))
                         .andExpect(status().isCreated())
                         .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asLong();
    }
}

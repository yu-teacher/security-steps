package com.kdt.steps;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
    void anyoneCanWriteWithAnyAuthorName() throws Exception {
        mvc.perform(post("/api/v1/posts").contentType("application/json")
                       .content("{\"author\":\"아무개\",\"title\":\"t\",\"body\":\"b\"}"))
           .andExpect(status().isCreated())
           .andExpect(jsonPath("$.author").value("아무개"));
    }

    @Test
    void problem_anyoneCanEditOthersPost() throws Exception {
        long id = createPost("kdt01");
        mvc.perform(put("/api/v1/posts/" + id).contentType("application/json")
                       .content("{\"author\":\"kdt02\",\"title\":\"남이 고침\",\"body\":\"검증 없음\"}"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.title").value("남이 고침"));
    }

    @Test
    void problem_anyoneCanDeleteOthersPost() throws Exception {
        mvc.perform(delete("/api/v1/posts/" + createPost("kdt01"))).andExpect(status().isNoContent());
    }

    private long createPost(String author) throws Exception {
        String body = mvc.perform(post("/api/v1/posts").contentType("application/json")
                                     .content("{\"author\":\"" + author + "\",\"title\":\"원본\",\"body\":\"본문\"}"))
                         .andExpect(status().isCreated())
                         .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asLong();
    }
}

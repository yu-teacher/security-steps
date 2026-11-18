package com.kdt.steps;

import com.kdt.steps.auth.JwtService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import tools.jackson.databind.json.JsonMapper;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class StepTest {
    @Autowired MockMvc mvc;
    private static final String ORIGIN = "http://127.0.0.1:8000";

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
    void accessIsShortAndRefreshIsLong() throws Exception {
        TokenPair tokens = issue();
        assertEquals(900, lifetime(jwtService.parseAccess(tokens.access())));
        assertEquals(604800, lifetime(jwtService.parseRefresh(tokens.refresh())));
    }

    @Test
    void refreshCannotBeUsedAsBearer() throws Exception {
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + issue().refresh()))
           .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshIssuesNewPairButAccessCannotRefresh() throws Exception {
        TokenPair tokens = issue();
        mvc.perform(post("/api/v1/auth/token/refresh").contentType("application/json")
                       .content("{\"refreshToken\":\"" + tokens.refresh() + "\"}"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.accessToken").isNotEmpty())
           .andExpect(jsonPath("$.refreshToken").isNotEmpty());
        mvc.perform(post("/api/v1/auth/token/refresh").contentType("application/json")
                       .content("{\"refreshToken\":\"" + tokens.access() + "\"}"))
           .andExpect(status().isUnauthorized());
    }

    @Test
    void cookieLoginHidesRefreshFromJs() throws Exception {
        mvc.perform(post("/api/v1/auth/token-cookie").header("Origin", ORIGIN).contentType("application/json")
                       .content("{\"loginId\":\"kdt01\",\"password\":\"kdt1234\"}"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.accessToken").isNotEmpty())
           .andExpect(jsonPath("$.refreshToken").doesNotExist())
           .andExpect(header().string("Set-Cookie", containsString("refreshToken=")))
           .andExpect(header().string("Set-Cookie", containsString("HttpOnly")))
           .andExpect(header().string("Set-Cookie", containsString("Path=/api/v1/auth")))
           .andExpect(header().string("Set-Cookie", containsString("SameSite=Lax")));
    }

    @Test
    void refreshCookieIssuesNewAccessAndNewCookie() throws Exception {
        mvc.perform(post("/api/v1/auth/token/refresh-cookie").header("Origin", ORIGIN)
                       .cookie(new Cookie("refreshToken", issue().refresh())))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.accessToken").isNotEmpty())
           .andExpect(header().string("Set-Cookie", containsString("refreshToken=")));
    }

    @Test
    void refreshCookieMissingIs401AndLogoutClearsCookie() throws Exception {
        mvc.perform(post("/api/v1/auth/token/refresh-cookie").header("Origin", ORIGIN)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/logout-cookie").header("Origin", ORIGIN))
           .andExpect(status().isNoContent())
           .andExpect(header().string("Set-Cookie", containsString("Max-Age=0")));
    }

    @Test
    void cookieRequestFromOtherOriginIsRejectedByCors() throws Exception {
        mvc.perform(post("/api/v1/auth/token/refresh-cookie")
                       .header("Origin", "https://evil.example")
                       .cookie(new Cookie("refreshToken", issue().refresh())))
           .andExpect(status().isForbidden())
           .andExpect(header().doesNotExist("Set-Cookie"));
    }

    @Test
    void cookieRequestWithoutOriginIsRejectedByOriginCheck() throws Exception {
        mvc.perform(post("/api/v1/auth/token/refresh-cookie").cookie(new Cookie("refreshToken", issue().refresh())))
           .andExpect(status().isForbidden())
           .andExpect(jsonPath("$.message").value("origin not allowed"));
    }

    @Test
    void corsAllowsOnlyFrontOrigin() throws Exception {
        mvc.perform(options("/api/v1/posts")
                       .header("Origin", "http://127.0.0.1:8000")
                       .header("Access-Control-Request-Method", "POST"))
           .andExpect(status().isOk())
           .andExpect(header().string("Access-Control-Allow-Origin", "http://127.0.0.1:8000"));
        mvc.perform(options("/api/v1/posts")
                       .header("Origin", "https://evil.example")
                       .header("Access-Control-Request-Method", "POST"))
           .andExpect(status().isForbidden());
    }

    @Test
    void rotatedRefreshCannotBeReused() throws Exception {
        TokenPair first = issue();
        refresh(first.refresh()).andExpect(status().isOk());
        refresh(first.refresh()).andExpect(status().isUnauthorized());
    }

    @Test
    void reuseRevokesAllRefreshOfMember() throws Exception {
        TokenPair first = issue();
        String body = refresh(first.refresh()).andExpect(status().isOk())
                                             .andReturn().getResponse().getContentAsString();
        String second = json.readTree(body).get("refreshToken").asString();
        refresh(first.refresh()).andExpect(status().isUnauthorized());
        refresh(second).andExpect(status().isUnauthorized());
    }

    @Test
    void concurrentReuseLetsOnlyOneThrough() throws Exception {
        String refreshToken = issue().refresh();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> results = List.of(
                    pool.submit(() -> { start.await(); return refresh(refreshToken).andReturn().getResponse().getStatus(); }),
                    pool.submit(() -> { start.await(); return refresh(refreshToken).andReturn().getResponse().getStatus(); }));
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> result : results) {
                statuses.add(result.get());
            }
            assertEquals(List.of(200, 401), statuses.stream().sorted().toList());
        } finally {
            pool.shutdown();
        }
    }

    @Test
    void logoutRevokesRefreshOnServer() throws Exception {
        String refreshToken = issue().refresh();
        mvc.perform(post("/api/v1/auth/logout-cookie").header("Origin", ORIGIN)
                       .cookie(new Cookie("refreshToken", refreshToken)))
           .andExpect(status().isNoContent());
        refresh(refreshToken).andExpect(status().isUnauthorized());
    }

    private org.springframework.test.web.servlet.ResultActions refresh(String refreshToken) throws Exception {
        return mvc.perform(post("/api/v1/auth/token/refresh").contentType("application/json")
                              .content("{\"refreshToken\":\"" + refreshToken + "\"}"));
    }

    private record TokenPair(String access, String refresh) {
    }

    private TokenPair issue() throws Exception {
        String body = mvc.perform(post("/api/v1/auth/token").contentType("application/json")
                                     .content("{\"loginId\":\"kdt01\",\"password\":\"kdt1234\"}"))
                         .andExpect(status().isOk())
                         .andReturn().getResponse().getContentAsString();
        var node = json.readTree(body);
        return new TokenPair(node.get("accessToken").asString(), node.get("refreshToken").asString());
    }

    private long lifetime(Claims claims) {
        return (claims.getExpiration().getTime() - claims.getIssuedAt().getTime()) / 1000;
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

package com.kdt.steps.auth;

import com.kdt.steps.exception.UnauthorizedException;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SessionStore {
    private final Map<String, String> sessions = new ConcurrentHashMap<>();

    public String create(String loginId) {
        String sid = UUID.randomUUID().toString();
        sessions.put(sid, loginId);
        return sid;
    }

    public String loginId(String sid) {
        String loginId = sid == null ? null : sessions.get(sid);
        if (loginId == null) {
            throw new UnauthorizedException("login required");
        }
        return loginId;
    }

    public void remove(String sid) {
        if (sid != null) {
            sessions.remove(sid);
        }
    }
}

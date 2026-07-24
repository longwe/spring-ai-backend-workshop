package com.ezcloud;

import com.ezcloud.security.JwtService;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtServiceTest {

    private static final String SECRET =
            Base64.getEncoder().encodeToString("test-secret-key-that-is-long-enough-123456".getBytes());

    @Test
    void roundTripsSubject() {
        var jwtService = new JwtService(SECRET, 60);
        var token = jwtService.generateToken("alice", "USER");
        assertEquals("alice", jwtService.validateAndExtractUsername(token));
    }

    @Test
    void rejectsTamperedToken() {
        var jwtService = new JwtService(SECRET, 60);
        var token = jwtService.generateToken("alice", "USER");
        var tampered = token.substring(0, token.length() - 2) + "xx";
        assertThrows(JwtException.class, () -> jwtService.validateAndExtractUsername(tampered));
    }

    @Test
    void rejectsTokenSignedWithDifferentKey() {
        var otherSecret = Base64.getEncoder()
                .encodeToString("another-secret-key-that-is-long-enough-999".getBytes());
        var token = new JwtService(otherSecret, 60).generateToken("alice", "USER");
        var jwtService = new JwtService(SECRET, 60);
        assertThrows(JwtException.class, () -> jwtService.validateAndExtractUsername(token));
    }
}

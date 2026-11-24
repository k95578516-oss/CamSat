package com.example.camsat.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.camsat.config.AppProperties;
import com.example.camsat.domain.Role;
import com.example.camsat.domain.User;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class JwtServiceTest {
    private static final String SECRET = "unit-test-secret-unit-test-secret-0123456789";

    private static AppProperties props(String secret, boolean ephemeral) {
        return new AppProperties(new AppProperties.Jwt(secret, 3600, ephemeral), new AppProperties.Cors(java.util.List.of()),
                new AppProperties.Simulation(0, 1, 1, 1), new AppProperties.Telemetry(100));
    }

    private static User user() {
        User u = new User("Ada", "ada@example.com", "hash", Role.USER, Instant.now());
        ReflectionTestUtils.setField(u, "id", 42L);
        return u;
    }

    @Test
    void issuedTokenVerifiesAndCarriesIdentityFromTheSignedClaims() {
        JwtService jwt = new JwtService(props(SECRET, false));
        AuthUser au = jwt.verify(jwt.issue(user()));
        assertThat(au.id()).isEqualTo(42L);
        assertThat(au.email()).isEqualTo("ada@example.com");
        assertThat(au.role()).isEqualTo(Role.USER);
    }

    @Test
    void expiredTokenIsRejectedAsExpired() {
        JwtService jwt = new JwtService(props(SECRET, false));
        String old = jwt.issue(user(), Instant.now().minusSeconds(7200));
        assertThatThrownBy(() -> jwt.verify(old)).isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void tokenFromAnotherSecretOrTamperedTokenIsRejected() {
        JwtService a = new JwtService(props(SECRET, false));
        JwtService b = new JwtService(props("another-secret-another-secret-0123456789", false));
        assertThatThrownBy(() -> a.verify(b.issue(user()))).isInstanceOf(JwtException.class);
        String t = a.issue(user());
        String tampered = t.substring(0, t.length() - 2) + (t.endsWith("xx") ? "yy" : "xx");
        assertThatThrownBy(() -> a.verify(tampered)).isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> a.verify("garbage")).isInstanceOf(JwtException.class);
    }

    @Test
    void weakOrMissingSecretsFailFastUnlessEphemeralIsAllowed() {
        assertThatThrownBy(() -> new JwtService(props("short", false))).isInstanceOf(IllegalStateException.class).hasMessageNotContaining("short");
        assertThatThrownBy(() -> new JwtService(props("", false))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new JwtService(props(null, false))).isInstanceOf(IllegalStateException.class);
        JwtService dev = new JwtService(props("", true));
        assertThat(dev.verify(dev.issue(user())).id()).isEqualTo(42L);
    }
}

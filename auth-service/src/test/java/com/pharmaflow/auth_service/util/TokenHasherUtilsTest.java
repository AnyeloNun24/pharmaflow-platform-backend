package com.pharmaflow.auth_service.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TokenHasherUtils")
class TokenHasherUtilsTest {

    private final TokenHasherUtils tokenHasherUtils = new TokenHasherUtils();

    @Test
    @DisplayName("generateRawToken produce 32 bytes codificados en Base64Url sin padding")
    void generateRawToken_isBase64UrlWithoutPadding() {
        String raw = tokenHasherUtils.generateRawToken();

        assertThat(raw).doesNotContain("=", "+", "/");
        // 32 bytes -> 43 chars en Base64 sin padding.
        assertThat(raw).hasSize(43);
        byte[] decoded = Base64.getUrlDecoder().decode(raw);
        assertThat(decoded).hasSize(32);
    }

    @Test
    @DisplayName("generateRawToken no se repite entre invocaciones")
    void generateRawToken_isUnique() {
        Set<String> generated = new HashSet<>();
        for (int i = 0; i < 1_000; i++) {
            generated.add(tokenHasherUtils.generateRawToken());
        }
        assertThat(generated).hasSize(1_000);
    }

    @Test
    @DisplayName("sha256Hex es deterministico y coincide con el vector conocido")
    void sha256Hex_knownVector() {
        // SHA-256("abc") segun NIST FIPS 180-4.
        String expected = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";

        assertThat(tokenHasherUtils.sha256Hex("abc")).isEqualTo(expected);
        // Determinista: misma entrada -> misma salida.
        assertThat(tokenHasherUtils.sha256Hex("abc")).isEqualTo(tokenHasherUtils.sha256Hex("abc"));
    }

    @Test
    @DisplayName("sha256Hex devuelve 64 caracteres hexadecimales en minuscula")
    void sha256Hex_format() {
        String hash = tokenHasherUtils.sha256Hex(tokenHasherUtils.generateRawToken());

        assertThat(hash).hasSize(64);
        assertThat(hash).matches("[0-9a-f]{64}");
    }
}

package com.backandwhite;

import com.backandwhite.core.test.JwtTestUtil;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import com.nimbusds.jose.jwk.gen.OctetSequenceKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

@TestConfiguration
public class TestJwtConfiguration {

    private static final OctetSequenceKey SHARED_KEY;

    static {
        try {
            SHARED_KEY = new OctetSequenceKeyGenerator(256).keyID("test-key-id").algorithm(JWSAlgorithm.HS256)
                    .generate();
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @Bean
    public JwtEncoder jwtEncoder() {
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(SHARED_KEY)));
    }

    @Bean
    @Primary
    public JwtDecoder jwtDecoder() throws Exception {
        return NimbusJwtDecoder.withSecretKey(SHARED_KEY.toSecretKey()).build();
    }

    @Bean
    @Primary
    public JwtTestUtil jwtTestUtil(JwtEncoder encoder) {
        return new JwtTestUtil(encoder, MacAlgorithm.HS256);
    }
}

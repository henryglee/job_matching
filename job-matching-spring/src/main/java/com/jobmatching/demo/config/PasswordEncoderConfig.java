package com.jobmatching.demo.config;

import com.jobmatching.demo.security.WerkzeugPasswordEncoder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class PasswordEncoderConfig {

    // Must stay wire-compatible with the existing Flask app's Werkzeug password hashes
    // (see WerkzeugPasswordEncoder) — do not swap this for BCryptPasswordEncoder, it
    // would break login for every account created before the Spring Boot cutover.
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new WerkzeugPasswordEncoder();
    }
}

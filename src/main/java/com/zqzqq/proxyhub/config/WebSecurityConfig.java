package com.zqzqq.proxyhub.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import com.zqzqq.proxyhub.management.security.ManagementAccessFilter;

@Configuration
@EnableWebSecurity
public class WebSecurityConfig {

    private final ManagementAccessFilter managementAccessFilter;

    public WebSecurityConfig(ManagementAccessFilter managementAccessFilter) {
        this.managementAccessFilter = managementAccessFilter;
    }

    @Bean
    public SecurityFilterChain managementSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            // Insert our custom auth filter into the security chain
            .addFilterBefore(managementAccessFilter,
                    org.springframework.security.web.authentication.www.BasicAuthenticationFilter.class)
            // Permit management endpoints (our filter enforces auth)
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/api/v1/**", "/actuator/**", "/dashboard/**", "/").authenticated()
                    .anyRequest().permitAll())
            // Disable Spring Security's default Basic Auth
            .httpBasic(custom -> custom.disable())
            .formLogin(form -> form.disable());

        return http.build();
    }
}

package br.com.monitor.security;

import java.nio.charset.StandardCharsets;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CsrfFilter;

@Configuration(proxyBeanMethods = false)
public class AdministrativeSecurityConfiguration {
    @Bean
    AuthenticationManager administrativeAuthenticationManager(Environment environment) {
        String username = environment.getProperty("ADMIN_USERNAME");
        String password = environment.getProperty("ADMIN_PASSWORD");
        if (invalid(username) || invalid(password) || username.length() > 128
                || username.indexOf(':') >= 0 || username.chars().anyMatch(Character::isISOControl)
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalStateException("Administrative credentials are missing or invalid");
        }
        var encoder = new BCryptPasswordEncoder();
        var users = new InMemoryUserDetailsManager(User.withUsername(username)
                .password(encoder.encode(password)).roles("ADMIN").build());
        var provider = new DaoAuthenticationProvider(users);
        provider.setPasswordEncoder(encoder);
        return new ProviderManager(provider);
    }

    private boolean invalid(String value) {
        return value == null || value.isBlank() || value.startsWith("<") && value.endsWith(">");
    }

    @Bean
    SecurityFilterChain administrativeSecurity(HttpSecurity http, AuthenticationManager manager) throws Exception {
        AuthenticationEntryPoint unauthorized = (request, response, exception) -> {
            response.setHeader("WWW-Authenticate", "Basic realm=\"monitor-admin\"");
            response.setStatus(401);
        };
        AccessDeniedHandler denied = (request, response, exception) -> {
            var authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !authentication.isAuthenticated()) {
                unauthorized.commence(request, response, null);
            } else {
                response.setStatus(403);
            }
        };
        http.authenticationManager(manager)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(unauthorized)
                        .accessDeniedHandler(denied))
                .csrf(Customizer.withDefaults())
                // Authenticate writes before CSRF so invalid/missing Basic always produces 401.
                .addFilterBefore(new BasicAuthenticationFilter(manager, unauthorized), CsrfFilter.class)
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/liveness",
                                "/actuator/health/readiness").permitAll()
                        .requestMatchers(HttpMethod.HEAD, "/actuator/health", "/actuator/health/liveness",
                                "/actuator/health/readiness").permitAll()
                        .anyRequest().hasRole("ADMIN"));
        return http.build();
    }
}

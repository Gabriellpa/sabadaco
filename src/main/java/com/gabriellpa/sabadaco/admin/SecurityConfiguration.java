package com.gabriellpa.sabadaco.admin;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.RequestMatcher;

import java.util.UUID;

/**
 * Login simples: um usuário admin vindo de {@code ADMIN_USER}/{@code ADMIN_PASSWORD}.
 * Formulário para o painel e basic auth para o Prometheus coletar métricas.
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(SecurityConfiguration.AdminProperties.class)
public class SecurityConfiguration {

    @ConfigurationProperties(prefix = "sabadaco.admin")
    public record AdminProperties(String username, String password) {
    }

    /**
     * O {@code /actuator/prometheus} fica livre só na porta de gerenciamento ({@code MANAGEMENT_PORT}, 8081),
     * que não sai do cluster: o Prometheus coleta sem credencial e o painel (8080) continua com login.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, Environment environment) throws Exception {
        // Lido a cada requisição: a porta real só é conhecida depois que o servidor sobe
        RequestMatcher scrape = request -> "/actuator/prometheus".equals(request.getRequestURI())
                && request.getLocalPort() == environment.getProperty("local.management.port", Integer.class, -1);
        return http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(scrape).permitAll()
                        .requestMatchers("/actuator/health", "/css/**", "/js/**", "/webjars/**").permitAll()
                        .anyRequest().authenticated())
                .formLogin(form -> form.defaultSuccessUrl("/admin", true))
                .httpBasic(Customizer.withDefaults())
                .logout(Customizer.withDefaults())
                .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService(AdminProperties properties, PasswordEncoder encoder) {
        var password = properties.password();
        if (password == null || password.isBlank()) {
            password = UUID.randomUUID().toString();
            log.warn("ADMIN_PASSWORD não definido. Senha temporária do painel (usuário {}): {}", properties.username(), password);
        }
        return new InMemoryUserDetailsManager(User.withUsername(properties.username())
                .password(encoder.encode(password))
                .roles("ADMIN")
                .build());
    }
}

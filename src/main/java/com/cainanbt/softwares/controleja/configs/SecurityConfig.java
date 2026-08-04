package com.cainanbt.softwares.controleja.configs;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
public class SecurityConfig {

    private final String URL_API = "/controle_ja_api/v1";
    private final String URL_LOGIN_API = URL_API+"/auth";
    private final String URL_LOGIN_API_GOOGLE = URL_LOGIN_API + "/google";
    private final String URL_LOGIN_API_AUTO = URL_LOGIN_API + "/auto-login";
    private final String URL_REGISTER_API = URL_API+"/users/register";
    private final String URL_HEALTH_API = URL_API + "/health";
    private final String URL_HEALTH = "/health";
    private final String URL_ACTUATOR_HEALTH = "/actuator/health";
    private final String URL_SWAGGER_v3_DOCS = "/v3/api-docs";
    private final String URL_SWAGGER_v3 = "/v3/api-docs/**";
    private final String URL_SWAGGER_UI = "/swagger-ui/**";
    private final String URL_SWAGGER_HTML = "/swagger-ui.html";
    private final String URL_SWAGGER_RESOURCES = "/swagger-resources/**";
    private final String URL_WEBJARS = "/webjars/**";

    @Autowired
    private SecurityFilter securityFilter;

    @Value("${CORS_ALLOWED_ORIGINS:http://localhost:*,http://127.0.0.1:*,http://192.168.100.103:*}")
    private String corsAllowedOrigins;

    private final String[] PUBLIC = {
            URL_LOGIN_API,
            URL_REGISTER_API,
            URL_LOGIN_API_GOOGLE,
            URL_LOGIN_API_AUTO,
            URL_HEALTH_API,
            URL_HEALTH,
            URL_ACTUATOR_HEALTH,
            URL_SWAGGER_v3_DOCS,
            URL_SWAGGER_v3,
            URL_SWAGGER_UI,
            URL_SWAGGER_HTML,
            URL_SWAGGER_RESOURCES,
            URL_WEBJARS
    };
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(resolveAllowedOriginPatterns());
        configuration.setAllowedMethods(List.of(
                HttpMethod.GET.name(),
                HttpMethod.POST.name(),
                HttpMethod.PUT.name(),
                HttpMethod.PATCH.name(),
                HttpMethod.DELETE.name(),
                HttpMethod.OPTIONS.name()
        ));
        configuration.setAllowedHeaders(List.of(
                HttpHeaders.AUTHORIZATION,
                HttpHeaders.CONTENT_TYPE,
                HttpHeaders.ACCEPT,
                CorrelationId.HEADER_NAME
        ));
        configuration.setExposedHeaders(List.of(CorrelationId.HEADER_NAME));
        configuration.setAllowCredentials(false);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    private List<String> resolveAllowedOriginPatterns() {
        return Arrays.stream(corsAllowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isBlank())
                .filter(origin -> !origin.equals("*"))
                .toList();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint((request, response, authException) ->
                                SecurityErrorResponseWriter.writeUnauthorized(request, response))
                        .accessDeniedHandler((request, response, accessDeniedException) ->
                                SecurityErrorResponseWriter.writeForbidden(request, response)))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(PUBLIC).permitAll()
                        .anyRequest().authenticated())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .addFilterBefore(securityFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}

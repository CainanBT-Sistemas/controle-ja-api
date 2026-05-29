package com.cainanbt.softwares.controleja.configs;

import com.cainanbt.softwares.controleja.dtos.UserAuthenticateDTO;
import com.cainanbt.softwares.controleja.repositories.UsersRepository;
import com.cainanbt.softwares.controleja.services.impl.JwtServiceImp;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@Component
public class SecurityFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final List<String> PUBLIC_ROUTES = Arrays.asList(
            "/controle_ja_api/v1/auth",
            "/controle_ja_api/v1/auth/google",
            "/controle_ja_api/v1/auth/auto-login",
            "/controle_ja_api/v1/users/register",
            "/controle_ja_api/v1/health",
            "/health",
            "/actuator/health",
            "/v3/api-docs",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/swagger-resources/**",
            "/webjars/**"
    );

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    private final JwtServiceImp tokenService;

    private final UsersRepository userRepository;

    public SecurityFilter(JwtServiceImp tokenService, UsersRepository userRepository) {
        this.tokenService = tokenService;
        this.userRepository = userRepository;
    }


    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        try {
            var token = this.recoverToken(request);
            if (token != null) {
                var login = tokenService.validateToken(token);
                var user = userRepository.findByEmailIgnoreCase(login)
                        .orElseThrow(() -> new JwtException("User not found or inactive"));
                UserAuthenticateDTO userAuthenticateDTO = new UserAuthenticateDTO(user);

                if (!isValidUser(userAuthenticateDTO)) {
                    throw new JwtException("User not found or inactive");
                }

                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                userAuthenticateDTO,
                                null,
                                userAuthenticateDTO.getAuthorities())
                );
            }
        } catch (Exception ex) {
            SecurityContextHolder.clearContext();
            SecurityErrorResponseWriter.writeUnauthorized(response);
            return;
        }
        filterChain.doFilter(request,response);
    }

    private String recoverToken(HttpServletRequest request) {
        var authHeader = request.getHeader("Authorization");
        if(authHeader==null)
            return null;
        if (!authHeader.startsWith(BEARER_PREFIX)) {
            throw new JwtException("Missing bearer token");
        }
        var token = authHeader.substring(BEARER_PREFIX.length()).trim();
        if (token.isBlank()) {
            throw new JwtException("Missing bearer token");
        }
        return token;
    }

    private boolean isValidUser(UserAuthenticateDTO userAuthenticateDTO) {
        return userAuthenticateDTO.isEnabled()
                && userAuthenticateDTO.isAccountNonExpired()
                && userAuthenticateDTO.isAccountNonLocked()
                && userAuthenticateDTO.isCredentialsNonExpired();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }

        String path = request.getServletPath();
        return PUBLIC_ROUTES.stream().anyMatch(route -> pathMatcher.match(route, path));
    }
}

package com.cainanbt.softwares.controleja.configs;

import com.cainanbt.softwares.controleja.dtos.UserAuthenticateDTO;
import com.cainanbt.softwares.controleja.repositories.UsersRepository;
import com.cainanbt.softwares.controleja.services.impl.JwtServiceImp;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class SecurityFilter extends OncePerRequestFilter {


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
                if (login != null) {
                    userRepository.findByEmailIgnoreCase(login).ifPresent(user -> {
                        UserAuthenticateDTO userAuthenticateDTO = new UserAuthenticateDTO(user);
                        SecurityContextHolder.getContext().setAuthentication(
                                new UsernamePasswordAuthenticationToken(
                                        userAuthenticateDTO,
                                        null,
                                        userAuthenticateDTO.getAuthorities())
                        );
                    });
                }
            }
        } catch (Exception ex) {
            System.out.println("Token inválido ou expirado ignorado: " + ex.getMessage());
        }
        filterChain.doFilter(request,response);
    }

    private String recoverToken(HttpServletRequest request) {
        var authHeader = request.getHeader("Authorization");
        if(authHeader==null)
            return null;
        return authHeader.replace("Bearer ","");
    }
}

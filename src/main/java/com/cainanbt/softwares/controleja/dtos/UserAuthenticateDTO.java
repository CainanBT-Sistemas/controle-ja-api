package com.cainanbt.softwares.controleja.dtos;

import com.cainanbt.softwares.controleja.entities.Users;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Arrays;
import java.util.Collection;

@Getter
public class UserAuthenticateDTO implements UserDetails {

    private final Users users;

    public UserAuthenticateDTO(Users user){
        this.users = user;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Arrays.asList(new RoleAdapterDTO(users.getRole()));
    }

    @Override
    public String getPassword() {
        return users.getPassword();
    }

    @Override
    public String getUsername() {
        return users.getEmail();
    }

    @Override
    public boolean isAccountNonExpired() {
        return users.getAccountNonExpired();
    }

    @Override
    public boolean isAccountNonLocked() {
        return users.getAccountNonLocked();
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return users.getCredentialsNonExpired();
    }

    @Override
    public boolean isEnabled() {
        return users.getEnabled();
    }

    public Users getUser(){
        return users;
    }
}

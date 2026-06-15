package com.sumicare.auth.service;

import com.sumicare.user.domain.User;
import com.sumicare.user.repository.UserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    public UserDetailsServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Unknown user"));
        if (user.getPasswordHash() == null) {
            throw new UsernameNotFoundException("Account setup is not complete");
        }
        if (user.getRole() == null || user.getRole().getCode() == null) {
            throw new UsernameNotFoundException("Account has no role assigned");
        }
        String roleCode = user.getRole().getCode();
        return new org.springframework.security.core.userdetails.User(
                user.getUsername(),
                user.getPasswordHash(),
                user.isActive(),
                true,
                true,
                !user.isAccountLocked(),
                Collections.singleton(new SimpleGrantedAuthority("ROLE_" + roleCode))
        );
    }
}

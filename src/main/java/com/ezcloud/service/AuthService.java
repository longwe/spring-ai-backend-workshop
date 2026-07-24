package com.ezcloud.service;

import com.ezcloud.domain.Role;
import com.ezcloud.domain.User;
import com.ezcloud.dto.AuthDtos.AuthResponse;
import com.ezcloud.dto.AuthDtos.LoginRequest;
import com.ezcloud.dto.AuthDtos.RegisterRequest;
import com.ezcloud.repository.UserRepository;
import com.ezcloud.security.JwtService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       AuthenticationManager authenticationManager) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new IllegalArgumentException("username_taken");
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("email_taken");
        }
        var user = new User(request.username(), request.email(),
                passwordEncoder.encode(request.password()), Role.USER);
        userRepository.save(user);
        var token = jwtService.generateToken(user.getUsername(), user.getRole().name());
        return new AuthResponse(token, user.getUsername(), user.getRole().name());
    }

    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password()));
        var user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> new IllegalArgumentException("user_not_found"));
        var token = jwtService.generateToken(user.getUsername(), user.getRole().name());
        return new AuthResponse(token, user.getUsername(), user.getRole().name());
    }
}

package com.nexusapi.service.impl;

import com.nexusapi.dto.request.LoginRequest;
import com.nexusapi.dto.request.RegisterRequest;
import com.nexusapi.dto.response.AuthResponse;
import com.nexusapi.dto.response.UserResponse;
import com.nexusapi.entity.User;
import com.nexusapi.exception.ConflictException;
import com.nexusapi.mapper.TaskMapper;
import com.nexusapi.repository.UserRepository;
import com.nexusapi.security.JwtService;
import com.nexusapi.service.AuthService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final TaskMapper taskMapper;

    public AuthServiceImpl(UserRepository userRepository,
                           PasswordEncoder passwordEncoder,
                           JwtService jwtService,
                           AuthenticationManager authenticationManager,
                           TaskMapper taskMapper) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
        this.taskMapper = taskMapper;
    }

    @Override
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmailAndDeletedAtIsNull(request.getEmail())) {
            throw new ConflictException("Email already registered: " + request.getEmail());
        }
        User user = new User();
        user.setDisplayName(request.getDisplayName());
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        userRepository.save(user);

        String token = jwtService.generateToken(user);
        UserResponse userResponse = taskMapper.toUserResponse(user);
        return new AuthResponse(token, jwtService.getExpirationMs() / 1000, userResponse);
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );
        User user = userRepository.findByEmailAndDeletedAtIsNull(request.getEmail())
            .orElseThrow(() -> new RuntimeException("User not found"));
        String token = jwtService.generateToken(user);
        UserResponse userResponse = taskMapper.toUserResponse(user);
        return new AuthResponse(token, jwtService.getExpirationMs() / 1000, userResponse);
    }
}
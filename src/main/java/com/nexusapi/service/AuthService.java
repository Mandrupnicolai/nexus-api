package com.nexusapi.service;

import com.nexusapi.dto.request.LoginRequest;
import com.nexusapi.dto.request.RegisterRequest;
import com.nexusapi.dto.response.AuthResponse;

public interface AuthService {
    AuthResponse register(RegisterRequest request);
    AuthResponse login(LoginRequest request);
}
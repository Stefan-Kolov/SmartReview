package com.smartreview.smartreview.service;

import com.smartreview.smartreview.model.dto.LoginRequest;
import com.smartreview.smartreview.model.dto.RegisterRequest;
import com.smartreview.smartreview.model.dto.AuthResponse;

public interface AuthService {
    AuthResponse register(RegisterRequest request);
    AuthResponse login(LoginRequest request);
}
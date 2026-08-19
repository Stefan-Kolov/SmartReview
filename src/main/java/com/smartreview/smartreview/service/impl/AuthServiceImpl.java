package com.smartreview.smartreview.service.impl;

import com.smartreview.smartreview.config.JwtService;
import com.smartreview.smartreview.model.User;
import com.smartreview.smartreview.model.dto.LoginRequest;
import com.smartreview.smartreview.model.dto.RegisterRequest;
import com.smartreview.smartreview.model.dto.AuthResponse;
import com.smartreview.smartreview.model.exceptions.EmailAlreadyExistsException;
import com.smartreview.smartreview.model.exceptions.InvalidCredentialsException;
import com.smartreview.smartreview.model.exceptions.UsernameAlreadyExistsException;
import com.smartreview.smartreview.repository.UserRepository;
import com.smartreview.smartreview.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Override
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.findByUsername(request.getUsername()).isPresent()) {
            throw new UsernameAlreadyExistsException("Username is already taken.");
        }

        if (userRepository.findByEmail(request.getEmail()).isPresent()){
            throw new EmailAlreadyExistsException("Email is already taken.");
        }

        User user = User.builder()
                .username(request.getUsername())
                .name(request.getName())
                .surname(request.getSurname())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .build();

        userRepository.save(user);
        String token = jwtService.generateToken(user.getUsername());
        return new AuthResponse(token, user.getUsername());
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new InvalidCredentialsException("Invalid username or password."));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new InvalidCredentialsException("Invalid username or password.");
        }

        String token = jwtService.generateToken(user.getUsername());
        return new AuthResponse(token, user.getUsername());
    }
}
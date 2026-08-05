package com.smartreview.smartreview.service.impl;

import com.smartreview.smartreview.model.User;
import com.smartreview.smartreview.model.dto.ChangePasswordRequest;
import com.smartreview.smartreview.model.dto.UpdateProfileRequest;
import com.smartreview.smartreview.model.dto.UserResponse;
import com.smartreview.smartreview.repository.UserRepository;
import com.smartreview.smartreview.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public UserResponse getProfile(User user) {
        return UserResponse.from(user);
    }

    @Override
    @Transactional
    public UserResponse updateProfile(User user, UpdateProfileRequest request) {
        user.setName(request.getName());
        user.setSurname(request.getSurname());
        user.setEmail(request.getEmail());
        userRepository.save(user);
        return UserResponse.from(user);
    }

    @Override
    @Transactional
    public void changePassword(User user, ChangePasswordRequest request) {
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Current password is incorrect.");
        }
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }
}
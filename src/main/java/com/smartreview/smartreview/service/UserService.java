package com.smartreview.smartreview.service;

import com.smartreview.smartreview.model.User;
import com.smartreview.smartreview.model.dto.ChangePasswordRequest;
import com.smartreview.smartreview.model.dto.UpdateProfileRequest;
import com.smartreview.smartreview.model.dto.UserResponse;

public interface UserService {
    UserResponse getProfile(User user);
    UserResponse updateProfile(User user, UpdateProfileRequest request);
    void changePassword(User user, ChangePasswordRequest request);
}
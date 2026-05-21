package com.smartreview.smartreview.model.dto;

import lombok.Data;

@Data
public class LoginRequest {
    private String username;
    private String password;
}
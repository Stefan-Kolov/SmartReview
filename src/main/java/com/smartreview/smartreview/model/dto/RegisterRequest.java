package com.smartreview.smartreview.model.dto;

import lombok.Data;

@Data
public class RegisterRequest {
    private String name;
    private String surname;
    private String username;
    private String email;
    private String password;
}
package com.timeverse.backend.dto;

import lombok.Data;

@Data
public class VerifyLoginOtpRequest {

    private String email;
    private String otp;
}
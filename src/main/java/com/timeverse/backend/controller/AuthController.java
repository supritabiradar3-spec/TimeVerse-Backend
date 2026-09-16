package com.timeverse.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.timeverse.backend.dto.ApiResponse;
import com.timeverse.backend.dto.ChangePasswordRequest;
import com.timeverse.backend.dto.ForgotPasswordRequest;
import com.timeverse.backend.dto.LoginRequest;
import com.timeverse.backend.dto.LoginResponse;
import com.timeverse.backend.dto.RegisterRequest;
import com.timeverse.backend.dto.ResetPasswordRequest;
import com.timeverse.backend.dto.VerifyLoginOtpRequest;
import com.timeverse.backend.dto.VerifyOtpRequest;
import com.timeverse.backend.entity.User;
import com.timeverse.backend.service.UserService;

import jakarta.validation.Valid;


@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {
    private final UserService userService;


    public AuthController(UserService userService) {
        this.userService = userService;
    }



    // ================= REGISTER =================

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<User>> register(
            @Valid @RequestBody RegisterRequest request) {


        User registeredUser = userService.registerUser(request);
        registeredUser.setPassword(null);


        return ResponseEntity.ok(
                ApiResponse.success(
                null,
                registeredUser));
    }





    // ================= LOGIN =================

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @Valid @RequestBody LoginRequest request) {


        LoginResponse response =
                userService.loginUser(request);


        return ResponseEntity.ok(
                ApiResponse.success(
                null,
                response));
    }





    // ================= VERIFY LOGIN OTP =================

    @PostMapping("/verify-login-otp")
    public ResponseEntity<ApiResponse<LoginResponse>> verifyLoginOtp(
            @Valid @RequestBody VerifyLoginOtpRequest request) {


        LoginResponse response =
                userService.verifyLoginOtp(
                        request.getEmail(),
                        request.getOtp()
                );


        return ResponseEntity.ok(
                ApiResponse.success(
                null,
                response));
    }





    // ================= LOGOUT =================

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @RequestHeader(
            value = "Authorization",
            required = false) String authHeader) {


        userService.logoutUser(authHeader);


        return ResponseEntity.ok(
                ApiResponse.success(
                null));
    }





    // ================= FORGOT PASSWORD =================

    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request) {


        userService.forgotPassword(request);


        return ResponseEntity.ok(
                ApiResponse.success(
                null));
    }





    // ================= VERIFY PASSWORD OTP =================

    @PostMapping("/verify-otp")
    public ResponseEntity<ApiResponse<Void>> verifyOtp(
            @Valid @RequestBody VerifyOtpRequest request) {


        userService.verifyOtp(request);


        return ResponseEntity.ok(
                ApiResponse.success(
                null));
    }





    // ================= RESET PASSWORD =================

    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request) {


        userService.resetPassword(request);


        return ResponseEntity.ok(
                ApiResponse.success(
                null));
    }





    // ================= CHANGE PASSWORD =================

    @PostMapping("/change-password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @Valid @RequestBody ChangePasswordRequest request) {


        userService.changePassword(request);


        return ResponseEntity.ok(
                ApiResponse.success(
                null));
    }

    // ================= GET USER PROFILE =================
    @GetMapping("/profile/{userId}")
    public ResponseEntity<ApiResponse<User>> getUserProfile(
            @PathVariable Long userId) {
        User user = userService.getUserById(userId);
        user.setPassword(null);
        return ResponseEntity.ok(
                ApiResponse.success(
                null,
                user));
    }

    // ================= UPDATE USER PROFILE =================
    @PutMapping("/profile/{userId}")
    public ResponseEntity<ApiResponse<User>> updateUserProfile(
            @PathVariable Long userId,
            @RequestBody User profileDetails) {
        User updatedUser = userService.updateUserProfile(userId, profileDetails);
        updatedUser.setPassword(null);
        return ResponseEntity.ok(
                ApiResponse.success(
                null,
                updatedUser));
    }

    // ================= DELETE USER ACCOUNT =================
    @DeleteMapping("/delete/{userId}")
    public ResponseEntity<ApiResponse<Void>> deleteUserAccount(@PathVariable Long userId) {
        userService.deleteUser(userId);
        return ResponseEntity.ok(
                ApiResponse.success(null));
    }
    // ================= GET ALL USERS =================

@GetMapping("/users")
public ResponseEntity<ApiResponse<java.util.List<User>>> getAllUsers() {

    java.util.List<User> users = userService.getAllUsers();

    users.forEach(user -> user.setPassword(null));

    return ResponseEntity.ok(
            ApiResponse.success(
                    null,
                    users));
}

    // ================= VERIFY REGISTRATION OTP =================
    @PostMapping("/verify-registration-otp")
    public ResponseEntity<ApiResponse<Void>> verifyRegistrationOtp(
            @Valid @RequestBody VerifyLoginOtpRequest request) {

        userService.verifyRegistrationOtp(request.getEmail(), request.getOtp());
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    // ================= RESEND REGISTRATION OTP =================
    @PostMapping("/resend-registration-otp")
    public ResponseEntity<ApiResponse<Void>> resendRegistrationOtp(
            @RequestBody ForgotPasswordRequest request) {

        userService.resendRegistrationOtp(request.getEmail());
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
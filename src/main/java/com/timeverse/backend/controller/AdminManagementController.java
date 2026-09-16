package com.timeverse.backend.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.timeverse.backend.dto.ApiResponse;
import com.timeverse.backend.dto.CreateAdminRequest;
import com.timeverse.backend.dto.TodayEarningsResponse;
import com.timeverse.backend.entity.User;
import com.timeverse.backend.service.AdminManagementService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping({"/api/admin", "/api/admin-mgmt"})
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class AdminManagementController {

    private final AdminManagementService adminManagementService;

    // ================= CREATE NEW ADMIN =================
    @PostMapping("/admins")
    public ResponseEntity<ApiResponse<User>> createAdmin(
            @Valid @RequestBody CreateAdminRequest request) {
        User createdAdmin = adminManagementService.createAdmin(request);
        return ResponseEntity.ok(
                ApiResponse.success("Admin created successfully", createdAdmin));
    }

    // ================= GET ALL ADMINS =================
    @GetMapping("/admins")
    public ResponseEntity<ApiResponse<List<User>>> getAllAdmins() {
        List<User> admins = adminManagementService.getAllAdmins();
        return ResponseEntity.ok(
                ApiResponse.success("Admins retrieved successfully", admins));
    }

    // ================= GET TODAY'S REAL STORE-WIDE EARNINGS =================
    @GetMapping("/today-earnings")
    public ResponseEntity<ApiResponse<TodayEarningsResponse>> getTodayEarnings() {
        TodayEarningsResponse response = adminManagementService.getTodayEarnings();
        return ResponseEntity.ok(
                ApiResponse.success("Today's earnings retrieved successfully", response));
    }
}

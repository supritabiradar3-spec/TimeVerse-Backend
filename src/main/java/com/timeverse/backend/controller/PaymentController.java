package com.timeverse.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.timeverse.backend.dto.PaymentRequest;
import com.timeverse.backend.dto.PaymentResponse;
import com.timeverse.backend.service.PaymentService;
import com.timeverse.backend.service.JwtService;

import lombok.RequiredArgsConstructor;


@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class PaymentController {


    private final PaymentService paymentService;
    private final JwtService jwtService;



    // Create Payment

    @PostMapping("/create/{userId}")
    public ResponseEntity<PaymentResponse> createPayment(
            @PathVariable Long userId,
            @RequestBody PaymentRequest request) {


        PaymentResponse response =
                paymentService.createPayment(userId, request);


        return ResponseEntity.ok(response);
    }





    // Get Payment By Order ID

    @GetMapping("/order/{orderId}")
    public ResponseEntity<PaymentResponse> getPaymentByOrder(
            @PathVariable Long orderId) {


        PaymentResponse response =
                paymentService.getPaymentByOrderId(orderId);


        return ResponseEntity.ok(response);
    }





    // Update Payment Status (Admin)

    @PutMapping("/{paymentId}/status")
    public ResponseEntity<PaymentResponse> updatePaymentStatus(
            @PathVariable Long paymentId,
            @RequestParam String status,
            @RequestHeader("Authorization") String authHeader) {

        String token = authHeader != null && authHeader.startsWith("Bearer ")
                ? authHeader.substring(7) : null;
        Long requesterId = token != null ? jwtService.extractUserId(token) : null;
        String role = token != null ? jwtService.extractRole(token) : null;

        PaymentResponse response =
                paymentService.updatePaymentStatus(paymentId, status, requesterId, role);


        return ResponseEntity.ok(response);
    }

}

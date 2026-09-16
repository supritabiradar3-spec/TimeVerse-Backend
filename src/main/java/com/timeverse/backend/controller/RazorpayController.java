package com.timeverse.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.timeverse.backend.dto.CreateOrderRequest;
import com.timeverse.backend.dto.RazorpayOrderResponse;
import com.timeverse.backend.dto.VerifyPaymentRequest;
import com.timeverse.backend.service.RazorpayService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class RazorpayController {

    private final RazorpayService razorpayService;

    // Create Razorpay Order
    @PostMapping("/create-order")
    public ResponseEntity<RazorpayOrderResponse> createOrder(
            @RequestBody CreateOrderRequest request) throws Exception {

        RazorpayOrderResponse response =
                razorpayService.createRazorpayOrder(request);

        return ResponseEntity.ok(response);
    }

    // Verify Razorpay Payment
    @PostMapping("/verify")
    public ResponseEntity<?> verifyPayment(
            @RequestBody VerifyPaymentRequest request) throws Exception {

        boolean verified =
                razorpayService.verifyPayment(request);

        if (verified) {
            return ResponseEntity.noContent().build();
        }

        return ResponseEntity.badRequest().body("Payment Verification Failed");
    }
}

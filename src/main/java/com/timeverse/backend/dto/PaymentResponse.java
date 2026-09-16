package com.timeverse.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentResponse {

    private Long paymentId;

    private Long orderId;

    private Long userId;

    private BigDecimal amount;

    private String paymentMethod;

    private String paymentStatus;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private String fullName;
    private String email;
    private String mobile;
    private String street;
    private String city;
    private String zip;
}
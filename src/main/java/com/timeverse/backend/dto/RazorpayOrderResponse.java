package com.timeverse.backend.dto;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RazorpayOrderResponse {

    private Long paymentId;

    private Long orderId;

    private String razorpayOrderId;

    private String razorpayKey;

    private BigDecimal amount;

    private String currency;

    private String status;

}
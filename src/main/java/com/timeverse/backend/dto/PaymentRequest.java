package com.timeverse.backend.dto;

import java.math.BigDecimal;

import lombok.Data;

@Data
public class PaymentRequest {

    private Long orderId;

    private BigDecimal amount;

    private String paymentMethod;


    // Customer Shipping Address

    private String fullName;

    private String email;

    private String mobile;

    private String street;

    private String city;

    private String zip;

}
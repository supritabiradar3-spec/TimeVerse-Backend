package com.timeverse.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import com.timeverse.backend.entity.Address;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderResponse {

    private Long orderId;

    private Long userId;

    private String customerName;

    private String customerEmail;

    private BigDecimal totalAmount;

    private String status;

    private LocalDateTime createdAt;

    private List<OrderItemResponse> items;

    private String cancellationReason;

    private String refundStatus;

    private BigDecimal refundAmount;

    private BigDecimal discountAmount;

    private String couponCode;

    private BigDecimal shippingCharge;

    private BigDecimal taxAmount;

    private Address shippingAddress;
    private String paymentStatus;
    private String paymentMethod;
}
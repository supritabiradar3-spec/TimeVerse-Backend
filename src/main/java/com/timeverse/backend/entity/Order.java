package com.timeverse.backend.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long orderId;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private BigDecimal totalAmount;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "cancellation_reason")
    private String cancellationReason;

    @Column(name = "refund_status")
    private String refundStatus;

    @Column(name = "refund_amount")
    private BigDecimal refundAmount;

    @Column(name = "discount_amount")
    private BigDecimal discountAmount;

    @Column(name = "coupon_code")
    private String couponCode;

    @Column(name = "shipping_charge")
    private BigDecimal shippingCharge;

    @Column(name = "tax_amount")
    private BigDecimal taxAmount;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "address_id")
    private Address shippingAddress;
}
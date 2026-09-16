package com.timeverse.backend.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.timeverse.backend.dto.PaymentRequest;
import com.timeverse.backend.dto.PaymentResponse;
import com.timeverse.backend.entity.Order;
import com.timeverse.backend.entity.Payment;
import com.timeverse.backend.entity.OrderItem;
import com.timeverse.backend.entity.Cart;
import com.timeverse.backend.entity.Product;
import com.timeverse.backend.repository.OrderRepository;
import com.timeverse.backend.repository.PaymentRepository;
import com.timeverse.backend.repository.OrderItemRepository;
import com.timeverse.backend.repository.CartRepository;
import com.timeverse.backend.repository.ProductRepository;

import lombok.RequiredArgsConstructor;


@Service
@Transactional
@RequiredArgsConstructor
public class PaymentService {


    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final CartRepository cartRepository;
    private final ProductRepository productRepository;



    // Create Payment

    public PaymentResponse createPayment(
            Long userId,
            PaymentRequest request) {


        Order order = orderRepository.findById(request.getOrderId())
                .orElseThrow(() ->
                new RuntimeException("Order not found"));



        Payment payment = Payment.builder()
                .orderId(order.getOrderId())
                .userId(userId)
                .amount(order.getTotalAmount())
                .paymentMethod(request.getPaymentMethod())
                .paymentStatus("PENDING")
                .fullName(request.getFullName())
                .email(request.getEmail())
                .mobile(request.getMobile())
                .street(request.getStreet())
                .city(request.getCity())
                .zip(request.getZip())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();



        paymentRepository.save(payment);



        return mapToResponse(payment);
    }




    // Get Payment By Order

    public PaymentResponse getPaymentByOrderId(Long orderId) {
        List<Payment> payments = paymentRepository.findByOrderIdOrderByPaymentIdDesc(orderId);
        Payment payment = payments.stream()
                .filter(p -> p.getPaymentStatus() != null &&
                        ("SUCCESS".equalsIgnoreCase(p.getPaymentStatus()) ||
                         "PAID".equalsIgnoreCase(p.getPaymentStatus()) ||
                         "COMPLETED".equalsIgnoreCase(p.getPaymentStatus())))
                .findFirst()
                .orElse(payments.isEmpty() ? null : payments.get(0));

        if (payment == null) {
            throw new RuntimeException("Payment not found");
        }

        return mapToResponse(payment);
    }





    // Update Payment Status

    public PaymentResponse updatePaymentStatus(
            Long paymentId,
            String status,
            Long requesterId,
            String role) {

        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() ->
                new RuntimeException("Payment not found"));

        boolean admin = "ADMIN".equalsIgnoreCase(role);
        if (!admin) {
            if (requesterId == null || !requesterId.equals(payment.getUserId())) {
                throw new RuntimeException("You can only update your own payment");
            }
            if (!"FAILED".equalsIgnoreCase(status) && !"CANCELLED".equalsIgnoreCase(status)) {
                throw new RuntimeException("Customers cannot set successful payment status");
            }
        }



        if ("FAILED".equalsIgnoreCase(status) || "CANCELLED".equalsIgnoreCase(status)) {
            if ("CREATED".equalsIgnoreCase(payment.getPaymentStatus()) || "PENDING".equalsIgnoreCase(payment.getPaymentStatus())) {
                Order order = orderRepository.findById(payment.getOrderId()).orElse(null);
                if (order != null && "PLACED".equalsIgnoreCase(order.getStatus())) {
                    order.setStatus("CANCELLED");
                    orderRepository.save(order);

                    List<OrderItem> items = orderItemRepository.findByOrderId(order.getOrderId());
                    for (OrderItem item : items) {
                        Product product = productRepository.findById(item.getProductId()).orElse(null);
                        if (product != null) {
                            product.setStock(product.getStock() + item.getQuantity());
                            productRepository.save(product);
                        }

                        Cart cart = cartRepository.findByUserIdAndProductId(payment.getUserId(), item.getProductId()).orElse(null);
                        if (cart != null) {
                            cart.setQuantity(cart.getQuantity() + item.getQuantity());
                        } else {
                            cart = Cart.builder()
                                    .userId(payment.getUserId())
                                    .productId(item.getProductId())
                                    .quantity(item.getQuantity())
                                    .createdAt(LocalDateTime.now())
                                    .build();
                        }
                        cartRepository.save(cart);
                    }
                }
            }
        }

        payment.setPaymentStatus(status);
        payment.setUpdatedAt(LocalDateTime.now());

        paymentRepository.save(payment);



        return mapToResponse(payment);
    }





    private PaymentResponse mapToResponse(Payment payment) {


        return PaymentResponse.builder()
                .paymentId(payment.getPaymentId())
                .orderId(payment.getOrderId())
                .userId(payment.getUserId())
                .amount(payment.getAmount())
                .paymentMethod(payment.getPaymentMethod())
                .paymentStatus(payment.getPaymentStatus())
                .createdAt(payment.getCreatedAt())
                .updatedAt(payment.getUpdatedAt())
                .fullName(payment.getFullName())
                .email(payment.getEmail())
                .mobile(payment.getMobile())
                .street(payment.getStreet())
                .city(payment.getCity())
                .zip(payment.getZip())
                .build();

    }

}

package com.timeverse.backend.service;

import java.nio.charset.StandardCharsets;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.timeverse.backend.dto.CreateOrderRequest;
import com.timeverse.backend.dto.RazorpayOrderResponse;
import com.timeverse.backend.dto.VerifyPaymentRequest;
import com.timeverse.backend.entity.Payment;
import com.timeverse.backend.repository.PaymentRepository;
import com.timeverse.backend.repository.OrderRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RazorpayService {

    private final RazorpayClient razorpayClient;
    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;

    @Value("${razorpay.key.id}")
    private String keyId;

    @Value("${razorpay.key.secret}")
    private String keySecret;

    public RazorpayOrderResponse createRazorpayOrder(CreateOrderRequest request) throws Exception {

        com.timeverse.backend.entity.Order dbOrder = orderRepository.findById(request.getOrderId())
                .orElseThrow(() -> new RuntimeException("Order not found"));

        if (!dbOrder.getUserId().equals(request.getUserId())) {
            throw new RuntimeException("Order does not belong to this user");
        }

        if (dbOrder.getTotalAmount().compareTo(request.getAmount()) != 0) {
            throw new RuntimeException("Payment amount does not match the order total");
        }

        JSONObject options = new JSONObject();

        // Amount in paise
        options.put("amount",
                request.getAmount()
                        .multiply(java.math.BigDecimal.valueOf(100))
                        .intValue());

        options.put("currency", "INR");
        options.put("receipt", "receipt_" + request.getOrderId());

        Order razorpayOrder = razorpayClient.orders.create(options);

        Payment payment = Payment.builder()
                .orderId(request.getOrderId())
                .userId(request.getUserId())
                .amount(request.getAmount())
                .paymentMethod("RAZORPAY")
                .paymentStatus("CREATED")
                .razorpayOrderId(razorpayOrder.get("id"))
                .fullName(request.getFullName())
                .email(request.getEmail())
                .mobile(request.getMobile())
                .street(request.getStreet())
                .city(request.getCity())
                .zip(request.getZip())
                .build();

        System.out.println("======================================");
        System.out.println("Payment Method : " + payment.getPaymentMethod());
        System.out.println("Order Id       : " + payment.getOrderId());
        System.out.println("User Id        : " + payment.getUserId());
        System.out.println("Amount         : " + payment.getAmount());
        System.out.println("======================================");

        payment = paymentRepository.save(payment);

        return RazorpayOrderResponse.builder()
                .paymentId(payment.getPaymentId())
                .orderId(payment.getOrderId())
                .razorpayOrderId(payment.getRazorpayOrderId())
                .razorpayKey(keyId)
                .amount(payment.getAmount())
                .currency("INR")
                .status(payment.getPaymentStatus())
                .build();
    }

    public boolean verifyPayment(VerifyPaymentRequest request) throws Exception {

        String data = request.getRazorpayOrderId()
                + "|"
                + request.getRazorpayPaymentId();

        Mac sha256Hmac = Mac.getInstance("HmacSHA256");

        SecretKeySpec secretKey = new SecretKeySpec(
                keySecret.getBytes(StandardCharsets.UTF_8),
                "HmacSHA256");

        sha256Hmac.init(secretKey);

        byte[] hash = sha256Hmac.doFinal(
                data.getBytes(StandardCharsets.UTF_8));

        StringBuilder generatedSignature = new StringBuilder();

        for (byte b : hash) {
            generatedSignature.append(String.format("%02x", b));
        }

        if (generatedSignature.toString().equals(request.getRazorpaySignature())) {

            Payment payment = paymentRepository.findById(request.getPaymentId())
                    .orElseThrow(() -> new RuntimeException("Payment not found"));

            if (!request.getRazorpayOrderId().equals(payment.getRazorpayOrderId())) {
                throw new RuntimeException("Payment order verification failed");
            }

            payment.setPaymentStatus("SUCCESS");
            payment.setRazorpayPaymentId(request.getRazorpayPaymentId());
            payment.setRazorpaySignature(request.getRazorpaySignature());

            paymentRepository.save(payment);

            com.timeverse.backend.entity.Order dbOrder = orderRepository.findById(payment.getOrderId())
                    .orElseThrow(() -> new RuntimeException("Order not found for ID: " + payment.getOrderId()));
            dbOrder.setStatus("CONFIRMED");
            orderRepository.save(dbOrder);

            return true;
        }

        return false;
    }
}
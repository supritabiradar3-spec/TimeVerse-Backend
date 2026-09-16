package com.timeverse.backend.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.timeverse.backend.dto.CreateAdminRequest;
import com.timeverse.backend.dto.OrderResponse;
import com.timeverse.backend.dto.TodayEarningsResponse;
import com.timeverse.backend.entity.Order;
import com.timeverse.backend.entity.Payment;
import com.timeverse.backend.entity.User;
import com.timeverse.backend.exception.BadRequestException;
import com.timeverse.backend.repository.OrderRepository;
import com.timeverse.backend.repository.PaymentRepository;
import com.timeverse.backend.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@Transactional
@RequiredArgsConstructor
public class AdminManagementService {

    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final OrderService orderService;
    private final PasswordEncoder passwordEncoder;

    // ================= CREATE ADMIN =================
    public User createAdmin(CreateAdminRequest request) {
        if (request.getFullName() == null || request.getFullName().trim().isEmpty()) {
            throw new BadRequestException("Full name is required");
        }

        if (request.getEmail() == null || request.getEmail().trim().isEmpty()) {
            throw new BadRequestException("Email is required");
        }

        String cleanedEmail = request.getEmail().toLowerCase().trim();
        if (userRepository.existsByEmail(cleanedEmail)) {
            throw new BadRequestException("Email already belongs to an existing user");
        }

        if (request.getPassword() == null || request.getPassword().length() < 6) {
            throw new BadRequestException("Password must be at least 6 characters");
        }

        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new BadRequestException("Confirm Password does not match Password");
        }

        // Generate a clean, unique username for the new admin
        String baseUsername = request.getFullName().trim().replaceAll("[^a-zA-Z0-9]", "_").toLowerCase();
        if (baseUsername.isEmpty()) {
            baseUsername = cleanedEmail.split("@")[0].replaceAll("[^a-zA-Z0-9]", "_").toLowerCase();
        }
        String username = baseUsername;
        int counter = 1;
        while (userRepository.existsByUsername(username)) {
            username = baseUsername + "_" + counter;
            counter++;
        }

        String encodedPassword = passwordEncoder.encode(request.getPassword());

        User adminUser = User.builder()
                .fullName(request.getFullName().trim())
                .username(username)
                .email(cleanedEmail)
                .password(encodedPassword)
                .role("ADMIN")
                .emailVerified(true)
                .firstLoginOtpVerified(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        return userRepository.save(adminUser);
    }

    // ================= GET ALL ADMINS =================
    @Transactional(readOnly = true)
    public List<User> getAllAdmins() {
        return userRepository.findByRoleIgnoreCase("ADMIN");
    }

    // ================= GET TODAY'S EARNINGS =================
    @Transactional(readOnly = true)
    public TodayEarningsResponse getTodayEarnings() {
        ZoneId zoneId = ZoneId.of("Asia/Kolkata");
        LocalDate today = LocalDate.now(zoneId);
        LocalDateTime startOfDay = today.atStartOfDay();
        LocalDateTime endOfDay = today.plusDays(1).atStartOfDay();

        List<Order> ordersToday = orderRepository.findByCreatedAtBetween(startOfDay, endOfDay);

        BigDecimal totalEarnings = BigDecimal.ZERO;
        List<OrderResponse> paidOrdersList = new ArrayList<>();

        for (Order order : ordersToday) {
            String status = order.getStatus() != null ? order.getStatus().toUpperCase() : "";
            String refundStatus = order.getRefundStatus() != null ? order.getRefundStatus().toUpperCase() : "";

            // Strictly exclude cancelled orders
            if ("CANCELLED".equals(status)) {
                continue;
            }

            // Only count orders placed by actual CUSTOMER users
            Optional<User> userOpt = userRepository.findById(order.getUserId());
            if (userOpt.isEmpty() || !"CUSTOMER".equalsIgnoreCase(userOpt.get().getRole())) {
                continue;
            }

            List<Payment> payments = paymentRepository.findByOrderIdOrderByPaymentIdDesc(order.getOrderId());
            Payment authPayment = payments.stream()
                    .filter(p -> p.getPaymentStatus() != null &&
                            ("SUCCESS".equalsIgnoreCase(p.getPaymentStatus()) ||
                             "PAID".equalsIgnoreCase(p.getPaymentStatus()) ||
                             "COMPLETED".equalsIgnoreCase(p.getPaymentStatus())))
                    .findFirst()
                    .orElse(payments.isEmpty() ? null : payments.get(0));

            if (authPayment == null) {
                continue;
            }

            String payStatus = authPayment.getPaymentStatus() != null ? authPayment.getPaymentStatus().toUpperCase() : "";

            boolean isPaymentSuccessful = "SUCCESS".equals(payStatus) || "PAID".equals(payStatus) || "COMPLETED".equals(payStatus);

            if (!isPaymentSuccessful) {
                continue;
            }

            BigDecimal amount = authPayment.getAmount() != null ? authPayment.getAmount() : (order.getTotalAmount() != null ? order.getTotalAmount() : BigDecimal.ZERO);
            if ("REFUNDED".equals(refundStatus)) {
                BigDecimal refundAmount = order.getRefundAmount() != null ? order.getRefundAmount() : BigDecimal.ZERO;
                if (refundAmount.compareTo(amount) >= 0 || refundAmount.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }
                amount = amount.subtract(refundAmount);
            }

            totalEarnings = totalEarnings.add(amount);
            paidOrdersList.add(orderService.convertToResponse(order));
        }

        return TodayEarningsResponse.builder()
                .date(today.toString())
                .todayEarnings(totalEarnings)
                .currency("INR")
                .currencySymbol("₹")
                .paidOrdersCount(paidOrdersList.size())
                .paidOrders(paidOrdersList)
                .build();
    }
}

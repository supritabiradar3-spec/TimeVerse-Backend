package com.timeverse.backend.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.timeverse.backend.dto.OrderResponse;
import com.timeverse.backend.dto.UpdateOrderStatusRequest;
import com.timeverse.backend.dto.CancelOrderRequest;
import com.timeverse.backend.service.OrderService;
import com.timeverse.backend.service.JwtService;

import jakarta.validation.Valid;


@RestController
@RequestMapping("/api/orders")
@CrossOrigin(origins = "*")
public class OrderController {


    private final OrderService orderService;
    private final JwtService jwtService;

    public OrderController(OrderService orderService, JwtService jwtService) {
        this.orderService = orderService;
        this.jwtService = jwtService;
    }

    private Long authenticatedUserId(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new RuntimeException("Authorization token is missing");
        }
        return jwtService.extractUserId(authHeader.substring(7));
    }

    private boolean isAdmin(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) return false;
        return "ADMIN".equalsIgnoreCase(jwtService.extractRole(authHeader.substring(7)));
    }



    // ================= PLACE ORDER =================

    @PostMapping("/place/{userId}")
    public ResponseEntity<OrderResponse> placeOrder(
            @PathVariable Long userId,
            @RequestParam Long addressId,
            @RequestParam(required = false) String couponCode,
            @RequestHeader("Authorization") String authHeader) {

        if (!isAdmin(authHeader) && !userId.equals(authenticatedUserId(authHeader))) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.FORBIDDEN, "You can only place orders for your own account");
        }

        OrderResponse response =
                orderService.placeOrder(userId, addressId, couponCode);

        return ResponseEntity.ok(response);
    }




    // ================= USER ORDERS =================

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<OrderResponse>> getUserOrders(
            @PathVariable Long userId,
            @RequestHeader("Authorization") String authHeader) {

        if (!isAdmin(authHeader) && !userId.equals(authenticatedUserId(authHeader))) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.FORBIDDEN, "You can only view your own orders");
        }

        List<OrderResponse> orders =
                orderService.getOrdersByUserId(userId);

        return ResponseEntity.ok(orders);
    }

    // ================= GET SINGLE ORDER =================

    @GetMapping("/{orderId}")
    public ResponseEntity<OrderResponse> getOrderById(
            @PathVariable Long orderId,
            @RequestHeader("Authorization") String authHeader) {

        OrderResponse order = orderService.getOrderById(orderId);
        if (!isAdmin(authHeader) && !order.getUserId().equals(authenticatedUserId(authHeader))) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.FORBIDDEN, "You can only view your own order details");
        }

        return ResponseEntity.ok(order);
    }




    // ================= ADMIN GET ALL ORDERS =================

    @GetMapping("/all")
    public ResponseEntity<List<OrderResponse>> getAllOrders(
            @RequestHeader("Authorization") String authHeader) {

        if (!isAdmin(authHeader)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.FORBIDDEN, "Admin access required");
        }

        List<OrderResponse> orders =
                orderService.getAllOrders();

        return ResponseEntity.ok(orders);
    }




    // ================= ADMIN UPDATE STATUS =================

    @PutMapping("/{orderId}/status")
    public ResponseEntity<OrderResponse> updateOrderStatus(
            @PathVariable Long orderId,
            @Valid @RequestBody UpdateOrderStatusRequest request) {


        OrderResponse response =
                orderService.updateOrderStatus(orderId, request);


        return ResponseEntity.ok(response);
    }

    // ================= CUSTOMER CANCEL ORDER =================

    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<OrderResponse> cancelOrder(
            @PathVariable Long orderId,
            @RequestBody CancelOrderRequest request,
            @RequestHeader("Authorization") String authHeader) {

        Long requesterId = isAdmin(authHeader) ? null : authenticatedUserId(authHeader);

        OrderResponse response =
                orderService.cancelOrder(orderId, request, requesterId);

        return ResponseEntity.ok(response);
    }
}
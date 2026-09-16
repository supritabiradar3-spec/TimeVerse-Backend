package com.timeverse.backend.controller;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.timeverse.backend.entity.Review;
import com.timeverse.backend.entity.Order;
import com.timeverse.backend.entity.OrderItem;
import com.timeverse.backend.entity.User;
import com.timeverse.backend.repository.ReviewRepository;
import com.timeverse.backend.repository.OrderRepository;
import com.timeverse.backend.repository.OrderItemRepository;
import com.timeverse.backend.repository.UserRepository;
import com.timeverse.backend.service.JwtService;

@RestController
@RequestMapping("/api/reviews")
@CrossOrigin(origins = "*")
public class ReviewController {

    private final ReviewRepository reviewRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final UserRepository userRepository;
    private final JwtService jwtService;

    public ReviewController(
            ReviewRepository reviewRepository,
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            UserRepository userRepository,
            JwtService jwtService) {
        this.reviewRepository = reviewRepository;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.userRepository = userRepository;
        this.jwtService = jwtService;
    }

    private Long getUserIdFromHeader(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new RuntimeException("Authorization token is missing");
        }
        String token = authHeader.substring(7);
        return jwtService.extractUserId(token);
    }

    @GetMapping("/product/{productId}")
    public ResponseEntity<List<Review>> getProductReviews(@PathVariable Long productId) {
        return ResponseEntity.ok(reviewRepository.findByProductId(productId));
    }

    @GetMapping("/user")
    public ResponseEntity<List<Review>> getUserReviews(
            @RequestHeader("Authorization") String authHeader) {
        Long userId = getUserIdFromHeader(authHeader);
        return ResponseEntity.ok(reviewRepository.findByUserId(userId));
    }

    @GetMapping("/user/eligible/{productId}")
    public ResponseEntity<Boolean> isEligibleToReview(
            @PathVariable Long productId,
            @RequestHeader("Authorization") String authHeader) {
        
        try {
            Long userId = getUserIdFromHeader(authHeader);
            boolean eligible = checkEligibility(userId, productId);
            return ResponseEntity.ok(eligible);
        } catch (Exception e) {
            return ResponseEntity.ok(false);
        }
    }

    @PostMapping("/product/{productId}")
    public ResponseEntity<Review> addReview(
            @PathVariable Long productId,
            @RequestBody Review reviewRequest,
            @RequestHeader("Authorization") String authHeader) {
        
        Long userId = getUserIdFromHeader(authHeader);

        if (!checkEligibility(userId, productId)) {
            throw new RuntimeException("You can only review products that have been delivered to you.");
        }

        if (reviewRepository.existsByProductIdAndUserId(productId, userId)) {
            throw new RuntimeException("You have already reviewed this product.");
        }

        if (reviewRequest.getRating() == null || reviewRequest.getRating() < 1 || reviewRequest.getRating() > 5) {
            throw new RuntimeException("Rating must be between 1 and 5.");
        }

        if (reviewRequest.getComment() == null || reviewRequest.getComment().trim().isEmpty()) {
            throw new RuntimeException("Review comment is required.");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Review review = Review.builder()
                .productId(productId)
                .userId(userId)
                .username(user.getUsername())
                .rating(reviewRequest.getRating())
                .comment(reviewRequest.getComment())
                .createdAt(LocalDateTime.now())
                .build();

        return ResponseEntity.ok(reviewRepository.save(review));
    }

    private boolean checkEligibility(Long userId, Long productId) {
        List<Order> orders = orderRepository.findByUserId(userId);
        for (Order order : orders) {
            if ("DELIVERED".equalsIgnoreCase(order.getStatus())) {
                List<OrderItem> items = orderItemRepository.findByOrderId(order.getOrderId());
                for (OrderItem item : items) {
                    if (item.getProductId().equals(productId)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}

package com.timeverse.backend.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.timeverse.backend.dto.OrderItemResponse;
import com.timeverse.backend.dto.OrderResponse;
import com.timeverse.backend.dto.UpdateOrderStatusRequest;
import com.timeverse.backend.entity.Cart;
import com.timeverse.backend.entity.Order;
import com.timeverse.backend.entity.OrderItem;
import com.timeverse.backend.entity.Product;
import com.timeverse.backend.repository.CartRepository;
import com.timeverse.backend.repository.OrderItemRepository;
import com.timeverse.backend.repository.OrderRepository;
import com.timeverse.backend.repository.ProductRepository;
import com.timeverse.backend.repository.UserRepository;
import com.timeverse.backend.repository.PaymentRepository;
import com.timeverse.backend.repository.CouponRepository;
import com.timeverse.backend.dto.CancelOrderRequest;
import com.timeverse.backend.entity.Payment;
import com.timeverse.backend.entity.Coupon;
import com.timeverse.backend.entity.Address;
import com.timeverse.backend.entity.User;
import com.timeverse.backend.repository.AddressRepository;
import java.util.Optional;

import lombok.RequiredArgsConstructor;

@Service
@Transactional
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final CartRepository cartRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final PaymentRepository paymentRepository;
    private final CouponRepository couponRepository;
    private final AddressRepository addressRepository;
    private final JwtService jwtService;



    // ================= PLACE ORDER =================

    public OrderResponse placeOrder(Long userId, Long addressId, String couponCode) {

        List<Cart> cartItems = cartRepository.findByUserId(userId);

        if (cartItems.isEmpty()) {
            throw new RuntimeException("Cart is empty");
        }


        BigDecimal subtotal = BigDecimal.ZERO;


        for (Cart cart : cartItems) {

            Product product = productRepository.findById(cart.getProductId())
                    .orElseThrow(() ->
                            new RuntimeException("Product not found"));


            if (product.getStock() < cart.getQuantity()) {
                throw new RuntimeException(
                        "Insufficient stock for product: "
                                + product.getName());
            }


            BigDecimal itemTotal =
                    product.getPrice()
                    .multiply(BigDecimal.valueOf(cart.getQuantity()));


            subtotal = subtotal.add(itemTotal);
        }

        BigDecimal taxAmount = BigDecimal.ZERO;
        BigDecimal shippingCharge = BigDecimal.ZERO;
        BigDecimal discountAmount = BigDecimal.ZERO;

        if (couponCode != null && !couponCode.trim().isEmpty()) {
            String cleanCode = couponCode.trim();
            if ("TIMEVERSE10".equalsIgnoreCase(cleanCode)) {
                discountAmount = subtotal.multiply(BigDecimal.valueOf(10)).divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
            } else {
                Optional<Coupon> couponOpt = couponRepository.findByCodeIgnoreCase(cleanCode);
                if (couponOpt.isPresent() && couponOpt.get().isActive()) {
                    Coupon coupon = couponOpt.get();
                    discountAmount = subtotal.multiply(coupon.getDiscountPercentage()).divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
                    if (coupon.getMaxDiscountAmount() != null && discountAmount.compareTo(coupon.getMaxDiscountAmount()) > 0) {
                        discountAmount = coupon.getMaxDiscountAmount();
                    }
                }
            }
        }

        BigDecimal totalAmount = subtotal.subtract(discountAmount).max(BigDecimal.ZERO);

        Address address = addressRepository.findById(addressId)
                .orElseThrow(() -> new RuntimeException("Address not found with id: " + addressId));

        Order order = Order.builder()
                .userId(userId)
                .totalAmount(totalAmount)
                .status("PLACED")
                .createdAt(LocalDateTime.now())
                .discountAmount(discountAmount)
                .couponCode(couponCode)
                .shippingCharge(shippingCharge)
                .taxAmount(taxAmount)
                .shippingAddress(address)
                .build();


        order = orderRepository.save(order);



        List<OrderItemResponse> itemResponses = new ArrayList<>();


        for (Cart cart : cartItems) {

            Product product = productRepository.findById(cart.getProductId())
                    .orElseThrow(() ->
                            new RuntimeException("Product not found"));



            OrderItem orderItem = OrderItem.builder()
                    .orderId(order.getOrderId())
                    .productId(product.getProductId())
                    .quantity(cart.getQuantity())
                    .price(product.getPrice())
                    .build();


            orderItemRepository.save(orderItem);



            String primaryImage = product.getImages() != null && !product.getImages().isEmpty()
                    ? product.getImages().get(0).getImageUrl() : "";
            itemResponses.add(
                    OrderItemResponse.builder()
                    .productId(product.getProductId())
                    .quantity(cart.getQuantity())
                    .price(product.getPrice())
                    .productName(product.getName())
                    .imageUrl(primaryImage)
                    .build()
            );



            product.setStock(
                    product.getStock() - cart.getQuantity()
            );

            productRepository.save(product);
        }



        cartRepository.deleteAll(cartItems);



        Optional<User> userOpt = userRepository.findById(order.getUserId());
        String customerName = userOpt.map(u -> (u.getFullName() != null && !u.getFullName().isBlank()) ? u.getFullName() : u.getUsername()).orElse("Customer #" + order.getUserId());
        String customerEmail = userOpt.map(User::getEmail).orElse(null);

        return OrderResponse.builder()
                .orderId(order.getOrderId())
                .userId(order.getUserId())
                .customerName(customerName)
                .customerEmail(customerEmail)
                .totalAmount(order.getTotalAmount())
                .status(order.getStatus())
                .createdAt(order.getCreatedAt())
                .items(itemResponses)
                .shippingAddress(order.getShippingAddress())
                .paymentStatus("PENDING")
                .paymentMethod("RAZORPAY")
                .build();
    }




    // ================= USER GET ORDERS =================

    public List<OrderResponse> getOrdersByUserId(Long userId) {

        List<Order> orders = orderRepository.findByUserId(userId);


        return orders.stream()
                .map(order -> convertToResponse(order))
                .collect(Collectors.toList());
    }

    // ================= GET ORDER BY ID =================

    public OrderResponse getOrderById(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found with ID: " + orderId));
        return convertToResponse(order);
    }




    // ================= ADMIN GET ALL ORDERS =================

    public List<OrderResponse> getAllOrders() {

        List<Order> orders = orderRepository.findAll();


        return orders.stream()
                .map(order -> convertToResponse(order))
                .collect(Collectors.toList());
    }




    // ================= ADMIN UPDATE ORDER STATUS =================

    public OrderResponse updateOrderStatus(
            Long orderId,
            UpdateOrderStatusRequest request) {


        Order order = orderRepository.findById(orderId)
                .orElseThrow(() ->
                        new RuntimeException("Order not found"));


        order.setStatus(request.getStatus());

        orderRepository.save(order);


        return convertToResponse(order);
    }




    // ================= CUSTOMER CANCEL ORDER =================

    public OrderResponse cancelOrder(Long orderId, CancelOrderRequest request, Long requesterId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));

        if (requesterId != null && !requesterId.equals(order.getUserId())) {
            throw new RuntimeException("You can only cancel your own orders");
        }

        String status = order.getStatus() != null ? order.getStatus().toUpperCase().replace(" ", "_") : "";
        if (status.equals("DELIVERED") || status.equals("CANCELLED") || status.equals("SHIPPED") || status.equals("OUT_FOR_DELIVERY") || status.equals("REFUNDED")) {
            throw new RuntimeException("Order cannot be cancelled at this stage: " + order.getStatus());
        }

        if (request.getReason() == null || request.getReason().trim().isEmpty()) {
            throw new RuntimeException("Cancellation reason is required");
        }

        order.setStatus("CANCELLED");
        order.setCancellationReason(request.getReason());

        Optional<Payment> paymentOpt = paymentRepository.findByOrderId(orderId);
        boolean isPaid = paymentOpt.isPresent() && "SUCCESS".equalsIgnoreCase(paymentOpt.get().getPaymentStatus());

        if (isPaid) {
            if ("REFUNDED".equalsIgnoreCase(order.getRefundStatus())) {
                throw new RuntimeException("Refund has already been processed");
            }
            order.setRefundStatus("REFUNDED");
            order.setRefundAmount(order.getTotalAmount());
        }

        orderRepository.save(order);

        List<OrderItem> items = orderItemRepository.findByOrderId(orderId);
        for (OrderItem item : items) {
            Product product = productRepository.findById(item.getProductId()).orElse(null);
            if (product != null) {
                product.setStock(product.getStock() + item.getQuantity());
                productRepository.save(product);
            }
        }

        return convertToResponse(order);
    }




    // ================= COMMON CONVERTER =================

    public OrderResponse convertToResponse(Order order) {


        List<OrderItemResponse> items =
                orderItemRepository.findByOrderId(order.getOrderId())
                .stream()
                .map(item -> {
                    Product product = productRepository.findById(item.getProductId()).orElse(null);
                    String productName = product != null ? product.getName() : "Curated Timepiece";
                    String primaryImage = product != null && product.getImages() != null && !product.getImages().isEmpty()
                            ? product.getImages().get(0).getImageUrl() : "";
                    return OrderItemResponse.builder()
                            .productId(item.getProductId())
                            .quantity(item.getQuantity())
                            .price(item.getPrice())
                            .productName(productName)
                            .imageUrl(primaryImage)
                            .build();
                })
                .collect(Collectors.toList());

        List<Payment> payments = paymentRepository.findByOrderIdOrderByPaymentIdDesc(order.getOrderId());
        Payment authPayment = payments.stream()
                .filter(p -> p.getPaymentStatus() != null &&
                        ("SUCCESS".equalsIgnoreCase(p.getPaymentStatus()) ||
                         "PAID".equalsIgnoreCase(p.getPaymentStatus()) ||
                         "COMPLETED".equalsIgnoreCase(p.getPaymentStatus())))
                .findFirst()
                .orElse(payments.isEmpty() ? null : payments.get(0));

        String paymentStatus = authPayment != null && authPayment.getPaymentStatus() != null ? authPayment.getPaymentStatus() : "PENDING";
        String paymentMethod = authPayment != null && authPayment.getPaymentMethod() != null ? authPayment.getPaymentMethod() : "RAZORPAY";

        Optional<User> userOpt = userRepository.findById(order.getUserId());
        String customerName = userOpt.map(u -> {
            if (u.getFullName() != null && !u.getFullName().isBlank()) return u.getFullName();
            return u.getUsername();
        }).orElse(null);
        if (customerName == null && order.getShippingAddress() != null && order.getShippingAddress().getFullName() != null) {
            customerName = order.getShippingAddress().getFullName();
        }
        if (customerName == null) {
            customerName = "Customer #" + order.getUserId();
        }
        String customerEmail = userOpt.map(User::getEmail).orElse(null);

        return OrderResponse.builder()
                .orderId(order.getOrderId())
                .userId(order.getUserId())
                .customerName(customerName)
                .customerEmail(customerEmail)
                .totalAmount(order.getTotalAmount())
                .status(order.getStatus())
                .createdAt(order.getCreatedAt())
                .cancellationReason(order.getCancellationReason())
                .refundStatus(order.getRefundStatus())
                .refundAmount(order.getRefundAmount())
                .discountAmount(order.getDiscountAmount())
                .couponCode(order.getCouponCode())
                .shippingCharge(order.getShippingCharge())
                .taxAmount(order.getTaxAmount())
                .items(items)
                .shippingAddress(order.getShippingAddress())
                .paymentStatus(paymentStatus)
                .paymentMethod(paymentMethod)
                .build();
    }
}
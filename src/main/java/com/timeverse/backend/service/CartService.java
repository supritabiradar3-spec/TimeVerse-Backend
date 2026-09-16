package com.timeverse.backend.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.timeverse.backend.dto.AddToCartRequest;
import com.timeverse.backend.dto.CartResponse;
import com.timeverse.backend.dto.UpdateCartRequest;
import com.timeverse.backend.entity.Cart;
import com.timeverse.backend.entity.Product;
import com.timeverse.backend.exception.ResourceNotFoundException;
import com.timeverse.backend.repository.CartRepository;
import com.timeverse.backend.repository.ProductRepository;

@Service
public class CartService {

    private final CartRepository cartRepository;
    private final ProductRepository productRepository;
    private final JwtService jwtService;

    public CartService(
            CartRepository cartRepository,
            ProductRepository productRepository,
            JwtService jwtService) {

        this.cartRepository = cartRepository;
        this.productRepository = productRepository;
        this.jwtService = jwtService;
    }

    public String test() {
        return "Cart Service Working";
    }

    // Add Item to Cart
    public Cart addToCart(AddToCartRequest request, String authHeader) {

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new RuntimeException("Authorization token is missing");
        }

        String token = authHeader.substring(7);
        Long userId = jwtService.extractUserId(token);

        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() ->
                        new ResourceNotFoundException("Product not found"));

        Cart cart = cartRepository
                .findByUserIdAndProductId(userId, request.getProductId())
                .orElse(null);

        if (cart != null) {
            cart.setQuantity(cart.getQuantity() + request.getQuantity());
        } else {
            cart = Cart.builder()
                    .userId(userId)
                    .productId(product.getProductId())
                    .quantity(request.getQuantity())
                    .createdAt(LocalDateTime.now())
                    .build();
        }

        return cartRepository.save(cart);
    }

    // Get Cart Items
    public List<CartResponse> getCart(String authHeader) {

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new RuntimeException("Authorization token is missing");
        }

        String token = authHeader.substring(7);
        Long userId = jwtService.extractUserId(token);

        List<Cart> cartItems = cartRepository.findByUserId(userId);

        return cartItems.stream().map(cart -> {

            Product product = productRepository.findById(cart.getProductId())
                    .orElseThrow(() ->
                            new ResourceNotFoundException("Product not found"));

            String imageUrl = null;

            if (product.getImages() != null && !product.getImages().isEmpty()) {
                imageUrl = product.getImages().get(0).getImageUrl();
            }

            return CartResponse.builder()
                    .productId(product.getProductId())
                    .productName(product.getName())
                    .price(product.getPrice())
                    .quantity(cart.getQuantity())
                    .imageUrl(imageUrl)
                    .build();

        }).collect(Collectors.toList());
    }

    // Get Cart Count
    public Long getCartCount(String authHeader) {

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new RuntimeException("Authorization token is missing");
        }

        String token = authHeader.substring(7);
        Long userId = jwtService.extractUserId(token);

        return cartRepository.countByUserId(userId);
    }

    // Update Cart Quantity
    public Cart updateCart(Long productId,
                           UpdateCartRequest request,
                           String authHeader) {

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new RuntimeException("Authorization token is missing");
        }

        String token = authHeader.substring(7);
        Long userId = jwtService.extractUserId(token);

        Cart cart = cartRepository
                .findByUserIdAndProductId(userId, productId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Cart item not found"));

        cart.setQuantity(request.getQuantity());

        return cartRepository.save(cart);
    }

    // Remove Item from Cart
    public void removeFromCart(Long productId, String authHeader) {

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new RuntimeException("Authorization token is missing");
        }

        String token = authHeader.substring(7);
        Long userId = jwtService.extractUserId(token);

        Cart cart = cartRepository
                .findByUserIdAndProductId(userId, productId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Cart item not found"));

        cartRepository.delete(cart);
    }
}
package com.timeverse.backend.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.timeverse.backend.dto.AddToCartRequest;
import com.timeverse.backend.dto.CartResponse;
import com.timeverse.backend.dto.UpdateCartRequest;
import com.timeverse.backend.entity.Cart;
import com.timeverse.backend.service.CartService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/cart")
@CrossOrigin(origins = "*")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    // Test API
    @GetMapping("/test")
    public ResponseEntity<String> test() {
        return ResponseEntity.ok(cartService.test());
    }

    // Add Item to Cart
    @PostMapping
    public ResponseEntity<Cart> addToCart(
            @Valid @RequestBody AddToCartRequest request,
            @RequestHeader("Authorization") String authHeader) {

        return ResponseEntity.ok(cartService.addToCart(request, authHeader));
    }

    // Get Cart Items
    @GetMapping
    public ResponseEntity<List<CartResponse>> getCart(
            @RequestHeader("Authorization") String authHeader) {

        return ResponseEntity.ok(cartService.getCart(authHeader));
    }

    // Get Cart Count
    @GetMapping("/count")
    public ResponseEntity<Long> getCartCount(
            @RequestHeader("Authorization") String authHeader) {

        return ResponseEntity.ok(cartService.getCartCount(authHeader));
    }

    // Update Cart Quantity
    @PutMapping("/{productId}")
    public ResponseEntity<Cart> updateCart(
            @PathVariable Long productId,
            @Valid @RequestBody UpdateCartRequest request,
            @RequestHeader("Authorization") String authHeader) {

        Cart updatedCart = cartService.updateCart(
                productId,
                request,
                authHeader);

        return ResponseEntity.ok(updatedCart);
    }

    // Remove Item from Cart
    @DeleteMapping("/{productId}")
    public ResponseEntity<Void> removeFromCart(
            @PathVariable Long productId,
            @RequestHeader("Authorization") String authHeader) {

        cartService.removeFromCart(productId, authHeader);

        return ResponseEntity.noContent().build();
    }
}
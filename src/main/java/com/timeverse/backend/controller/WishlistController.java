package com.timeverse.backend.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import com.timeverse.backend.dto.WishlistResponse;
import com.timeverse.backend.entity.Wishlist;
import com.timeverse.backend.service.WishlistService;

@RestController
@RequestMapping("/api/wishlist")
@CrossOrigin(origins = "*")
public class WishlistController {

    private final WishlistService wishlistService;

    public WishlistController(WishlistService wishlistService) {
        this.wishlistService = wishlistService;
    }

    // Test API
    @GetMapping("/test")
    public ResponseEntity<String> test() {
        return ResponseEntity.ok(wishlistService.test());
    }

    // Add Product to Wishlist
    @PostMapping("/{productId}")
    public ResponseEntity<Wishlist> addToWishlist(@PathVariable Long productId) {

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        String email = authentication.getName();

        Wishlist wishlist =
                wishlistService.addToWishlist(productId, email);

        return ResponseEntity.ok(wishlist);
    }

    // Get Wishlist
    @GetMapping
    public ResponseEntity<List<WishlistResponse>> getWishlist() {

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        String email = authentication.getName();

        return ResponseEntity.ok(
                wishlistService.getWishlist(email)
        );
    }

    // Remove Product from Wishlist
    @DeleteMapping("/{productId}")
    public ResponseEntity<Void> removeFromWishlist(
            @PathVariable Long productId) {

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        String email = authentication.getName();

        wishlistService.removeFromWishlist(productId, email);

        return ResponseEntity.noContent().build();
    }

    // Wishlist Count
    @GetMapping("/count")
    public ResponseEntity<Long> getWishlistCount() {

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        String email = authentication.getName();

        return ResponseEntity.ok(
                wishlistService.getWishlistCount(email)
        );
    }
}
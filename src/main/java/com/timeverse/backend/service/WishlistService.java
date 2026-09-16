package com.timeverse.backend.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.timeverse.backend.dto.WishlistResponse;
import com.timeverse.backend.entity.Product;
import com.timeverse.backend.entity.User;
import com.timeverse.backend.entity.Wishlist;
import com.timeverse.backend.exception.BadRequestException;
import com.timeverse.backend.exception.ResourceNotFoundException;
import com.timeverse.backend.repository.ProductRepository;
import com.timeverse.backend.repository.UserRepository;
import com.timeverse.backend.repository.WishlistRepository;

@Service
public class WishlistService {

    private final WishlistRepository wishlistRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    public WishlistService(
            WishlistRepository wishlistRepository,
            ProductRepository productRepository,
            UserRepository userRepository) {

        this.wishlistRepository = wishlistRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
    }

    public String test() {
        return "Wishlist Service Working";
    }

    private Long getUserId(String email) {

        User user = userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new ResourceNotFoundException("User not found"));

        return user.getUserId();
    }

    // Add to Wishlist
    public Wishlist addToWishlist(Long productId, String email) {

        Long userId = getUserId(email);

        Product product = productRepository.findById(productId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Product not found"));

        if (wishlistRepository.findByUserIdAndProductId(userId, productId).isPresent()) {
            throw new BadRequestException("Product already exists in wishlist");
        }

        Wishlist wishlist = Wishlist.builder()
                .userId(userId)
                .productId(product.getProductId())
                .createdAt(LocalDateTime.now())
                .build();

        return wishlistRepository.save(wishlist);
    }

    // Get Wishlist
    public List<WishlistResponse> getWishlist(String email) {

        Long userId = getUserId(email);

        List<Wishlist> wishlistItems = wishlistRepository.findByUserId(userId);

        return wishlistItems.stream().map(item -> {

            Product product = productRepository.findById(item.getProductId())
                    .orElseThrow(() ->
                            new ResourceNotFoundException("Product not found"));

            String imageUrl = null;

            if (product.getImages() != null && !product.getImages().isEmpty()) {
                imageUrl = product.getImages().get(0).getImageUrl();
            }

            return WishlistResponse.builder()
                    .productId(product.getProductId())
                    .productName(product.getName())
                    .price(product.getPrice())
                    .imageUrl(imageUrl)
                    .build();

        }).collect(Collectors.toList());
    }

    // Remove from Wishlist
    public void removeFromWishlist(Long productId, String email) {

        Long userId = getUserId(email);

        Wishlist wishlist = wishlistRepository
                .findByUserIdAndProductId(userId, productId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Wishlist item not found"));

        wishlistRepository.delete(wishlist);
    }

    // Wishlist Count
    public Long getWishlistCount(String email) {

        Long userId = getUserId(email);

        return wishlistRepository.countByUserId(userId);
    }
}
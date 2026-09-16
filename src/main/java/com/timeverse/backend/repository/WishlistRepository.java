package com.timeverse.backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.timeverse.backend.entity.Wishlist;

@Repository
public interface WishlistRepository extends JpaRepository<Wishlist, Long> {

    // Get all wishlist items of a user
    List<Wishlist> findByUserId(Long userId);

    // Check if product already exists in wishlist
    Optional<Wishlist> findByUserIdAndProductId(Long userId, Long productId);

    // Wishlist count
    Long countByUserId(Long userId);

    // Remove product from wishlist
    void deleteByUserIdAndProductId(Long userId, Long productId);
}
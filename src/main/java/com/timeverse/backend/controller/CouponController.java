package com.timeverse.backend.controller;

import java.math.BigDecimal;
import java.util.Optional;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.timeverse.backend.entity.Coupon;
import com.timeverse.backend.repository.CouponRepository;

@RestController
@RequestMapping("/api/coupons")
@CrossOrigin(origins = "*")
public class CouponController {

    private final CouponRepository couponRepository;

    public CouponController(CouponRepository couponRepository) {
        this.couponRepository = couponRepository;
    }

    private void initCouponsIfEmpty() {
        if (couponRepository.count() == 0) {
            couponRepository.save(Coupon.builder()
                    .code("WELCOME10")
                    .discountPercentage(BigDecimal.valueOf(10))
                    .maxDiscountAmount(BigDecimal.valueOf(5000))
                    .active(true)
                    .build());
            couponRepository.save(Coupon.builder()
                    .code("TIMEVERSE20")
                    .discountPercentage(BigDecimal.valueOf(20))
                    .maxDiscountAmount(BigDecimal.valueOf(10000))
                    .active(true)
                    .build());
        }
    }

    @GetMapping("/validate/{code}")
    public ResponseEntity<Coupon> validateCoupon(@PathVariable String code) {
        initCouponsIfEmpty();
        Optional<Coupon> couponOpt = couponRepository.findByCodeIgnoreCase(code);
        if (couponOpt.isPresent() && couponOpt.get().isActive()) {
            return ResponseEntity.ok(couponOpt.get());
        }
        throw new RuntimeException("Invalid or expired coupon code");
    }
}

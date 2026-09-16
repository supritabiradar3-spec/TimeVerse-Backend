package com.timeverse.backend.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.timeverse.backend.entity.Otp;

@Repository
public interface OtpRepository extends JpaRepository<Otp, Long> {
    Optional<Otp> findByEmailAndOtp(String email, String otp);
    Optional<Otp> findByEmailAndOtpAndPurpose(String email, String otp, String purpose);
    Optional<Otp> findFirstByEmailOrderByCreatedAtDesc(String email);
    boolean existsByRegistrationUsername(String registrationUsername);
    void deleteByEmail(String email);
}

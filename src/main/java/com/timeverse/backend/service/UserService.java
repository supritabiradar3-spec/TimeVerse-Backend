package com.timeverse.backend.service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.timeverse.backend.dto.ChangePasswordRequest;
import com.timeverse.backend.dto.ForgotPasswordRequest;
import com.timeverse.backend.dto.LoginRequest;
import com.timeverse.backend.dto.LoginResponse;
import com.timeverse.backend.dto.RegisterRequest;
import com.timeverse.backend.dto.ResetPasswordRequest;
import com.timeverse.backend.dto.VerifyOtpRequest;
import com.timeverse.backend.entity.JwtToken;
import com.timeverse.backend.entity.Otp;
import com.timeverse.backend.entity.User;
import com.timeverse.backend.exception.BadRequestException;
import com.timeverse.backend.exception.ResourceNotFoundException;
import com.timeverse.backend.exception.UnauthorizedException;
import com.timeverse.backend.repository.JwtTokenRepository;
import com.timeverse.backend.repository.OtpRepository;
import com.timeverse.backend.repository.UserRepository;

@Service
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final JwtTokenRepository jwtTokenRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final OtpRepository otpRepository;
    private final EmailService emailService;

    public UserService(
            UserRepository userRepository,
            JwtTokenRepository jwtTokenRepository,
            JwtService jwtService,
            PasswordEncoder passwordEncoder,
            OtpRepository otpRepository,
            EmailService emailService) {

        this.userRepository = userRepository;
        this.jwtTokenRepository = jwtTokenRepository;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
        this.otpRepository = otpRepository;
        this.emailService = emailService;
    }

    // ================= REGISTER =================

    public User registerUser(RegisterRequest request) {
        String cleanedEmail = request.getEmail().toLowerCase().trim();

        if (userRepository.existsByEmail(cleanedEmail)) {
            throw new BadRequestException("Email already registered");
        }

        if (userRepository.existsByUsername(request.getUsername())) {
            throw new BadRequestException("Username already taken");
        }

        String role = userRepository.count() == 0 ? "ADMIN" : "CUSTOMER";
        String encodedPassword = passwordEncoder.encode(request.getPassword());

        // Delete any existing OTP for the email
        otpRepository.deleteByEmail(cleanedEmail);

        // Generate 6-digit OTP
        SecureRandom random = new SecureRandom();
        String otp = String.format("%06d", random.nextInt(1000000));

        // Send OTP using EmailService through SMTP first before persisting
        boolean mailSent = emailService.sendRegistrationOtp(cleanedEmail, otp);
        if (!mailSent) {
            throw new BadRequestException("Unable to send verification OTP");
        }

        // Save pending registration details inside the Otp entity only after successful email dispatch
        Otp otpEntity = Otp.builder()
                .email(cleanedEmail)
                .registrationUsername(request.getUsername())
                .registrationPasswordHash(encodedPassword)
                .otp(otp)
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusMinutes(5))
                .verified(false)
                .purpose("REGISTRATION")
                .build();

        otpRepository.save(otpEntity);

        return User.builder()
                .username(request.getUsername())
                .email(cleanedEmail)
                .password(encodedPassword)
                .role(role)
                .emailVerified(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    // ================= VERIFY REGISTRATION OTP =================

    public void verifyRegistrationOtp(String email, String otp) {
        String cleanedEmail = email.toLowerCase().trim();
        Otp otpEntity = otpRepository.findByEmailAndOtpAndPurpose(cleanedEmail, otp, "REGISTRATION")
                .orElseThrow(() -> new BadRequestException("Invalid registration OTP"));

        if (otpEntity.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BadRequestException("OTP expired");
        }

        if (userRepository.existsByEmail(cleanedEmail)) {
            throw new BadRequestException("Email already registered");
        }

        if (userRepository.existsByUsername(otpEntity.getRegistrationUsername())) {
            throw new BadRequestException("Username already taken");
        }

        User user = User.builder()
                .username(otpEntity.getRegistrationUsername())
                .email(otpEntity.getEmail())
                .password(otpEntity.getRegistrationPasswordHash())
                .role("CUSTOMER")
                .emailVerified(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        userRepository.save(user);

        // Delete the OTP only after successful verification
        otpRepository.deleteByEmail(cleanedEmail);
    }

    // ================= RESEND REGISTRATION OTP =================

    public void resendRegistrationOtp(String email) {
        String cleanedEmail = email.toLowerCase().trim();
        if (userRepository.existsByEmail(cleanedEmail)) {
            throw new BadRequestException("Email already registered");
        }

        Otp pending = otpRepository.findFirstByEmailOrderByCreatedAtDesc(cleanedEmail)
                .orElseThrow(() -> new BadRequestException("No pending registration found"));

        otpRepository.deleteByEmail(cleanedEmail);

        SecureRandom random = new SecureRandom();
        String newOtp = String.format("%06d", random.nextInt(1000000));

        Otp otpEntity = Otp.builder()
                .email(cleanedEmail)
                .registrationUsername(pending.getRegistrationUsername())
                .registrationPasswordHash(pending.getRegistrationPasswordHash())
                .otp(newOtp)
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusMinutes(5))
                .verified(false)
                .purpose("REGISTRATION")
                .build();

        otpRepository.save(otpEntity);

        try {
            boolean mailSent = emailService.sendRegistrationOtp(cleanedEmail, newOtp);
            if (!mailSent) {
                otpRepository.deleteByEmail(cleanedEmail);
                throw new BadRequestException("Unable to send verification OTP");
            }
        } catch (RuntimeException ex) {
            otpRepository.deleteByEmail(cleanedEmail);
            throw ex;
        }
    }

    // ================= LOGIN =================
    public LoginResponse loginUser(LoginRequest request) {
        String cleanedEmail = request.getEmail().toLowerCase().trim();

        User user = userRepository.findByEmail(cleanedEmail)
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password"));

        boolean roleMatches = user.getRole().equalsIgnoreCase(request.getRole());

        if (!roleMatches) {
            throw new UnauthorizedException("Invalid role for this account");
        }

        boolean passwordMatches =
                passwordEncoder.matches(request.getPassword(), user.getPassword());

        if (!passwordMatches) {
            throw new UnauthorizedException("Invalid email or password");
        }

        // CUSTOMER and ADMIN authenticate with email + password only without login OTP.
        return createLoginResponseAndToken(user);
    }

private LoginResponse createLoginResponseAndToken(User user) {
    jwtTokenRepository.deleteByUserId(user.getUserId());

    String token = jwtService.generateToken(
            user.getEmail(),
            user.getUserId(),
            user.getRole());

    JwtToken jwtToken = JwtToken.builder()
            .userId(user.getUserId())
            .token(jwtService.extractSignature(token))
            .createdAt(LocalDateTime.now())
            .expiresAt(LocalDateTime.now()
                    .plusSeconds(jwtService.getExpirationTime() / 1000))
            .build();

    jwtTokenRepository.save(jwtToken);

    return LoginResponse.builder()
            .username(user.getUsername())
            .email(user.getEmail())
            .role(user.getRole())
            .token(token)
            .build();
}


    // ================= VERIFY LOGIN OTP =================

    public LoginResponse verifyLoginOtp(String email, String otp) {
        String cleanedEmail = email.toLowerCase().trim();

        Otp otpEntity = otpRepository.findByEmailAndOtpAndPurpose(cleanedEmail, otp, "LOGIN")
                .orElseThrow(() -> new BadRequestException("Invalid login OTP"));

        if (otpEntity.isVerified()) {
            throw new BadRequestException("OTP already used");
        }

        if (otpEntity.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BadRequestException("OTP expired");
        }

        User user = userRepository.findByEmail(cleanedEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (!"ADMIN".equalsIgnoreCase(user.getRole())) {
            throw new UnauthorizedException("Login OTP is only required for admin accounts");
        }

        otpEntity.setVerified(true);
        otpRepository.save(otpEntity);

        LoginResponse response = createLoginResponseAndToken(user);
        otpRepository.delete(otpEntity);
        return response;
    }

    // ================= LOGOUT =================

    public void logoutUser(String authorizationHeader) {
        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            String token = authorizationHeader.substring(7);
            jwtTokenRepository.deleteByToken(jwtService.extractSignature(token));
        }
    }

    // ================= FORGOT PASSWORD =================

    public void forgotPassword(ForgotPasswordRequest request) {
        String cleanedEmail = request.getEmail().toLowerCase().trim();
        if (!userRepository.existsByEmail(cleanedEmail)) {
            throw new ResourceNotFoundException("Email not found");
        }

        otpRepository.deleteByEmail(cleanedEmail);

        SecureRandom random = new SecureRandom();
        String otp = String.format("%06d", random.nextInt(1000000));

        Otp otpEntity = Otp.builder()
                .email(cleanedEmail)
                .otp(otp)
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusMinutes(5))
                .verified(false)
                .purpose("PASSWORD_RESET")
                .build();

        otpRepository.save(otpEntity);

        String body = "<h1>Reset Your Password</h1>\n" +
                "<p>Use the verification code below to reset your TimeVerse password:</p>\n" +
                "<div class=\"otp-code\">" + otp + "</div>\n" +
                "<p>This code will expire in 5 minutes.</p>";

        try {
            boolean mailSent = emailService.sendHtmlEmail(
                    cleanedEmail,
                    "TimeVerse Password Reset OTP",
                    emailService.buildTemplate(body));
            if (!mailSent) {
                otpRepository.deleteByEmail(cleanedEmail);
                throw new BadRequestException("Unable to send verification OTP");
            }
        } catch (RuntimeException ex) {
            otpRepository.deleteByEmail(cleanedEmail);
            throw ex;
        }
    }

    // ================= VERIFY PASSWORD OTP =================

    public void verifyOtp(VerifyOtpRequest request) {
        String cleanedEmail = request.getEmail().toLowerCase().trim();

        Otp otpEntity = otpRepository.findByEmailAndOtpAndPurpose(
                        cleanedEmail, request.getOtp(), "PASSWORD_RESET")
                .orElseThrow(() -> new BadRequestException("Invalid password reset OTP"));

        if (otpEntity.isVerified()) {
            throw new BadRequestException("OTP already used");
        }

        if (otpEntity.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BadRequestException("OTP expired");
        }

        otpEntity.setVerified(true);
        otpRepository.save(otpEntity);
    }

    // ================= RESET PASSWORD =================

    public void resetPassword(ResetPasswordRequest request) {
        String cleanedEmail = request.getEmail().toLowerCase().trim();

        Otp otpEntity = otpRepository.findFirstByEmailOrderByCreatedAtDesc(cleanedEmail)
                .orElseThrow(() -> new BadRequestException("Invalid password reset OTP"));

        if (!"PASSWORD_RESET".equalsIgnoreCase(otpEntity.getPurpose()) || !otpEntity.isVerified()) {
            throw new BadRequestException("Password reset OTP not verified");
        }

        if (otpEntity.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BadRequestException("OTP expired");
        }

        User user = userRepository.findByEmail(cleanedEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Email not found"));

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);

        otpRepository.delete(otpEntity);
    }

    // ================= CHANGE PASSWORD =================

    public void changePassword(ChangePasswordRequest request) {
        org.springframework.security.core.Authentication auth = 
                SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || "anonymousUser".equalsIgnoreCase(auth.getName())) {
            throw new UnauthorizedException("User not logged in");
        }

        String email = auth.getName();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Email not found"));

        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new BadRequestException("Incorrect old password");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
    }

    // ================= GET USER PROFILE =================
    public User getUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
    }

    // ================= UPDATE USER PROFILE =================
    public User updateUserProfile(Long userId, User profileDetails) {
        User user = getUserById(userId);

        if (profileDetails.getFullName() != null) {
            user.setFullName(profileDetails.getFullName());
        }

        if (profileDetails.getMobile() != null) {
            user.setMobile(profileDetails.getMobile());
        }

        if (profileDetails.getUsername() != null && !profileDetails.getUsername().isBlank()) {
            user.setUsername(profileDetails.getUsername());
        }

        if (profileDetails.getEmail() != null && !profileDetails.getEmail().isBlank()) {
            user.setEmail(profileDetails.getEmail());
        }

        user.setUpdatedAt(LocalDateTime.now());

        return userRepository.save(user);
    }

    // ================= GET ALL USERS =================
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    // ================= DELETE USER =================
    public void deleteUser(Long userId) {
        User user = getUserById(userId);
        userRepository.delete(user);
    }
}
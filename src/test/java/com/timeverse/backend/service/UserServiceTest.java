package com.timeverse.backend.service;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.matches;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

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

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private JwtTokenRepository jwtTokenRepository;

    @Mock
    private JwtService jwtService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private OtpRepository otpRepository;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private UserService userService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .userId(1L)
                .username("testuser")
                .email("test@timeverse.com")
                .password("encoded_old_password")
                .role("CUSTOMER")
                .build();
    }

    // ================= forgotPassword() Tests =================

        @Test
        void registerUser_GeneratesAndSavesOtpAndSendsItToRegisteredEmail() {
        RegisterRequest request = new RegisterRequest(
            "newuser", "newuser@timeverse.com", "password123");

        when(userRepository.existsByEmail(request.getEmail())).thenReturn(false);
        when(userRepository.existsByUsername(request.getUsername())).thenReturn(false);
        when(passwordEncoder.encode(request.getPassword())).thenReturn("encoded_password");
        when(userRepository.count()).thenReturn(1L);
        when(emailService.sendRegistrationOtp(eq(request.getEmail()), matches("\\d{6}")))
            .thenReturn(true);

        userService.registerUser(request);

        verify(otpRepository).deleteByEmail(request.getEmail());
        verify(otpRepository).save(any(Otp.class));
        verify(emailService).sendRegistrationOtp(eq(request.getEmail()), matches("\\d{6}"));
        }

        @Test
        void registerUser_SmtpFailureDoesNotCreateUser() {
        RegisterRequest request = new RegisterRequest(
            "newuser", "newuser@timeverse.com", "password123");

        when(userRepository.existsByEmail(request.getEmail())).thenReturn(false);
        when(userRepository.existsByUsername(request.getUsername())).thenReturn(false);
        when(passwordEncoder.encode(request.getPassword())).thenReturn("encoded_password");
        when(userRepository.count()).thenReturn(1L);
        when(emailService.sendRegistrationOtp(eq(request.getEmail()), matches("\\d{6}")))
            .thenReturn(false);

        assertThrows(BadRequestException.class, () -> userService.registerUser(request));

        verify(userRepository, never()).save(any(User.class));
        verify(otpRepository, never()).save(any(Otp.class));
        }

        @Test
        void verifyRegistrationOtp_CreatesCustomerAndDeletesPendingRegistration() {
        Otp pending = pendingRegistration("newuser@timeverse.com", "newuser", "encoded_password");
        when(otpRepository.findByEmailAndOtpAndPurpose(pending.getEmail(), pending.getOtp(), "REGISTRATION"))
            .thenReturn(Optional.of(pending));
        when(userRepository.existsByEmail(pending.getEmail())).thenReturn(false);
        when(userRepository.existsByUsername(pending.getRegistrationUsername())).thenReturn(false);

        userService.verifyRegistrationOtp(pending.getEmail(), pending.getOtp());

        verify(userRepository).save(argThat(user ->
            user.getEmail().equals(pending.getEmail())
                && user.getUsername().equals("newuser")
                && user.getPassword().equals("encoded_password")
                && user.isEmailVerified()
                && "CUSTOMER".equals(user.getRole())));
        verify(otpRepository).deleteByEmail(pending.getEmail());
        }

        @Test
        void verifyRegistrationOtp_WrongOtpDoesNotCreateUser() {
        when(otpRepository.findByEmailAndOtpAndPurpose("newuser@timeverse.com", "999999", "REGISTRATION"))
            .thenReturn(Optional.empty());

        assertThrows(BadRequestException.class,
            () -> userService.verifyRegistrationOtp("newuser@timeverse.com", "999999"));

        verify(userRepository, never()).save(any(User.class));
        }

        @Test
        void verifyRegistrationOtp_ExpiredOtpDoesNotCreateUser() {
        Otp pending = pendingRegistration("newuser@timeverse.com", "newuser", "encoded_password");
        pending.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        when(otpRepository.findByEmailAndOtpAndPurpose(pending.getEmail(), pending.getOtp(), "REGISTRATION"))
            .thenReturn(Optional.of(pending));

        assertThrows(BadRequestException.class,
            () -> userService.verifyRegistrationOtp(pending.getEmail(), pending.getOtp()));

        verify(userRepository, never()).save(any(User.class));
        }

        @Test
        void resendRegistrationOtp_UsesPendingRegistrationData() {
        Otp pending = pendingRegistration("newuser@timeverse.com", "newuser", "encoded_password");
        when(userRepository.existsByEmail(pending.getEmail())).thenReturn(false);
        when(otpRepository.findFirstByEmailOrderByCreatedAtDesc(pending.getEmail()))
            .thenReturn(Optional.of(pending));
        when(emailService.sendRegistrationOtp(eq(pending.getEmail()), matches("\\d{6}")))
            .thenReturn(true);

        userService.resendRegistrationOtp(pending.getEmail());

        verify(otpRepository).deleteByEmail(pending.getEmail());
        verify(otpRepository).save(any(Otp.class));
        verify(emailService).sendRegistrationOtp(eq(pending.getEmail()), matches("\\d{6}"));
        }

    @Test
    void verifiedCustomerCanLogIn() {
        testUser.setEmailVerified(true);
        testUser.setFirstLoginOtpVerified(true);
        LoginRequest request = new LoginRequest(
                testUser.getEmail(), "password123", "CUSTOMER");
        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(request.getPassword(), testUser.getPassword())).thenReturn(true);
        when(jwtService.generateToken(testUser.getEmail(), testUser.getUserId(), testUser.getRole()))
                .thenReturn("jwt-token");
        when(jwtService.extractSignature("jwt-token")).thenReturn("signature");
        when(jwtService.getExpirationTime()).thenReturn(86_400_000L);

        LoginResponse response = userService.loginUser(request);

        assertEquals("jwt-token", response.getToken());
        verify(jwtTokenRepository).save(any(JwtToken.class));
    }

    @Test
    void verifiedCustomerCanLogInWithoutOtpVerification() {
        testUser.setEmailVerified(true);
        testUser.setFirstLoginOtpVerified(false);
        LoginRequest request = new LoginRequest(
                testUser.getEmail(), "password123", "CUSTOMER");
        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(request.getPassword(), testUser.getPassword())).thenReturn(true);
        when(jwtService.generateToken(testUser.getEmail(), testUser.getUserId(), testUser.getRole()))
                .thenReturn("jwt-token");
        when(jwtService.extractSignature("jwt-token")).thenReturn("signature");
        when(jwtService.getExpirationTime()).thenReturn(86_400_000L);

        LoginResponse response = userService.loginUser(request);

        assertEquals("jwt-token", response.getToken());
        verify(jwtTokenRepository).save(any(JwtToken.class));
    }

    @Test
    void adminCanLogIn() {
        User adminUser = User.builder()
                .userId(2L)
                .username("admin")
                .email("admin@timeverse.com")
                .password("encoded_admin_password")
                .role("ADMIN")
                .emailVerified(true)
                .firstLoginOtpVerified(true)
                .build();

        LoginRequest request = new LoginRequest(
                adminUser.getEmail(), "admin123", "ADMIN");

        when(userRepository.findByEmail(request.getEmail()))
                .thenReturn(Optional.of(adminUser));

        when(passwordEncoder.matches(
                request.getPassword(),
                adminUser.getPassword()))
                .thenReturn(true);
        when(jwtService.generateToken(adminUser.getEmail(), adminUser.getUserId(), adminUser.getRole()))
                .thenReturn("jwt-token");
        when(jwtService.extractSignature("jwt-token")).thenReturn("signature");
        when(jwtService.getExpirationTime()).thenReturn(86_400_000L);

        LoginResponse response = userService.loginUser(request);

        assertNotNull(response);
        assertEquals(adminUser.getUsername(), response.getUsername());
        assertEquals(adminUser.getEmail(), response.getEmail());
        assertEquals("ADMIN", response.getRole());
        assertEquals("jwt-token", response.getToken());

        verify(otpRepository, never()).save(any(Otp.class));
        verify(emailService, never()).sendLoginOtp(anyString(), anyString());
        verify(jwtTokenRepository).save(any(JwtToken.class));
    }
    @Test
    void customerLoggingInAsAdmin_IsRejected() {
        testUser.setEmailVerified(true);
        LoginRequest request = new LoginRequest(
                testUser.getEmail(), "password123", "ADMIN");
        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.of(testUser));

        assertThrows(UnauthorizedException.class, () -> userService.loginUser(request));
    }

    @Test
    void adminLoggingInAsCustomer_IsRejected() {
        User adminUser = User.builder()
                .userId(2L)
                .username("admin")
                .email("admin@timeverse.com")
                .password("encoded_admin_password")
                .role("ADMIN")
                .emailVerified(true)
                .build();
        LoginRequest request = new LoginRequest(
                adminUser.getEmail(), "admin123", "CUSTOMER");
        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.of(adminUser));

        assertThrows(UnauthorizedException.class, () -> userService.loginUser(request));
    }

        private Otp pendingRegistration(String email, String username, String passwordHash) {
        return Otp.builder()
            .email(email)
            .registrationUsername(username)
            .registrationPasswordHash(passwordHash)
            .otp("123456")
            .createdAt(LocalDateTime.now())
            .expiresAt(LocalDateTime.now().plusMinutes(5))
            .verified(false)
            .build();
        }

    @Test
    void forgotPassword_Success() {
        ForgotPasswordRequest request = new ForgotPasswordRequest("test@timeverse.com");
        when(userRepository.existsByEmail(request.getEmail())).thenReturn(true);
        when(emailService.sendHtmlEmail(any(), any(), any())).thenReturn(true);

        userService.forgotPassword(request);

        verify(otpRepository, times(1)).deleteByEmail(request.getEmail());
        verify(otpRepository, times(1)).save(any(Otp.class));
    }

    @Test
    void forgotPassword_EmailNotFound_ThrowsException() {
        ForgotPasswordRequest request = new ForgotPasswordRequest("unknown@timeverse.com");
        when(userRepository.existsByEmail(request.getEmail())).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () -> userService.forgotPassword(request));
        verify(otpRepository, never()).save(any(Otp.class));
    }

    // ================= verifyOtp() Tests =================

    @Test
    void verifyOtp_Success() {
        VerifyOtpRequest request = new VerifyOtpRequest("test@timeverse.com", "123456");
        Otp otpEntity = Otp.builder()
                .email("test@timeverse.com")
                .otp("123456")
                .expiresAt(LocalDateTime.now().plusMinutes(5))
                .verified(false)
                .build();

        when(otpRepository.findByEmailAndOtpAndPurpose(request.getEmail(), request.getOtp(), "PASSWORD_RESET"))
                .thenReturn(Optional.of(otpEntity));

        userService.verifyOtp(request);

        assertTrue(otpEntity.isVerified());
        verify(otpRepository, times(1)).save(otpEntity);
    }

    @Test
    void verifyOtp_InvalidOtp_ThrowsException() {
        VerifyOtpRequest request = new VerifyOtpRequest("test@timeverse.com", "999999");
        when(otpRepository.findByEmailAndOtpAndPurpose(request.getEmail(), request.getOtp(), "PASSWORD_RESET"))
                .thenReturn(Optional.empty());

        assertThrows(BadRequestException.class, () -> userService.verifyOtp(request));
    }

    @Test
    void verifyOtp_ExpiredOtp_ThrowsException() {
        VerifyOtpRequest request = new VerifyOtpRequest("test@timeverse.com", "123456");
        Otp otpEntity = Otp.builder()
                .email("test@timeverse.com")
                .otp("123456")
                .expiresAt(LocalDateTime.now().minusMinutes(1))
                .verified(false)
                .build();

        when(otpRepository.findByEmailAndOtpAndPurpose(request.getEmail(), request.getOtp(), "PASSWORD_RESET"))
                .thenReturn(Optional.of(otpEntity));

        assertThrows(BadRequestException.class, () -> userService.verifyOtp(request));
    }

    @Test
    void verifyOtp_AlreadyUsedOtp_ThrowsException() {
        VerifyOtpRequest request = new VerifyOtpRequest("test@timeverse.com", "123456");
        Otp otpEntity = Otp.builder()
                .email("test@timeverse.com")
                .otp("123456")
                .expiresAt(LocalDateTime.now().plusMinutes(5))
                .verified(true)
                .build();

        when(otpRepository.findByEmailAndOtpAndPurpose(request.getEmail(), request.getOtp(), "PASSWORD_RESET"))
                .thenReturn(Optional.of(otpEntity));

        assertThrows(BadRequestException.class, () -> userService.verifyOtp(request));
    }

    // ================= resetPassword() Tests =================

    @Test
    void resetPassword_Success() {
        ResetPasswordRequest request = new ResetPasswordRequest("test@timeverse.com", "newSecurePassword123");
        Otp otpEntity = Otp.builder()
                .email("test@timeverse.com")
                .otp("123456")
                .verified(true)
                .purpose("PASSWORD_RESET")
                .expiresAt(LocalDateTime.now().plusMinutes(5))
                .build();

        when(otpRepository.findFirstByEmailOrderByCreatedAtDesc(request.getEmail()))
                .thenReturn(Optional.of(otpEntity));
        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.of(testUser));
        when(passwordEncoder.encode(request.getNewPassword())).thenReturn("encoded_new_password");

        userService.resetPassword(request);

        assertEquals("encoded_new_password", testUser.getPassword());
        verify(userRepository, times(1)).save(testUser);
        verify(otpRepository, times(1)).delete(otpEntity);
    }

    @Test
    void resetPassword_OtpNotVerified_ThrowsException() {
        ResetPasswordRequest request = new ResetPasswordRequest("test@timeverse.com", "newSecurePassword123");
        Otp otpEntity = Otp.builder()
                .email("test@timeverse.com")
                .otp("123456")
                .verified(false)
                .purpose("PASSWORD_RESET")
                .expiresAt(LocalDateTime.now().plusMinutes(5))
                .build();

        when(otpRepository.findFirstByEmailOrderByCreatedAtDesc(request.getEmail()))
                .thenReturn(Optional.of(otpEntity));

        assertThrows(BadRequestException.class, () -> userService.resetPassword(request));
        verify(userRepository, never()).save(any(User.class));
    }

    // ================= changePassword() Tests =================

    @Test
    void changePassword_Success() {
        ChangePasswordRequest request = new ChangePasswordRequest("oldPassword123", "newPassword123");
        
        // Mock Security Context
        SecurityContext securityContext = mock(SecurityContext.class);
        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn("test@timeverse.com");
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);

        when(userRepository.findByEmail("test@timeverse.com")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(request.getOldPassword(), testUser.getPassword())).thenReturn(true);
        when(passwordEncoder.encode(request.getNewPassword())).thenReturn("encoded_new_password");

        userService.changePassword(request);

        assertEquals("encoded_new_password", testUser.getPassword());
        verify(userRepository, times(1)).save(testUser);
    }

    @Test
    void changePassword_IncorrectOldPassword_ThrowsException() {
        ChangePasswordRequest request = new ChangePasswordRequest("wrongOldPassword", "newPassword123");

        // Mock Security Context
        SecurityContext securityContext = mock(SecurityContext.class);
        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn("test@timeverse.com");
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);

        when(userRepository.findByEmail("test@timeverse.com")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(request.getOldPassword(), testUser.getPassword())).thenReturn(false);

        assertThrows(BadRequestException.class, () -> userService.changePassword(request));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void changePassword_NotLoggedIn_ThrowsException() {
        ChangePasswordRequest request = new ChangePasswordRequest("oldPassword123", "newPassword123");

        // Mock Security Context empty/anonymous
        SecurityContext securityContext = mock(SecurityContext.class);
        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn("anonymousUser");
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);

        assertThrows(UnauthorizedException.class, () -> userService.changePassword(request));
    }


    @Test
    void verifyLoginOtp_Success_DeletesOtp() {
        String email = "admin@timeverse.com";
        String otp = "123456";
        Otp otpEntity = Otp.builder()
                .email(email)
                .otp(otp)
                .expiresAt(LocalDateTime.now().plusMinutes(5))
                .verified(false)
                .build();
        User adminUser = User.builder()
                .userId(2L)
                .username("admin")
                .email(email)
                .role("ADMIN")
                .build();

        when(otpRepository.findByEmailAndOtpAndPurpose(email, otp, "LOGIN")).thenReturn(Optional.of(otpEntity));
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(adminUser));
        when(jwtService.generateToken(email, adminUser.getUserId(), adminUser.getRole())).thenReturn("jwt-token");
        when(jwtService.extractSignature("jwt-token")).thenReturn("signature");
        when(jwtService.getExpirationTime()).thenReturn(86_400_000L);

        LoginResponse response = userService.verifyLoginOtp(email, otp);

        assertEquals("jwt-token", response.getToken());
        verify(otpRepository).delete(otpEntity);
    }

    @Test
    void verifyLoginOtp_IncorrectOtp_ThrowsExceptionAndDoesNotDeleteCorrectOtp() {
        String email = "admin@timeverse.com";
        String incorrectOtp = "999999";

        when(otpRepository.findByEmailAndOtpAndPurpose(email, incorrectOtp, "LOGIN")).thenReturn(Optional.empty());

        assertThrows(BadRequestException.class, () -> userService.verifyLoginOtp(email, incorrectOtp));

        verify(otpRepository, never()).delete(any(Otp.class));
    }
}

package com.timeverse.backend.service;

import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.timeverse.backend.exception.BadRequestException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class EmailService {

    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);
    private static final Pattern SMTP_STATUS_PATTERN = Pattern.compile("\\b([245]\\d{2})(?:[- ]|$)");

    private final JavaMailSender mailSender;

    @Value("${mail.smtp.diagnostic.enabled:false}")
    private boolean diagnosticEmailEnabled;

    @Value("${spring.mail.host:smtp-relay.brevo.com}")
    private String mailHost;

    @Value("${spring.mail.port:2525}")
    private int mailPort;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    @Value("${spring.mail.password:}")
    private String mailPassword;

    @Value("${spring.mail.properties.mail.smtp.auth:true}")
    private boolean smtpAuth;

    @Value("${spring.mail.properties.mail.smtp.starttls.enable:true}")
    private boolean smtpStarttls;

    @Value("${spring.mail.properties.mail.smtp.ssl.enable:false}")
    private boolean smtpSsl;

    @Value("${spring.mail.from:supritabiradar3@gmail.com}")
    private String senderEmail;

    public static class SmtpDiagnosticDetails {
        public String result = "SUCCESS";
        public String smtpCode = "<none>";
        public String exceptionClass = "<none>";
        public String sanitizedReason = "<none>";
        public String failureType = "SUCCESS";
        public String userMessage = "Unable to send verification OTP: SMTP delivery failed";
    }

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public boolean sendHtmlEmail(String to, String subject, String htmlContent) {
        try {
            logger.info("[OTP EMAIL FLOW] EmailService reached");
            logger.info("[OTP] Registration OTP email sending started");
            logger.info("[OTP] Recipient: {}", to);
            if (to != null && to.contains("@")) {
                String domain = to.substring(to.indexOf("@") + 1);
                logger.info("[OTP] Recipient Domain: {}", domain);
            }
            logger.info("[OTP] From address: {}", senderEmail);
            if (mailSender instanceof org.springframework.mail.javamail.JavaMailSenderImpl) {
                org.springframework.mail.javamail.JavaMailSenderImpl impl = (org.springframework.mail.javamail.JavaMailSenderImpl) mailSender;
                if (diagnosticEmailEnabled) {
                    logger.info("[SMTP DIAGNOSTIC] Host: {}, Port: {}, Username: {}, passwordPresent={}",
                            impl.getHost(),
                            impl.getPort(),
                            impl.getUsername(),
                            impl.getPassword() != null && !impl.getPassword().isEmpty());
                    impl.getJavaMailProperties().put("mail.debug", "true");
                }
            }
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            
            helper.setFrom(senderEmail);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);
            
            logger.info("[SMTP SEND ATTEMPT] host={} port={} usernamePresent={} passwordPresent={}",
                    mailHost, mailPort, (mailUsername != null && !mailUsername.isBlank()), (mailPassword != null && !mailPassword.isBlank()));
            logger.info("[OTP] Calling JavaMailSender.send()...");
            mailSender.send(message);
            logger.info("[OTP] JavaMailSender.send() completed successfully without exception");

            SmtpDiagnosticDetails successDiag = new SmtpDiagnosticDetails();
            successDiag.result = "SUCCESS";
            logSmtpDiagnostic(successDiag);

            return true;
        } catch (MailException e) {
            SmtpDiagnosticDetails diag = analyzeMailException(e);
            logSmtpDiagnostic(diag);
            throw new BadRequestException(diag.userMessage);
        } catch (Exception e) {
            SmtpDiagnosticDetails diag = analyzeMailException(e);
            logSmtpDiagnostic(diag);
            return false;
        }
    }

    private void logSmtpDiagnostic(SmtpDiagnosticDetails diag) {
        logger.info("[SMTP DIAGNOSTIC] =================================");
        logger.info("[SMTP DIAGNOSTIC] host={}", mailHost);
        logger.info("[SMTP DIAGNOSTIC] port={}", mailPort);
        logger.info("[SMTP DIAGNOSTIC] auth={}", smtpAuth);
        logger.info("[SMTP DIAGNOSTIC] starttls={}", smtpStarttls);
        logger.info("[SMTP DIAGNOSTIC] ssl={}", smtpSsl);
        logger.info("[SMTP DIAGNOSTIC] usernamePresent={}", mailUsername != null && !mailUsername.isBlank());
        logger.info("[SMTP DIAGNOSTIC] passwordPresent={}", mailPassword != null && !mailPassword.isBlank());
        logger.info("[SMTP DIAGNOSTIC] passwordLength={}", mailPassword != null ? mailPassword.length() : 0);
        logger.info("[SMTP DIAGNOSTIC] RESULT={}", diag.result);
        if (!"SUCCESS".equals(diag.result)) {
            logger.info("[SMTP DIAGNOSTIC] smtpCode={}", diag.smtpCode);
            logger.info("[SMTP DIAGNOSTIC] exceptionClass={}", diag.exceptionClass);
            logger.info("[SMTP DIAGNOSTIC] sanitizedReason={}", diag.sanitizedReason);
        }
        logger.info("[SMTP DIAGNOSTIC] =================================");
    }

    private SmtpDiagnosticDetails analyzeMailException(Throwable exception) {
        SmtpDiagnosticDetails diag = new SmtpDiagnosticDetails();
        diag.exceptionClass = exception.getClass().getName();
        diag.sanitizedReason = sanitizeMessage(exception.getMessage());

        List<Throwable> allExceptions = new ArrayList<>();
        collectAllExceptions(exception, allExceptions, new HashSet<>());

        String detectedSmtpCode = null;
        String detectedFailureType = null;
        String detectedUserMessage = null;

        for (Throwable t : allExceptions) {
            String msg = sanitizeMessage(t.getMessage());
            String lower = msg.toLowerCase();

            if (detectedSmtpCode == null) {
                Matcher matcher = SMTP_STATUS_PATTERN.matcher(msg);
                if (matcher.find()) {
                    detectedSmtpCode = matcher.group(1);
                }
            }

            if (t instanceof jakarta.mail.AuthenticationFailedException
                    || t instanceof org.springframework.mail.MailAuthenticationException) {
                detectedFailureType = "AUTHENTICATION_FAILED";
                if (detectedSmtpCode == null) detectedSmtpCode = "535";
                detectedUserMessage = "Unable to send verification OTP: SMTP authentication failed";
            } else if (lower.contains("535")
                    || lower.contains("authentication failed")
                    || lower.contains("username and password not accepted")
                    || lower.contains("bad credentials")
                    || lower.contains("invalid login")) {
                if (detectedFailureType == null) detectedFailureType = "AUTHENTICATION_FAILED";
                if (detectedSmtpCode == null) detectedSmtpCode = "535";
                if (detectedUserMessage == null) detectedUserMessage = "Unable to send verification OTP: SMTP authentication failed";
            } else if (t instanceof java.net.ConnectException || lower.contains("connection refused")) {
                if (detectedFailureType == null) detectedFailureType = "CONNECTION_REFUSED";
                if (detectedUserMessage == null) detectedUserMessage = "Unable to send verification OTP: SMTP connection refused";
            } else if (t instanceof java.net.SocketTimeoutException || lower.contains("timed out") || lower.contains("timeout")) {
                if (detectedFailureType == null) detectedFailureType = "CONNECTION_TIMEOUT";
                if (detectedUserMessage == null) detectedUserMessage = "Unable to send verification OTP: SMTP connection timed out";
            } else if (t instanceof java.net.UnknownHostException || lower.contains("unknown host") || lower.contains("no such host")) {
                if (detectedFailureType == null) detectedFailureType = "HOST_RESOLUTION_FAILED";
                if (detectedUserMessage == null) detectedUserMessage = "Unable to send verification OTP: SMTP host resolution failed";
            } else if (lower.contains("starttls") || lower.contains("could not convert socket to tls") || lower.contains("tls negotiation")) {
                if (detectedFailureType == null) detectedFailureType = "TLS_STARTTLS_FAILED";
                if (detectedUserMessage == null) detectedUserMessage = "Unable to send verification OTP: SMTP TLS negotiation failed";
            } else if (lower.contains("ssl") || lower.contains("handshake")) {
                if (detectedFailureType == null) detectedFailureType = "SSL_FAILED";
                if (detectedUserMessage == null) detectedUserMessage = "Unable to send verification OTP: SMTP SSL negotiation failed";
            } else if (lower.contains("sender") && (lower.contains("rejected") || lower.contains("not allowed") || lower.contains("invalid") || lower.contains("unverified"))) {
                if (detectedFailureType == null) detectedFailureType = "SENDER_REJECTED";
                if (detectedUserMessage == null) detectedUserMessage = "Unable to send verification OTP: SMTP provider rejected sender address";
            } else if (lower.contains("recipient") || lower.contains("mailbox") || lower.contains("user not found") || lower.contains("no such user")) {
                if (detectedFailureType == null) detectedFailureType = "RECIPIENT_REJECTED";
                if (detectedUserMessage == null) detectedUserMessage = "Unable to send verification OTP: SMTP provider rejected recipient address";
            } else if (detectedSmtpCode != null && detectedSmtpCode.startsWith("4")) {
                if (detectedFailureType == null) detectedFailureType = "SMTP_TEMPORARY_FAILURE";
                if (detectedUserMessage == null) detectedUserMessage = "Unable to send verification OTP: SMTP server temporary failure";
            } else if (detectedSmtpCode != null && detectedSmtpCode.startsWith("5")) {
                if (detectedFailureType == null) detectedFailureType = "SMTP_PERMANENT_FAILURE";
                if (detectedUserMessage == null) detectedUserMessage = "Unable to send verification OTP: SMTP provider rejected delivery";
            }
        }

        diag.failureType = detectedFailureType != null ? detectedFailureType : "UNKNOWN_ERROR";
        diag.result = diag.failureType;
        diag.smtpCode = detectedSmtpCode != null ? detectedSmtpCode : "<none>";
        diag.userMessage = detectedUserMessage != null ? detectedUserMessage : "Unable to send verification OTP: SMTP delivery failed";

        Throwable root = rootCauseOf(exception);
        diag.exceptionClass = root.getClass().getName();
        diag.sanitizedReason = sanitizeMessage(root.getMessage());

        return diag;
    }

    private void collectAllExceptions(Throwable t, List<Throwable> list, Set<Throwable> visited) {
        if (t == null || visited.contains(t)) return;
        visited.add(t);
        list.add(t);

        if (t instanceof org.springframework.mail.MailSendException) {
            org.springframework.mail.MailSendException mse = (org.springframework.mail.MailSendException) t;
            if (mse.getMessageExceptions() != null) {
                for (Exception ex : mse.getMessageExceptions()) {
                    collectAllExceptions(ex, list, visited);
                }
            }
        }

        if (t instanceof jakarta.mail.MessagingException) {
            jakarta.mail.MessagingException me = (jakarta.mail.MessagingException) t;
            Exception next = me.getNextException();
            if (next != null) {
                collectAllExceptions(next, list, visited);
            }
        }

        if (t.getCause() != null && t.getCause() != t) {
            collectAllExceptions(t.getCause(), list, visited);
        }
    }

    private Throwable rootCauseOf(Throwable throwable) {
        Throwable rootCause = throwable;
        while (rootCause.getCause() != null && rootCause.getCause() != rootCause) {
            rootCause = rootCause.getCause();
        }
        return rootCause;
    }

    private String sanitizeMessage(String message) {
        if (message == null || message.isBlank()) {
            return "<no message>";
        }
        String redacted = message.replaceAll("(?i)(password|passphrase|app password|token|key|secret)[=:\\s]+\\S+", "$1=[REDACTED]");
        redacted = redacted.replaceAll("(?i)bearer\\s+[A-Za-z0-9_.-]+", "Bearer [REDACTED]");
        redacted = redacted.replaceAll("(?i)basic\\s+[A-Za-z0-9+/=]+", "Basic [REDACTED]");
        redacted = redacted.replaceAll("(?i)xsmtpsib-[A-Za-z0-9_-]+", "[REDACTED_BREVO_KEY]");
        if (mailPassword != null && !mailPassword.isBlank()) {
            redacted = redacted.replace(mailPassword, "[REDACTED]");
        }
        return redacted.trim();
    }

    public String buildTemplate(String bodyContent) {
        return "<!DOCTYPE html>\n" +
                "<html>\n" +
                "<head>\n" +
                "    <meta charset=\"UTF-8\">\n" +
                "    <style>\n" +
                "        body { font-family: 'Outfit', 'Helvetica Neue', Helvetica, Arial, sans-serif; background-color: #111111; color: #FFFFFF; margin: 0; padding: 0; }\n" +
                "        .container { max-width: 600px; margin: 0 auto; padding: 40px 20px; background-color: #111111; }\n" +
                "        .card { background-color: #1a1a1a; border: 1px solid #c5a880; border-radius: 8px; padding: 40px; box-shadow: 0 4px 20px rgba(0, 0, 0, 0.5); text-align: center; }\n" +
                "        .logo { font-family: Georgia, serif; font-size: 26px; letter-spacing: 2px; color: #c5a880; margin-bottom: 30px; text-transform: uppercase; font-weight: bold; }\n" +
                "        h1 { font-size: 20px; margin-bottom: 20px; color: #ffffff; text-transform: uppercase; letter-spacing: 1px; }\n" +
                "        p { color: #aaaaaa; font-size: 14px; line-height: 1.6; margin-bottom: 30px; text-align: center; }\n" +
                "        .otp-code { font-size: 32px; font-weight: bold; letter-spacing: 6px; color: #c5a880; padding: 15px 30px; border: 1px dashed rgba(197, 168, 128, 0.4); border-radius: 4px; display: inline-block; background-color: rgba(197, 168, 128, 0.05); margin-bottom: 30px; }\n" +
                "        .footer { margin-top: 40px; border-top: 1px solid #222222; padding-top: 20px; font-size: 11px; color: #555555; text-align: center; }\n" +
                "        .btn { display: inline-block; padding: 12px 24px; background-color: #c5a880; color: #111111 !important; text-decoration: none; border-radius: 4px; font-weight: bold; font-size: 14px; text-transform: uppercase; letter-spacing: 1px; margin-top: 10px; }\n" +
                "        .item-list { width: 100%; border-collapse: collapse; margin-top: 20px; margin-bottom: 30px; text-align: left; }\n" +
                "        .item-row th { border-bottom: 1px solid #222222; padding: 10px; font-size: 12px; color: #c5a880; text-transform: uppercase; }\n" +
                "        .item-row td { border-bottom: 1px solid #222222; padding: 12px 10px; font-size: 13px; color: #aaaaaa; }\n" +
                "    </style>\n" +
                "</head>\n" +
                "<body>\n" +
                "    <div class=\"container\">\n" +
                "        <div class=\"card\">\n" +
                "            <div class=\"logo\">TimeVerse</div>\n" +
                "            " + bodyContent + "\n" +
                "            <div class=\"footer\">\n" +
                "                This is an automated communication from TimeVerse Luxury Boutique.<br>\n" +
                "                &copy; 2026 TimeVerse. All Rights Reserved.\n" +
                "            </div>\n" +
                "        </div>\n" +
                "    </div>\n" +
                "</body>\n" +
                "</html>";
    }

    public boolean sendRegistrationOtp(String to, String otp) {
        String body = "<h1>Verify Your Email</h1>\n" +
                "<p>Thank you for registering at TimeVerse. Use the verification code below to verify your email address and activate your account:</p>\n" +
                "<div class=\"otp-code\">" + otp + "</div>\n" +
                "<p>This code will expire in 15 minutes.</p>";
        if (sendHtmlEmail(to, "TimeVerse Registration OTP", buildTemplate(body))) {
            logger.info("OTP email sent successfully");
            if (diagnosticEmailEnabled) {
                sendSmtpDiagnosticEmail(to);
            }
            return true;
        }
        return false;
    }

    private void sendSmtpDiagnosticEmail(String to) {
        logger.info("Sending temporary SMTP diagnostic email to: {}", to);
        String body = "SMTP diagnostic message from TimeVerse. The registration email sender accepted this message.";
        if (sendHtmlEmail(to, "TimeVerse SMTP Diagnostic", body)) {
            logger.info("Temporary SMTP diagnostic email accepted by JavaMailSender");
        }
    }

    public boolean sendLoginOtp(String to, String otp) {
        String body = "<h1>Verify Your Login</h1>\n" +
                "<p>A login attempt requires verification. Use the verification code below to authenticate your session:</p>\n" +
                "<div class=\"otp-code\">" + otp + "</div>\n" +
                "<p>This code will expire in 15 minutes.</p>";
        return sendHtmlEmail(to, "TimeVerse Login OTP", buildTemplate(body));
    }

    public void sendOrderConfirmation(String to, Long orderId, double totalAmount, String itemsHtml) {
        String body = "<h1>Order Placed Successfully</h1>\n" +
                "<p>Thank you for shopping at TimeVerse. Your order <strong>#TV-ORD-" + orderId + "</strong> has been successfully placed. We are currently verifying your concierge payment.</p>\n" +
                "<table class=\"item-list\">\n" +
                "    <thead>\n" +
                "        <tr class=\"item-row\">\n" +
                "            <th style=\"border-bottom: 1px solid #222222; padding: 10px; font-size: 12px; color: #c5a880; text-transform: uppercase;\">Item</th>\n" +
                "            <th style=\"border-bottom: 1px solid #222222; padding: 10px; font-size: 12px; color: #c5a880; text-transform: uppercase; text-align: right;\">Amount</th>\n" +
                "        </tr>\n" +
                "    </thead>\n" +
                "    <tbody>\n" +
                "        " + itemsHtml + "\n" +
                "    </tbody>\n" +
                "</table>\n" +
                "<p style=\"font-size: 16px; font-weight: bold; color: #c5a880; text-align: center;\">Total Amount: ₹" + String.format("%.2f", totalAmount) + "</p>";
        sendHtmlEmail(to, "TimeVerse Order Confirmation", buildTemplate(body));
    }

    public void sendPaymentConfirmation(String to, Long orderId, double totalAmount, String paymentId) {
        String body = "<h1>Payment Confirmed</h1>\n" +
                "<p>We have successfully verified your payment for order <strong>#TV-ORD-" + orderId + "</strong>.</p>\n" +
                "<p>Transaction ID: <strong>" + paymentId + "</strong></p>\n" +
                "<p>Your luxury timepiece is now being secured and prepared for premium delivery.</p>\n" +
                "<p style=\"font-size: 16px; font-weight: bold; color: #c5a880; text-align: center;\">Amount Paid: ₹" + String.format("%.2f", totalAmount) + "</p>";
        sendHtmlEmail(to, "TimeVerse Payment Confirmation", buildTemplate(body));
    }

    public void sendOrderStatusUpdate(String to, Long orderId, String newStatus) {
        String body = "<h1>Order Status Updated</h1>\n" +
                "<p>The status of your order <strong>#TV-ORD-" + orderId + "</strong> has been updated to:</p>\n" +
                "<div class=\"otp-code\">" + newStatus + "</div>\n" +
                "<p>If you have any questions or require further concierge assistance, please reply directly to this email.</p>";
        sendHtmlEmail(to, "TimeVerse Order Status Update", buildTemplate(body));
    }
}

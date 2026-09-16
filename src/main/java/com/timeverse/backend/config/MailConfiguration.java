package com.timeverse.backend.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.annotation.PostConstruct;

@Configuration
public class MailConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(MailConfiguration.class);

    @Autowired
    private Environment environment;

    @Value("${spring.mail.username}")
    private String mailUsername;

    @Value("${spring.mail.password}")
    private String mailPassword;

    @Value("${spring.mail.host}")
    private String mailHost;

    @Value("${spring.mail.port}")
    private int mailPort;

    @Value("${spring.mail.properties.mail.smtp.auth}")
    private boolean smtpAuth;

    @Value("${spring.mail.properties.mail.smtp.starttls.enable:false}")
    private boolean smtpStarttls;

    @Value("${spring.mail.properties.mail.smtp.ssl.enable:false}")
    private boolean smtpSsl;

    @Value("${spring.mail.from:supritabiradar3@gmail.com}")
    private String mailFrom;

    @PostConstruct
    public void validateMailCredentials() {
        if (mailUsername == null || mailUsername.isBlank() || mailPassword == null || mailPassword.isBlank()) {
            throw new IllegalStateException(
                    "SMTP is not configured. Set MAIL_USERNAME and MAIL_PASSWORD environment variables "
                            + "before starting the application.");
        }

        String envSpringMailHost = System.getenv("SPRING_MAIL_HOST");
        String envMailHost = System.getenv("MAIL_HOST");
        String envSpringMailPort = System.getenv("SPRING_MAIL_PORT");
        String envMailPort = System.getenv("MAIL_PORT");
        String envSpringMailUsername = System.getenv("SPRING_MAIL_USERNAME");
        String envMailUsername = System.getenv("MAIL_USERNAME");
        String envTvMailUsername = System.getenv("TV_MAIL_USERNAME");
        String envSpringMailPassword = System.getenv("SPRING_MAIL_PASSWORD");
        String envMailPassword = System.getenv("MAIL_PASSWORD");
        String envTvMailPassword = System.getenv("TV_MAIL_PASSWORD");
        String envMailFrom = System.getenv("MAIL_FROM");

        boolean matchesSpringMailPassword = envSpringMailPassword != null && envSpringMailPassword.equals(mailPassword);
        boolean matchesMailPassword = envMailPassword != null && envMailPassword.equals(mailPassword);
        boolean matchesTvMailPassword = envTvMailPassword != null && envTvMailPassword.equals(mailPassword);

        String usernameDomain = (mailUsername != null && mailUsername.contains("@"))
                ? mailUsername.substring(mailUsername.indexOf("@") + 1)
                : "<none>";

        logger.info("[SMTP STARTUP DIAGNOSTIC] =================================");
        logger.info("[SMTP STARTUP DIAGNOSTIC] resolvedHost='{}'", mailHost);
        logger.info("[SMTP STARTUP DIAGNOSTIC] resolvedPort={}", mailPort);
        logger.info("[SMTP STARTUP DIAGNOSTIC] authEnabled={}", smtpAuth);
        logger.info("[SMTP STARTUP DIAGNOSTIC] starttlsEnabled={}", smtpStarttls);
        logger.info("[SMTP STARTUP DIAGNOSTIC] sslEnabled={}", smtpSsl);
        logger.info("[SMTP STARTUP DIAGNOSTIC] usernamePresent={}, usernameDomain='{}'", !mailUsername.isBlank(), usernameDomain);
        logger.info("[SMTP STARTUP DIAGNOSTIC] passwordPresent={}, passwordLength={}", !mailPassword.isBlank(), mailPassword.length());
        logger.info("[SMTP STARTUP DIAGNOSTIC] mailFrom='{}'", mailFrom);
        logger.info("[SMTP STARTUP DIAGNOSTIC] env.SPRING_MAIL_HOST(present={}), env.MAIL_HOST(present={})",
                envSpringMailHost != null && !envSpringMailHost.isBlank(), envMailHost != null && !envMailHost.isBlank());
        logger.info("[SMTP STARTUP DIAGNOSTIC] env.SPRING_MAIL_PORT(present={}), env.MAIL_PORT(present={})",
                envSpringMailPort != null && !envSpringMailPort.isBlank(), envMailPort != null && !envMailPort.isBlank());
        logger.info("[SMTP STARTUP DIAGNOSTIC] env.SPRING_MAIL_USERNAME(present={}), env.MAIL_USERNAME(present={})",
                envSpringMailUsername != null && !envSpringMailUsername.isBlank(), envMailUsername != null && !envMailUsername.isBlank());
        logger.info("[SMTP STARTUP DIAGNOSTIC] env.SPRING_MAIL_PASSWORD(present={}), env.MAIL_PASSWORD(present={})",
                envSpringMailPassword != null && !envSpringMailPassword.isBlank(), envMailPassword != null && !envMailPassword.isBlank());
        logger.info("[SMTP STARTUP DIAGNOSTIC] resolvedFromSpringMailPassword={}, resolvedFromMailPassword={}",
                matchesSpringMailPassword, matchesMailPassword);
        logger.info("[SMTP STARTUP DIAGNOSTIC] =================================");

        if ((mailHost != null && (mailHost.contains("aivencloud.com") || mailHost.contains("mysql")))
                || "avnadmin".equalsIgnoreCase(mailUsername)
                || mailPort == 3306) {
            logger.warn(
                    "[SECURITY WARNING: DATABASE CONFIG IN MAIL PROPERTIES] Database credentials (host/port/user) detected in mail properties! Ensure SPRING_MAIL_HOST, SPRING_MAIL_PORT, SPRING_MAIL_USERNAME are removed from Render and configured under DB_HOST, DB_PORT, DB_USERNAME, DB_PASSWORD, DB_NAME instead.");
        }
    }
}
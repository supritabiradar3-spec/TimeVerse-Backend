package com.timeverse.backend.config;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.annotation.PostConstruct;

@Configuration
public class DatabaseConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseConfiguration.class);

    @Autowired
    private DataSource dataSource;

    @Value("${spring.datasource.url:}")
    private String datasourceUrl;

    @Value("${spring.datasource.username:}")
    private String datasourceUsername;

    @Value("${spring.datasource.password:}")
    private String datasourcePassword;

    @PostConstruct
    public void validateDatabaseConnection() {
        String sanitizedUrl = sanitizeJdbcUrl(datasourceUrl);
        String envDbHost = System.getenv("DB_HOST");
        String envDbPort = System.getenv("DB_PORT");
        String envDbName = System.getenv("DB_NAME");
        String envDbUsername = System.getenv("DB_USERNAME");
        String envDbPassword = System.getenv("DB_PASSWORD");
        String envSpringDsUrl = System.getenv("SPRING_DATASOURCE_URL");

        logger.info(
                "[DATASOURCE DIAGNOSTIC] Configuration: url='{}', usernamePresent={}, passwordPresent={}, env.DB_HOST='{}', env.DB_PORT='{}', env.DB_NAME='{}', env.DB_USERNAME='{}', env.SPRING_DATASOURCE_URL present={}",
                sanitizedUrl,
                datasourceUsername != null && !datasourceUsername.isBlank(),
                datasourcePassword != null && !datasourcePassword.isBlank(),
                envDbHost,
                envDbPort,
                envDbName,
                envDbUsername,
                envSpringDsUrl != null && !envSpringDsUrl.isBlank());

        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            logger.info(
                    "[DATASOURCE SUCCESS] Connected to database: product='{}', version='{}', url='{}'",
                    metaData.getDatabaseProductName(),
                    metaData.getDatabaseProductVersion(),
                    sanitizeJdbcUrl(metaData.getURL()));
        } catch (Exception ex) {
            logger.error("[DATASOURCE ERROR] Failed to establish database connection: {}", ex.getMessage());
        }
    }

    private String sanitizeJdbcUrl(String url) {
        if (url == null || url.isBlank()) {
            return "<none>";
        }
        return url.replaceAll("(?i)(password=)[^&;]+", "$1[REDACTED]");
    }
}

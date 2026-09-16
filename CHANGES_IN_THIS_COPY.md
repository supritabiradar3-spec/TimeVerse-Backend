# Changes in this corrected copy

Only one backend security fix was applied to the uploaded baseline:

- `src/main/java/com/timeverse/backend/config/SecurityConfig.java`: `/api/cart/**` now requires authentication because `CartService` already requires a valid JWT. This prevents the API security configuration from contradicting the service layer.

The OTP purpose changes already present in the uploaded source were preserved. In particular, registration, login, and password-reset OTP records carry their correct `purpose` value.

No database records were changed and no unrelated backend functionality was rewritten.

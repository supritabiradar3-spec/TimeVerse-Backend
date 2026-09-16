package com.timeverse.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CancelOrderRequest {
    private String reason;
    private String cancellationReason;

    public String getReason() {
        if (reason != null && !reason.trim().isEmpty()) {
            return reason;
        }
        return cancellationReason;
    }
}

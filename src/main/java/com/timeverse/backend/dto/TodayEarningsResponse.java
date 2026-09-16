package com.timeverse.backend.dto;

import java.math.BigDecimal;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TodayEarningsResponse {

    private BigDecimal todayEarnings;
    private String currency;
    private String currencySymbol;
    private String date;
    private int paidOrdersCount;
    private List<OrderResponse> paidOrders;
}

package com.timeverse.backend.dto;

import lombok.Data;

@Data
public class CartRequest {

    private Long productId;
    private Integer quantity;
}
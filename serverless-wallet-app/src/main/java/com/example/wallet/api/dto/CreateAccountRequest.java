package com.example.wallet.api.dto;

import java.math.BigDecimal;

public record CreateAccountRequest(BigDecimal initialBalance) {
}

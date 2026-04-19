package com.example.wallet.dto;

import java.math.BigDecimal;

public record CreateAccountRequest(BigDecimal initialBalance) {
}

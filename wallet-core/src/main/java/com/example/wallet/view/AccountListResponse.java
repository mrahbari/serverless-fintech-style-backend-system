package com.example.wallet.view;

import java.util.List;

public record AccountListResponse(List<AccountSummary> accounts, String nextCursor) {}

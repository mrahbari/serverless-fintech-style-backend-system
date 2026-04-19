package com.example.wallet.repository;

import com.example.wallet.domain.Account;

import java.util.List;

/**
 * One page of accounts from a single repository read (no per-row N+1 fetches).
 */
public record AccountPage(List<Account> items, String nextCursor) {}

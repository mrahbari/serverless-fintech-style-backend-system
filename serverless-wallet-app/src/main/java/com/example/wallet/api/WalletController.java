package com.example.wallet.api;

import com.example.wallet.dto.CreateAccountRequest;
import com.example.wallet.dto.DepositRequest;
import com.example.wallet.dto.TransferRequest;
import com.example.wallet.domain.TransferResult;
import com.example.wallet.handler.CreateAccountHandler;
import com.example.wallet.handler.DepositHandler;
import com.example.wallet.handler.GetAccountHandler;
import com.example.wallet.handler.ListAccountsHandler;
import com.example.wallet.handler.TransferHandler;
import com.example.wallet.view.AccountListResponse;
import com.example.wallet.view.AccountSummary;
import com.example.wallet.view.CreateAccountResult;
import com.example.wallet.view.DepositResult;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Thin API layer mapping HTTP to Lambda handlers; in AWS each route might invoke a different Lambda alias/version.
 */
@RestController
public class WalletController {

    private final CreateAccountHandler createAccountHandler;
    private final DepositHandler depositHandler;
    private final TransferHandler transferHandler;
    private final ListAccountsHandler listAccountsHandler;
    private final GetAccountHandler getAccountHandler;

    public WalletController(
            CreateAccountHandler createAccountHandler,
            DepositHandler depositHandler,
            TransferHandler transferHandler,
            ListAccountsHandler listAccountsHandler,
            GetAccountHandler getAccountHandler) {
        this.createAccountHandler = createAccountHandler;
        this.depositHandler = depositHandler;
        this.transferHandler = transferHandler;
        this.listAccountsHandler = listAccountsHandler;
        this.getAccountHandler = getAccountHandler;
    }

    /**
     * Cursor-based listing: one repository page read per request (not N+1 {@code findById} per account).
     * Pass {@code cursor} from the previous response's {@code nextCursor} for the next page.
     */
    @GetMapping("/accounts")
    public AccountListResponse listAccounts(
            @RequestParam(required = false) Integer limit, @RequestParam(required = false) String cursor) {
        return listAccountsHandler.handle(limit, cursor);
    }

    @GetMapping("/accounts/{accountId}")
    public AccountSummary getAccount(@PathVariable String accountId) {
        return getAccountHandler.handle(accountId);
    }

    @PostMapping("/accounts")
    @ResponseStatus(HttpStatus.CREATED)
    public CreateAccountResult createAccount(@RequestBody(required = false) CreateAccountRequest body) {
        var initial = body != null ? body.initialBalance() : null;
        return createAccountHandler.handle(initial);
    }

    @PostMapping("/deposit")
    public DepositResult deposit(@Valid @RequestBody DepositRequest request) {
        return depositHandler.handle(request.accountId(), request.amount());
    }

    @PostMapping("/transfer")
    public TransferResult transfer(@Valid @RequestBody TransferRequest request) {
        return transferHandler.handle(
                request.transactionId(),
                request.fromAccountId(),
                request.toAccountId(),
                request.amount());
    }
}

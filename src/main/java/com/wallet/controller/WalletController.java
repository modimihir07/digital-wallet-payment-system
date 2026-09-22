package com.wallet.controller;

import com.wallet.dto.TopUpRequest;
import com.wallet.model.LedgerEntry;
import com.wallet.model.Wallet;
import com.wallet.security.JwtAuthFilter;
import com.wallet.service.WalletService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Wallet endpoints for the authenticated user.
 */
@RestController
@RequestMapping("/api/wallet")
public class WalletController {

    private final WalletService walletService;

    /** Wires the service. */
    public WalletController(WalletService walletService) {
        this.walletService = walletService;
    }

    private static long userId(HttpServletRequest req) {
        return (Long) req.getAttribute(JwtAuthFilter.ATTR_USER_ID);
    }

    /**
     * Returns the caller's wallet.
     *
     * @param req HTTP request (auth attributes)
     * @return wallet
     */
    @GetMapping("/me")
    public ResponseEntity<Wallet> me(HttpServletRequest req) {
        return ResponseEntity.ok(walletService.myWallet(userId(req)));
    }

    /**
     * Returns the ledger-backed statement page.
     *
     * @param req  HTTP request
     * @param page zero-based page
     * @param size page size
     * @return entries
     */
    @GetMapping("/me/statement")
    public ResponseEntity<List<LedgerEntry>> statement(
            HttpServletRequest req,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(walletService.statement(userId(req), page, size));
    }

    /**
     * Simulates adding money.
     *
     * @param req     HTTP request
     * @param topUp   amount payload
     * @return txn id
     */
    @PostMapping("/topup")
    public ResponseEntity<Map<String, Object>> topUp(
            HttpServletRequest req, @Valid @RequestBody TopUpRequest topUp) {
        long txnId = walletService.topUp(userId(req), topUp);
        return ResponseEntity.ok(Map.of("txnId", txnId, "status", "SUCCESS"));
    }
}

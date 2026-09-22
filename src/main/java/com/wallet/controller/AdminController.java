package com.wallet.controller;

import com.wallet.model.AuditLog;
import com.wallet.model.Wallet;
import com.wallet.service.AuditService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Admin endpoints (ROLE_ADMIN enforced by JwtAuthFilter).
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AuditService auditService;

    /** Wires the service. */
    public AdminController(AuditService auditService) {
        this.auditService = auditService;
    }

    /**
     * Lists wallets.
     *
     * @param page zero-based page
     * @param size page size
     * @return wallets
     */
    @GetMapping("/wallets")
    public ResponseEntity<List<Wallet>> wallets(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(auditService.listWallets(page, size));
    }

    /**
     * Freezes a wallet.
     *
     * @param id wallet id
     * @return status
     */
    @PostMapping("/wallets/{id}/freeze")
    public ResponseEntity<Map<String, Object>> freeze(@PathVariable long id) {
        auditService.freeze(id);
        return ResponseEntity.ok(Map.of("walletId", id, "status", "FROZEN"));
    }

    /**
     * Unfreezes a wallet.
     *
     * @param id wallet id
     * @return status
     */
    @PostMapping("/wallets/{id}/unfreeze")
    public ResponseEntity<Map<String, Object>> unfreeze(@PathVariable long id) {
        auditService.unfreeze(id);
        return ResponseEntity.ok(Map.of("walletId", id, "status", "ACTIVE"));
    }

    /**
     * Reads the audit log.
     *
     * @param page zero-based page
     * @param size page size
     * @return rows
     */
    @GetMapping("/audit")
    public ResponseEntity<List<AuditLog>> audit(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(auditService.audit(page, size));
    }

    /**
     * Daily report for a date.
     *
     * @param date YYYY-MM-DD
     * @return summary
     */
    @GetMapping("/reports/daily")
    public ResponseEntity<Map<String, Object>> daily(@RequestParam String date) {
        return ResponseEntity.ok(auditService.dailyReport(date));
    }

    /**
     * Top users by outgoing volume.
     *
     * @return ranked users
     */
    @GetMapping("/reports/top-users")
    public ResponseEntity<List<Map<String, Object>>> topUsers() {
        return ResponseEntity.ok(auditService.topUsers());
    }
}

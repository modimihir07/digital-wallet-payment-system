package com.wallet.controller;

import com.wallet.dto.TransferRequest;
import com.wallet.dto.TransferResponse;
import com.wallet.model.Transaction;
import com.wallet.service.TransferService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * P2P transfer endpoints.
 */
@RestController
@RequestMapping("/api/transfer")
public class TransferController {

    private final TransferService transferService;

    /** Wires the service. */
    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    /**
     * Executes a transfer. Prefers the X-Idempotency-Key header; falls back
     * to the body field; generates a UUID when both are absent.
     *
     * @param req        payload
     * @param idemHeader optional header key
     * @return transfer result
     */
    @PostMapping
    public ResponseEntity<TransferResponse> transfer(
            @Valid @RequestBody TransferRequest req,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idemHeader) {
        if ((req.getIdempotencyKey() == null || req.getIdempotencyKey().isBlank()) && idemHeader != null) {
            req.setIdempotencyKey(idemHeader);
        }
        return ResponseEntity.ok(transferService.transfer(req));
    }

    /**
     * Fetches a transaction.
     *
     * @param txnId id
     * @return transaction
     */
    @GetMapping("/{txnId}")
    public ResponseEntity<Transaction> get(@PathVariable long txnId) {
        return ResponseEntity.ok(transferService.get(txnId));
    }
}

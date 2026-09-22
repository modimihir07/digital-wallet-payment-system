package com.wallet.controller;

import com.wallet.model.Bill;
import com.wallet.security.JwtAuthFilter;
import com.wallet.service.BillService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Bill endpoints.
 */
@RestController
@RequestMapping("/api/bills")
public class BillController {

    private final BillService billService;

    /** Wires the service. */
    public BillController(BillService billService) {
        this.billService = billService;
    }

    /**
     * Lists the caller's bills.
     *
     * @param req HTTP request
     * @return bills
     */
    @GetMapping("/my")
    public ResponseEntity<List<Bill>> my(HttpServletRequest req) {
        long userId = (Long) req.getAttribute(JwtAuthFilter.ATTR_USER_ID);
        return ResponseEntity.ok(billService.myBills(userId));
    }

    /**
     * Pays a bill.
     *
     * @param req        HTTP request
     * @param billId     bill
     * @param idemHeader optional idempotency key
     * @return paid txn id
     */
    @PostMapping("/{billId}/pay")
    public ResponseEntity<Map<String, Object>> pay(
            HttpServletRequest req,
            @PathVariable long billId,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idemHeader) {
        long userId = (Long) req.getAttribute(JwtAuthFilter.ATTR_USER_ID);
        long txnId = billService.payBill(userId, billId, idemHeader);
        return ResponseEntity.ok(Map.of("txnId", txnId, "status", "SUCCESS"));
    }
}

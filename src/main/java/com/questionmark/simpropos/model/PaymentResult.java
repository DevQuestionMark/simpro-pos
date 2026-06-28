package com.questionmark.simpropos.model;

import java.math.BigDecimal;

/**
 * Immutable result from the tender dialog.
 *
 * dbMethod     — value stored in incoming_payments.payment_method ('CASH' or 'TRANSFER')
 * displayMethod — label shown in the receipt ('Cash', 'QRIS', 'Transfer')
 * tendered      — amount the customer handed over (equals grandTotal for non-cash)
 * change        — tendered minus grandTotal (zero for non-cash)
 */
public record PaymentResult(
    String     dbMethod,
    String     displayMethod,
    BigDecimal tendered,
    BigDecimal change
) {}

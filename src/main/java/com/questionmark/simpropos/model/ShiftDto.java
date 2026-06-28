package com.questionmark.simpropos.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ShiftDto(
    long          id,
    int           outletId,
    int           terminalId,
    long          cashierId,
    LocalDateTime openedAt,
    BigDecimal    openingFloat,
    LocalDateTime closedAt,      // null if still open
    BigDecimal    countedCash,   // null if still open
    BigDecimal    expectedCash,  // null if still open
    BigDecimal    variance,      // null if still open
    String        status         // 'OPEN' | 'CLOSED'
) {}

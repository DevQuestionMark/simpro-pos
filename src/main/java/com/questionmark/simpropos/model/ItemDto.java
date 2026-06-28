package com.questionmark.simpropos.model;

import java.math.BigDecimal;

public record ItemDto(
    long       id,
    String     itemCode,
    String     itemName,
    String     uom,
    BigDecimal price,
    BigDecimal onHandQty,
    String     image       // nullable — path stored in item_master.image
) {}

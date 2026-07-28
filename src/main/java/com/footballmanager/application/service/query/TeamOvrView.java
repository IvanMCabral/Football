package com.footballmanager.application.service.query;

import java.math.BigDecimal;

public record TeamOvrView(
    String id,
    String name,
    String country,
    String formation,
    int ovr,
    int playerCount,
    BigDecimal budget
) {
}

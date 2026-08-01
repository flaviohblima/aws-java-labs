package br.com.flaviohblima.lab65batchmigration.model.rds;

import java.math.BigDecimal;
import java.time.Instant;

public record OrderRow(
        long id,
        String customerId,
        String status,
        BigDecimal amount,
        Instant createdAt,
        String itemsJson // json aggregate output, parsed in the processor
) {}

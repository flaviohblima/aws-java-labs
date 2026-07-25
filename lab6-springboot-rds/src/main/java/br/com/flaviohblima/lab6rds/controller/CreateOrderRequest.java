package br.com.flaviohblima.lab6rds.controller;

import java.math.BigDecimal;

public record CreateOrderRequest(String customerId, BigDecimal amount) {
}

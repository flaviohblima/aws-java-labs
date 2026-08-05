package br.com.flaviohblima.lab7fargate;

import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/orders")
public class OrderController {

    // In-memory store, on purpose — see README note on statefulness
    private final Map<String, Map<String, Object>> orders = new ConcurrentHashMap<>();

    @GetMapping
    public List<Map<String, Object>> getOrders() {
        return List.copyOf(orders.values());
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        String id = "ord-" + UUID.randomUUID().toString().substring(0, 8);
        Map<String, Object> order = Map.of(
                "orderId", id,
                "customerId", String.valueOf(body.getOrDefault("customerId", "unknown")),
                "amount", new BigDecimal(String.valueOf(body.getOrDefault("amount", "0"))),
                "status", "PENDING",
                "createdAt", Instant.now().toString());

        orders.put(id, order);
        return order;
    }

    @GetMapping("/whoami")
    public Map<String, String> whoAmI() {
        return Map.of(
                "instance", System.getenv().getOrDefault("HOSTNAME", "unknown"),
                "javaVersion", System.getProperty("java.version"),
                "appVersion", "v2"
        );
    }


}

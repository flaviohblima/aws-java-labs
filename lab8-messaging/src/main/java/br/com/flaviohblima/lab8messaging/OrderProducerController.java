package br.com.flaviohblima.lab8messaging;

import io.awspring.cloud.sns.core.SnsTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@RestController
public class OrderProducerController {

    private final SnsTemplate snsTemplate;
    private final String topicArn;

    public OrderProducerController(SnsTemplate snsTemplate,
                                   @Value("${app.topic-arn}") String topicArn) {
        this.snsTemplate = snsTemplate;
        this.topicArn = topicArn;
    }

    @PostMapping("/orders")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, String> publish(@RequestBody Map<String, String> payload) {
        OrderMessage msg = new OrderMessage(
            "ord-" + UUID.randomUUID().toString().substring(0, 8),
                payload.get("customerId"),
                new BigDecimal(payload.get("amount")),
                Instant.now()
        );

        snsTemplate.convertAndSend(topicArn, msg);
        return Map.of("orderId", msg.orderId(), "status", "ACCEPTED");
    }

}

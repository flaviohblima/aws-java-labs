package br.com.flaviohblima.lab8messaging;

import io.awspring.cloud.sqs.annotation.SqsListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class OrderListener {

    private static final Logger log = LoggerFactory.getLogger(OrderListener.class);

    @SqsListener("lab8-order-queue")
    public void handle(OrderMessage order,
                       @Header("Sqs_Msa_ApproximateReceiveCount") String receiveCount) {
        log.info("Received {} (attempt {}): customer: {}, amount: {})",
                order.orderId(), receiveCount, order.customerId(), order.amount());

        if (order.amount().compareTo(new BigDecimal(900)) > 0) {
            throw new IllegalStateException("SIMULATION: Amount must be greater than or equal to 900");
        }

        log.info("Processed {} successfully!", order.orderId());
    }

}

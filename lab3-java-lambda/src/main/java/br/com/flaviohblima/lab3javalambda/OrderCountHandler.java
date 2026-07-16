package br.com.flaviohblima.lab3javalambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public class OrderCountHandler implements RequestHandler<Map<String, String>, Map<String, Object>> {

    private static final DynamoDbClient BASE = DynamoDbClient.builder()
            .httpClient(UrlConnectionHttpClient.create())
            .build();

    private static final DynamoDbTable<Order> TABLE = DynamoDbEnhancedClient.builder()
            .dynamoDbClient(BASE).build()
            .table("Lab2Orders", TableSchema.fromBean(Order.class));

    private static final AtomicLong invocationInThisEnv = new AtomicLong();

    @Override
    public Map<String, Object> handleRequest(Map<String, String> event, Context context) {
        String customerId = event.getOrDefault("customerId", "cust-001");

        long count = TABLE.query(QueryConditional.keyEqualTo(
                Key.builder().partitionValue(customerId).build()
        )).items().stream().count();

        long n = invocationInThisEnv.incrementAndGet();
        context.getLogger().log("Invocation #%d in this environment".formatted(n));

        return Map.of(
                "customerId", customerId,
                "count", count,
                "invocationInThisEnv", n
        );
    }

}

package br.com.flaviohblima.lab2dynamodb;

import software.amazon.awssdk.enhanced.dynamodb.*;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class OrderApp {

    private static final Region REGION = Region.SA_EAST_1;

    public static void main(String[] args) {
        try (DynamoDbClient dynamoDbClient = DynamoDbClient.builder().region(REGION).build()) {
            // The enhanced client wraps the loc-level client
            DynamoDbEnhancedClient enhanced = DynamoDbEnhancedClient.builder()
                    .dynamoDbClient(dynamoDbClient).build();

            // TableShcema.fromBean reads the annotations via reflection.
            // Build it ONCE and reuse - it's the expensive part
            DynamoDbTable<Order> table = enhanced.table("Lab2Orders", TableSchema.fromBean(Order.class));

            switch (args.length > 0 ? args[0] : "demo") {
                case "seed" -> seed(table);
                case "put" -> put(table, args[1], args[2], new BigDecimal(args[3]));
                case "get" -> get(table, args[1], args[2]);
                case "pure-get" -> pureGet(dynamoDbClient, args[1], args[2]);
                case "query" -> queryByCustomer(table, args[1]);
                case "scan" -> scanByStatus(table, args[1]);
                case "update" -> updateStatus(table, args[1], args[2], args[3]);
                case "delete" -> delete(table, args[1], args[2]);
                case "put-once" -> putIfNotExists(table, args[1], args[2], new BigDecimal(args[3]));
                default -> System.err.println("""
                        Usage:
                            seed
                            put <customerId> <status> <amount>
                            get <customerId> <orderId>
                            pure-get <customerId> <orderId>
                            query <customerId>
                            scan <status>
                            update <customerId> <orderId> <newStatus>
                            delete <customerId> <orderId>
                            put-once <customerId> <orderId> <amount>
                        """);
            }
        }
    }

    private static void seed(DynamoDbTable table) {
        String[][] rows = {
                {"cust-001", "SHIPPED", "49.90"},
                {"cust-001", "PENDING", "120.00"},
                {"cust-001", "DELIVERED", "15.50"},
                {"cust-002", "PENDING", "310.75"},
                {"cust-002", "SHIPPED", "89.99"}
        };

        for (String[] row : rows) {
            put(table, row[0], row[1], new BigDecimal(row[2]));
        }
    }

    private static void put(DynamoDbTable<Order> table, String customerId, String status, BigDecimal amount) {
        Order order = new Order();
        order.setCustomerId(customerId);
        order.setOrderId("ord-" + UUID.randomUUID().toString().substring(0, 8));
        order.setAmount(amount);
        order.setStatus(status);
        order.setCreatedAt(Instant.now());

        table.putItem(order);
        System.out.println("Created: " + order);
    }

    private static void get(DynamoDbTable<Order> table, String customerId, String orderId) {
        Order order = table.getItem(Key.builder()
                .partitionValue(customerId)
                .sortValue(orderId)
                .build());
        System.out.println(order != null ? order : "Not found");
    }

    private static void pureGet(DynamoDbClient dynamoDbClient, String customerId, String orderId) {

        Map<String, AttributeValue> queryMap = new HashMap<>();
        queryMap.put("customerId", AttributeValue.builder().s(customerId).build());
        queryMap.put("orderId", AttributeValue.builder().s(orderId).build());

        GetItemRequest request = GetItemRequest.builder()
                .tableName("Lab2Orders")
                .key(queryMap)
                .build();
        GetItemResponse response = dynamoDbClient.getItem(request);
        if (!response.hasItem()) {
            System.out.println("Not found");
            return;
        }

        Order order = new Order();
        order.setOrderId(orderId);
        order.setCustomerId(customerId);
        order.setStatus(response.item().get("status").s());
        order.setAmount(new BigDecimal(response.item().get("amount").n()));
        order.setCreatedAt(Instant.parse(response.item().get("createdAt").s()));

        System.out.println(order);
    }

    private static void queryByCustomer(DynamoDbTable<Order> table, String customerId) {
        QueryConditional byCustomer = QueryConditional.keyEqualTo(
                Key.builder().partitionValue(customerId).build());

        System.out.println("Orders for customer " + byCustomer + ":");
        table.query(byCustomer).items()
                .forEach(order -> System.out.println("  " + order));
    }

    private static void scanByStatus(DynamoDbTable<Order> table, String status) {
        System.out.println("All orders with status " + status + " (SCAN - reads whole table):");
        table.scan().items()
                .stream().filter(order -> status.equals(order.getStatus()))
                .forEach(order -> System.out.println("  " + order));
    }

    private static void updateStatus(DynamoDbTable<Order> table, String customerId, String orderId, String status) {
        Order order = table.getItem(Key.builder()
                .partitionValue(customerId)
                .sortValue(orderId)
                .build());

        if (order == null) {
            System.out.println("Not found");
            return;
        }

        order.setStatus(status);
        table.updateItem(order);
        System.out.println("Updated: " + order);
    }

    private static void delete(DynamoDbTable<Order> table, String customerId, String orderId) {
        table.deleteItem(Key.builder()
                .partitionValue(customerId)
                .sortValue(orderId)
                .build());

        System.out.println("Deleted: " + orderId);
    }

    private static void putIfNotExists(DynamoDbTable<Order> table, String customerId, String orderId, BigDecimal amount) {
        Order order = new Order();
        order.setCustomerId(customerId);
        order.setOrderId(orderId);
        order.setAmount(amount);
        order.setStatus("PENDING");
        order.setCreatedAt(Instant.now());

        try {
            table.putItem(r -> r.item(order)
                    .conditionExpression(Expression.builder()
                            .expression("attribute_not_exists(orderId)")
                            .build()));
            System.out.println("Created: " + order);
        } catch (ConditionalCheckFailedException e) {
            System.out.println("Order already exists - write rejected by DynamoDB");
        }
    }

}

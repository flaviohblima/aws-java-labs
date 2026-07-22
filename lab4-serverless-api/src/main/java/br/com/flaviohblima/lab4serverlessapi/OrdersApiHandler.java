package br.com.flaviohblima.lab4serverlessapi;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class OrdersApiHandler implements RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {

    private static final DynamoDbTable<Order> ORDERS_TABLE =
            DynamoDbEnhancedClient.builder()
                    .dynamoDbClient(DynamoDbClient.builder()
                            .httpClient(UrlConnectionHttpClient.create())
                            .build())
                    .build()
                    .table("Lab2Orders", TableSchema.fromBean(Order.class));

    private static final ObjectMapper JSON = new ObjectMapper().registerModule(new JavaTimeModule());

    @Override
    public APIGatewayV2HTTPResponse handleRequest(APIGatewayV2HTTPEvent event, Context context) {
        try {
            // routeKey looks like "GET /orders/{customerId}"
            return switch (event.getRouteKey()) {
                case "GET /orders/{customerId}" -> getOrders(event.getPathParameters().get("customerId"));
                case "POST /orders" -> createOrder(event.getBody());
                default -> json(400, Map.of("error", "route not found"));
            };
        } catch (IllegalArgumentException e) {
            return json(400, Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return json(500, Map.of("error", "internal error"));
        }
    }

    private APIGatewayV2HTTPResponse getOrders(String customerId) {
        List<Order> orders = ORDERS_TABLE.query(QueryConditional.keyEqualTo(
                Key.builder().partitionValue(customerId).build()
        )).items().stream().toList();

        return json(200, orders);
    }

    private APIGatewayV2HTTPResponse createOrder(String body) throws Exception {
        if(body == null || body.isBlank())
            throw new IllegalArgumentException("Request body required");

        Order orderToCreate = JSON.readValue(body, Order.class);
        if(orderToCreate.getCustomerId() == null || orderToCreate.getAmount() == null)
            throw new IllegalArgumentException("CustomerId and amount are required");

        orderToCreate.setOrderId("ord-" + UUID.randomUUID().toString().substring(0, 8));
        orderToCreate.setStatus("PENDING");
        orderToCreate.setCreatedAt(Instant.now());

        ORDERS_TABLE.putItem(orderToCreate);
        return json(201, orderToCreate);
    }

    private APIGatewayV2HTTPResponse json(int statusCode, Object payload) {
        try {
            return APIGatewayV2HTTPResponse.builder()
                    .withStatusCode(statusCode)
                    .withHeaders(Map.of("Content-Type", "application/json"))
                    .withBody(JSON.writeValueAsString(payload))
                    .build();
        } catch (Exception e) {
            return APIGatewayV2HTTPResponse.builder()
                    .withStatusCode(500).build();
        }
    }

}

package br.com.flaviohblima;

import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.model.BatchWriteResult;
import software.amazon.awssdk.enhanced.dynamodb.model.WriteBatch;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.util.ArrayList;
import java.util.List;

public class BatchWriter {
    private BatchWriter() {
        /* This utility class should not be instantiated */
    }


    private static final int DYNAMO_BATCH_LIMIT = 25;
    private static final DynamoDbEnhancedClient enhanced = DynamoDbEnhancedClient.builder()
            .dynamoDbClient(DynamoDbClient.create())
            .build();

    public static void write(DynamoDbTable<IngestedOrder> table, List<IngestedOrder> batch) throws InterruptedException {
        List<IngestedOrder> buffer = new ArrayList<>(batch);

        for (int i = 0; i < buffer.size(); i += DYNAMO_BATCH_LIMIT) {
            List<IngestedOrder> slice = buffer.subList(i, Math.min(i + DYNAMO_BATCH_LIMIT, buffer.size()));

            writeWithRetry(table, slice);
        }
    }

    private static void writeWithRetry(DynamoDbTable<IngestedOrder> table, List<IngestedOrder> items) throws InterruptedException {
        List<IngestedOrder> pending = items;
        int attempt = 0;

        while (!pending.isEmpty()) {
            final List<IngestedOrder> batch = pending;

            BatchWriteResult result = enhanced.batchWriteItem(b -> {
                WriteBatch.Builder<IngestedOrder> writeBuilder = WriteBatch.builder(IngestedOrder.class)
                        .mappedTableResource(table);
                batch.forEach(writeBuilder::addPutItem);
                b.addWriteBatch(writeBuilder.build());
            });

            pending = result.unprocessedPutItemsForTable(table);

            if (!pending.isEmpty()) {
                if (++attempt > 8) {
                    throw new IllegalStateException(pending.size() + " items unprocess after 8 retries");
                }
                Thread.sleep((long) Math.pow(2, attempt) * 50); // exponential backoff
            }
        }
    }
}

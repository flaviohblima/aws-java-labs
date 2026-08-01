package br.com.flaviohblima.lab65batchmigration.app;

import br.com.flaviohblima.lab65batchmigration.model.dynamo.OrderDocument;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.BatchWriteResult;
import software.amazon.awssdk.enhanced.dynamodb.model.WriteBatch;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.util.ArrayList;
import java.util.List;

@Component
public class DynamoDbItemWriter implements ItemWriter<OrderDocument> {

    private static final int DYNAMO_BATCH_LIMIT = 25;
    private final DynamoDbEnhancedClient enhanced;
    private final DynamoDbTable<OrderDocument> table;

    public DynamoDbItemWriter() {
        this.enhanced = DynamoDbEnhancedClient.builder()
                .dynamoDbClient(DynamoDbClient.create())
                .build();

        this.table = enhanced.table("OrderDocuments", TableSchema.fromBean(OrderDocument.class));
    }

    @Override
    public void write(Chunk<? extends OrderDocument> chunk) throws Exception {
        List<OrderDocument> buffer = new ArrayList<>(chunk.getItems());

        for(int i = 0; i < buffer.size(); i += DYNAMO_BATCH_LIMIT) {
            List<OrderDocument> slice = buffer.subList(i, Math.min(i + DYNAMO_BATCH_LIMIT, buffer.size()));

            this.writeWithRetry(slice);
        }
    }

    private void writeWithRetry(List<OrderDocument> items) throws InterruptedException {
        List<OrderDocument> pending = items;
        int attempt = 0;

        while(!pending.isEmpty()) {
            final List<OrderDocument> batch = pending;

            BatchWriteResult result = enhanced.batchWriteItem(b -> {
                WriteBatch.Builder<OrderDocument> writeBuilder = WriteBatch.builder(OrderDocument.class)
                        .mappedTableResource(table);
                batch.forEach(writeBuilder::addPutItem);
                b.addWriteBatch(writeBuilder.build());
            });

            pending = result.unprocessedPutItemsForTable(table);

            if(!pending.isEmpty()) {
                if(++attempt > 8) {
                    throw new IllegalStateException(pending.size() + " items unprocess after 8 retries");
                }
                Thread.sleep((long) Math.pow(2, attempt) * 50); // exponential backoff
            }
        }
    }
}

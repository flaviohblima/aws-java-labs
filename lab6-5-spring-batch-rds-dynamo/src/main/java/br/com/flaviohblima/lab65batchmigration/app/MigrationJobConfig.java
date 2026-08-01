package br.com.flaviohblima.lab65batchmigration.app;

import br.com.flaviohblima.lab65batchmigration.model.dynamo.LineItem;
import br.com.flaviohblima.lab65batchmigration.model.dynamo.OrderDocument;
import br.com.flaviohblima.lab65batchmigration.model.rds.OrderRow;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.listener.ChunkListener;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.batch.infrastructure.item.database.JdbcPagingItemReader;
import org.springframework.batch.infrastructure.item.database.Order;
import org.springframework.batch.infrastructure.item.database.support.PostgresPagingQueryProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

@Configuration
public class MigrationJobConfig {

    private static final Logger log = LoggerFactory.getLogger(MigrationJobConfig.class);

    private static final int CHUNK_SIZE = 500;

    @Bean
    @StepScope
    public JdbcPagingItemReader<OrderRow> reader(
            DataSource dataSource,
            @Value("#{jobParameters['fromId']}") Long fromId,
            @Value("#{jobParameters['toId']}") Long toId) {

        PostgresPagingQueryProvider provider = new PostgresPagingQueryProvider();
        provider.setSelectClause("""
                SELECT o.id as orderid, o.customer_id, o.status, o.amount, o.created_at,
                json_agg(json_build_object(
                    'product', i.product,
                     'qty', i.qty,
                     'price', i.price
                )) as items
                """);
        provider.setFromClause("""
                FROM orders o
                    JOIN order_items i ON i.order_id = o.id
                """);
        provider.setWhereClause("WHERE o.id >= :fromId AND o.id <= :toId");
        provider.setGroupClause("GROUP BY orderid");
        // Deterministic sort key = restartable paging. Non-negotiable
        provider.setSortKeys(Map.of("orderid", Order.ASCENDING));

        JdbcPagingItemReader<OrderRow> reader = new JdbcPagingItemReader<>(dataSource, provider);
        reader.setName("orderReader");
        reader.setPageSize(CHUNK_SIZE);
        reader.setParameterValues(Map.of("fromId", fromId, "toId", toId));
        reader.setRowMapper((rs, n) -> new OrderRow(
                rs.getLong("orderid"),
                "cust-" + rs.getInt("customer_id"),
                rs.getString("status"),
                rs.getBigDecimal("amount"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getString("items")
        ));

        return reader;
    }

    @Bean
    public ItemProcessor<OrderRow, OrderDocument> processor() {
        ObjectMapper json = new ObjectMapper();

        return row -> {
            OrderDocument doc = new OrderDocument();
            doc.setCustomerId(row.customerId());
            doc.setSk("ORDER#%06d".formatted(row.id()));
            doc.setStatus(row.status());
            doc.setAmount(row.amount());
            doc.setCreatedAt(row.createdAt());
            doc.setItems(json.readValue(row.itemsJson(),
                    json.getTypeFactory().constructCollectionType(List.class, LineItem.class)));
            return doc;
        };
    }

    @Bean
    public Step migrateStep(JobRepository jobRepository,
                            JdbcPagingItemReader<OrderRow> reader,
                            ItemProcessor<OrderRow, OrderDocument> processor,
                            DynamoDbItemWriter writer) {
        return new StepBuilder("migrateOrders", jobRepository)
                .<OrderRow, OrderDocument>chunk(CHUNK_SIZE)
                .faultTolerant()
                .reader(reader)
                .processor(processor)
                .writer(writer)
                .listener(new ChunkListener() {
                    @Override
                    public void afterChunk(ChunkContext ctx) {
                        var exec = ctx.getStepContext().getStepExecution();
                        log.info("chunk commited - read={} written={}",
                                exec.getReadCount(), exec.getWriteCount());
                    }
                })
                .build();
    }

    @Bean
    public Job migrateJob(JobRepository jobRepository, Step migrateStep) {
        return new JobBuilder("ordersMigration", jobRepository)
                .start(migrateStep)
                .build();
    }

}

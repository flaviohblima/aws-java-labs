package br.com.flaviohblima;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.s3.S3Client;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class CsvIngestHandler implements RequestHandler<S3Event, Void> {

    private static final Logger log = LoggerFactory.getLogger(CsvIngestHandler.class);

    private static final S3Client S3 = S3Client.builder()
            .httpClient(UrlConnectionHttpClient.create())
            .build();

    private static final DynamoDbTable<IngestedOrder> TABLE =
            DynamoDbEnhancedClient.builder()
                    .dynamoDbClient(DynamoDbClient.builder()
                            .httpClient(UrlConnectionHttpClient.create())
                            .build())
                    .build()
                    .table("OrderDocuments", TableSchema.fromBean(IngestedOrder.class));

    @Override
    public Void handleRequest(S3Event event, Context context) {
        event.getRecords().forEach(r -> {
            String bucket = r.getS3().getBucket().getName();
            String key = URLDecoder.decode(
                    r.getS3().getObject().getKey().replace("+", " "),
                    StandardCharsets.UTF_8);

            MDC.put("requestId", context.getAwsRequestId());
            MDC.put("objectKey", key);

            try {
                processFile(bucket, key);
            } finally {
                MDC.clear();
            }
        });

        return null;
    }

    private void processFile(String bucket, String key) {
        log.info("Starting ingestion");
        long start = System.currentTimeMillis();
        int ok = 0;
        int bad = 0;

        List<IngestedOrder> buffer = new ArrayList<>(25);

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                S3.getObject(b -> b.bucket(bucket).key(key)), StandardCharsets.UTF_8))) {

            String line = reader.readLine();
            int lineNo = 1;
            while((line = reader.readLine()) != null) {
                lineNo++;
                try {
                    buffer.add(parse(line, key));
                    ok++;
                } catch (RuntimeException e) {
                    bad++;
                    log.warn("Skipping malformed row {}: {}", lineNo, e.getMessage());
                }

                if(buffer.size() == 25) {
                    BatchWriter.write(TABLE, buffer);
                    buffer.clear();
                }
            }
            if(!buffer.isEmpty()) BatchWriter.write(TABLE, buffer);

        } catch (Exception ex) {
            log.error("Ingestion failed", ex);
            Emf.flush(key, ok, bad, System.currentTimeMillis() - start, false);
            throw new IllegalStateException("Ingestion failed for " + key, ex);
        }

        long milis = System.currentTimeMillis() - start;
        log.info("Ingestion complete: {} rows ok, {} skipped, {} ms", ok, bad, milis);
        Emf.flush(key, ok, bad, milis, true);
    }

    private IngestedOrder parse(String line, String sourceKey) {
        String[] fields = line.split(",");
        if (fields.length != 3) throw new IllegalArgumentException("Expected 3 fields, got " + fields.length);

        IngestedOrder o = new IngestedOrder();
        o.setCustomerId(fields[0].trim());
        if (o.getCustomerId().isEmpty()) throw new IllegalArgumentException("Customer Id is empty");

        o.setSk("ORDER#" + sourceKey + "#" + fields[1].trim()); // Idempotency by key
        o.setAmount(new BigDecimal(fields[2].trim()));
        o.setIngestedAt(Instant.now());

        return o;
    }

}

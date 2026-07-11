package br.com.flaviohblima.awslabs;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ListBucketsResponse;

public class App {

    public static void main(String[] args) {
        // The builder pattern: every v2 client is created this way.
        // No credentials in code - the SDK walks the provider chain

        try (S3Client s3Client = S3Client.builder()
                .region(Region.SA_EAST_1)
                .build()) {

            ListBucketsResponse response = s3Client.listBuckets();

            System.out.println("Buckets in your account:");
            response.buckets().forEach(b ->
                    System.out.printf(" %s (created %s)%n", b.name(), b.creationDate()));

            if (response.buckets().isEmpty()) {
                System.out.println("  (none — that's fine, the call succeeded)");
            }
        }
    }

}

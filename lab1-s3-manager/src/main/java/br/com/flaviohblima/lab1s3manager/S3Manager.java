package br.com.flaviohblima.lab1s3manager;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.transfer.s3.S3TransferManager;
import software.amazon.awssdk.transfer.s3.model.*;
import software.amazon.awssdk.transfer.s3.progress.LoggingTransferListener;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;

public class S3Manager {

    private static final Region REGION = Region.SA_EAST_1;

    public static void main(String[] args) {
        if (args.length == 0) {
            printUsageAndExit();
        }

        try (S3Client s3Client = S3Client.builder().region(REGION).build()) {
            switch (args[0]) {
                case "list-buckets" -> listBuckets(s3Client);
                case "list" -> listObjects(s3Client, args[1], args.length > 2 ? args[2] : null);
                case "upload" -> upload(args[1], Paths.get(args[2]), args[3]);
                case "download" -> download(args[1], args[2], Paths.get(args[3]));
                case "presign" -> presign(args[1], args[2], Integer.parseInt(args[3]));
                case "delete" -> delete(s3Client, args[1], args[3]);
                default -> printUsageAndExit();
            }
        } catch (S3Exception e) {
            System.err.printf("S3 error [%d %s]: %s%n",
                    e.statusCode(),
                    e.awsErrorDetails().errorCode(),
                    e.awsErrorDetails().errorMessage());
        } catch (ArrayIndexOutOfBoundsException e) {
            printUsageAndExit();
        }
    }

    private static void printUsageAndExit() {
        System.err.println("""
                Usage:
                    list-buckets
                    list <bucket> [prefix]
                    upload <bucket> <local-file> <key>
                    download <bucket> <key> <local-file>
                    presign <bucket> <key> <minutes>
                    delete <bucket> <key>
                """);
        System.exit(2);
    }

    private static void listBuckets(S3Client s3Client) {
        s3Client.listBuckets().buckets().stream()
                .map(Bucket::name)
                .forEach(System.out::println);
    }

    private static void listObjects(S3Client s3Client, String bucket, String prefix) {
        ListObjectsV2Request request = ListObjectsV2Request.builder()
                .bucket(bucket)
                .prefix(prefix)
                .build();

        long count = 0;
        long totalBytes = 0;
        for (S3Object obj : s3Client.listObjectsV2Paginator(request).contents()) {
            System.out.printf("%10d %s%n", obj.size(), obj.key());
            count++;
            totalBytes += obj.size();
        }
        System.out.printf("-- %d objects, %d bytes total%n", count, totalBytes);
    }

    private static void upload(String bucket, Path localFile, String key) {
        try (S3TransferManager tm = S3TransferManager.create()) {
            UploadFileRequest request = UploadFileRequest.builder()
                    .putObjectRequest(b -> b.bucket(bucket).key(key))
                    .source(localFile)
                    .addTransferListener(LoggingTransferListener.create())
                    .build();

            FileUpload upload = tm.uploadFile(request);
            CompletedFileUpload result = upload.completionFuture().join();
            System.out.println("Uploaded. ETag: " + result.response().eTag());
        }
    }

    private static void download(String bucket, String key, Path targetLocalFile) {
        try (S3TransferManager tm = S3TransferManager.create()) {
            DownloadFileRequest request = DownloadFileRequest.builder()
                    .getObjectRequest(b -> b.bucket(bucket).key(key))
                    .destination(targetLocalFile)
                    .addTransferListener(LoggingTransferListener.create())
                    .build();

            FileDownload download = tm.downloadFile(request);
            download.completionFuture().join();
            System.out.println("Downloaded to " + targetLocalFile.toAbsolutePath());
        }
    }

    private static void presign(String bucket, String key, Integer minutes) {
        try (S3Presigner presigner = S3Presigner.builder().region(REGION).build()) {
            GetObjectPresignRequest request = GetObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofMinutes(minutes))
                    .getObjectRequest(b -> b.bucket(bucket).key(key))
                    .build();

            String url = presigner.presignGetObject(request).url().toString();
            System.out.println("Presigned URL: " + url);
        }
    }

    private static void delete(S3Client s3Client, String bucket, String key) {
        s3Client.deleteObject(b -> b.bucket(bucket).key(key));
        System.out.println("Deleted: " + key);
    }

}

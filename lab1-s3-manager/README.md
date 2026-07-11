# lab1-s3-manager

The goal for this lab was to perform operations from a java cli application to a s3 bucket.

## SDK

AWS offers an SDK for every action we need to do in S3. In this lab, I've used the S3Client, S3TransferManager and S3Presigner.

When listing objects, the number of results in a bucket could be too large. To avoid that, we can use the ListObjectsV2Request paginator.
The logic for pagination is abstracted inside its contents() method, so we can use it without worrying about in what page we are.

Another feature is how the upload is managed. If the file is small (under 8MB), the upload occurs directly. But, if the file size surpasses this threshold, then the upload is performed using multipart file. The Etag that is generated for the file show at the end the number of parts that was needed to perform the entire upload.

And last, but not least, the presign urls that we can generate carries the local permissions with it, and expires after the time we set up. After that time, if the user tries to access the url, it will return a 403 (forbidden).

## How to test the app
- Make sure you have `aws` cli installed in your terminal.
- The region in the app is hardcoded, so if someone tries to use it, remember to modify it or to use the same region (SA-EAST-1).
- Build the application (java-21 needed)
```bash
mvn -q package
```

- Then execute the operations
```bash
# Create a scratch bucket (name must be globally unique)
aws s3 mb s3://lab1-scratch-$RANDOM

# Set it for convenience
BUCKET=<the-name-you-got>

# Small file
echo "hello from java" > small.txt
java -jar target/lab1-s3-manager-1.0.jar upload $BUCKET small.txt test/small.txt

# Big file to trigger multipart (100 MB of zeros)
dd if=/dev/zero of=big.bin bs=1M count=100
java -jar target/lab1-s3-manager-1.0.jar upload $BUCKET big.bin test/big.bin
# Watch the LoggingTransferListener output — you'll see progress percentages

# List
java -jar target/lab1-s3-manager-1.0.jar list $BUCKET test/

# Download
java -jar target/lab1-s3-manager-1.0.jar download $BUCKET test/small.txt roundtrip.txt
cat roundtrip.txt

# Pre-signed URL, 5 minutes
java -jar target/lab1-s3-manager-1.0.jar presign $BUCKET test/small.txt 5
# Paste the URL into your browser or: curl "<url>"
# Wait 5+ minutes and curl again — you should get an ExpiredToken/AccessDenied XML error

# Prove the multipart upload really was multipart:
aws s3api head-object --bucket $BUCKET --key test/big.bin --query 'ETag'
# A multipart ETag looks like "abc123...-13" — the -N suffix is the part count.
# Single-part uploads have a plain MD5 ETag with no suffix.
```

- Don't forget to clean up all the files you've created.
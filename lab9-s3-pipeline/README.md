# Lab9 - S3 pipeline ingest CSV to DynamoDB

The goal for this lab was to process a batch of lines coming from a CSV to a DynamoDB database.

## Preamble
## Setup

### Crete the bucket for the CSV file

```shell
ACCOUNT_ID=$(aws sts get-caller-identity --query 'Account' --output text)
BUCKET=lab9-ingest-$RANDOM
aws s3 mb s3://$BUCKET
```

### Create dynamo table

```shell
aws dynamodb create-table \
  --table-name OrderDocuments \
  --attribute-definitions \
    AttributeName=customerId,AttributeType=S \
    AttributeName=sk,AttributeType=S \
  --key-schema \
    AttributeName=customerId,KeyType=HASH \
    AttributeName=sk,KeyType=RANGE \
  --billing-mode PAY_PER_REQUEST
```

### Create a Failure Queue

```shell
FAIL_Q_URL=$(aws sqs create-queue --queue-name lab9-failed-events \
  --query 'QueueUrl' --output text)
FAIL_Q_ARN=$(aws sqs get-queue-attributes --queue-url $FAIL_Q_URL \
  --attribute-names QueueArn --query 'Attributes.QueueArn' --output text)
```

### Create the Lambda function Role

```shell
aws iam create-role --role-name lab9-lambda-role \
  --assume-role-policy-document file://trust.json
  
aws iam attach-role-policy --role-name lab9-lambda-role \
  --policy-arn arn:aws:iam::aws:policy/service-role/AwsLambdaBasicExecutionRole

sed -i 's/<BUCKET>/'${BUCKET}'/g' policy.json
sed -i 's/<FAIL_Q_ARN>/'${FAIL_Q_ARN}'/g' policy.json

aws iam put-role-policy --role-name lab9-lambda-role \
  --policy-name lab9-access --policy-document file://policy.json

ROLE_ARN=$(aws iam get-role --role-name lab9-lambda-role \
  --query 'Role.Arn' --output text)
```

### Create the Java Lambda Function

First build the project to upload it:
```shell
mvn -q package
```

Then, use it as the Lambda Function:
```shell
aws lambda create-function \
  --function-name lab9-csv-ingest \
  --runtime java21 \
  --role $ROLE_ARN \
  --handler br.com.flaviohblima.CsvIngestHandler::handleRequest \
  --zip-file fileb://target/lab9-s3-pipeline-1.0.jar \
  --memory-size 512 \
  --timeout 60
```

```shell
aws lambda wait function-active --function-name lab9-csv-ingest

aws lambda put-function-event-invoke-config \
  --function-name lab9-csv-ingest \
  --maximum-retry-attempts 2 \
  --destination-config "{\"OnFailure\":{\"Destination\":\"$FAIL_Q_ARN\"}}"
```

### Add the S3 Trigger to the Lambda Function

```shell
aws lambda add-permission \
  --function-name lab9-csv-ingest \
  --statement-id s3-invoke \
  --action lambda:InvokeFunction \
  --principal s3.amazonaws.com \
  --source-arn arn:aws:s3:::${BUCKET} \
  --source-account $ACCOUNT_ID
  
FN_ARN=$(aws lambda get-function --function-name lab9-csv-ingest \
  --query 'Configuration.FunctionArn' --output text)

sed -i 's/<FN_ARN>/'${FN_ARN}'/g' notification.json

aws s3api put-bucket-notification-configuration \
  --bucket $BUCKET --notification-configuration file://notification.json
```

## Testing

To test if the simple upload of a csv file to our bucket will trigger the lambda function, execute the command:
```shell
aws s3 cp orders.csv s3://$BUCKET/incoming/orders.csv
```

15 seconds should be enough to see the result at the dynamo table:
```shell
aws dynamodb scan --table-name OrderDocuments --select COUNT --query 'Count'
```

## Check the AWS Observability

Open the console and navigate to `CloudWatch -> Logs Insights`, and open the group `aws/lambda/lab9-csv-ingest`, there should be 2 rows malformed (intentionally).

For the metrics collected from logging, check the `CloudWatch -> Metrics` and see `Lab9/Ingestion` namespace.

## Retryability

To see the Failures queue, we can remove the DynamoDB statement from the role-policy and upload a copy from csv (you can name it `orders2.csv`).

Removing the policy:
```shell
sed -i 's/<BUCKET>/'${BUCKET}'/g' error-policy.json
sed -i 's/<FAIL_Q_ARN>/'${FAIL_Q_ARN}'/g' error-policy.json

aws iam put-role-policy --role-name lab9-lambda-role \
  --policy-name lab9-access --policy-document file://error-policy.json
```

Uploading the file with another name:
```shell
aws s3 cp orders.csv s3://$BUCKET/incoming/orders2.csv
```

Then watch two retries in the logs. After that, we can read the failed invocation event in the failure queue:
```shell
aws sqs receive-message --queue-url $FAIL_Q_URL --query 'Messages[0].Body'
```

To fix that sending, you can set up the correct policy, and then just replace the file with this command:
```shell
aws s3 cp s3://$BUCKET/incoming/orders2.csv s3://$BUCKET/incoming/orders2.csv \
  --metadata-directive REPLACE --metadata replayed=true
```

## Clean Up

```shell
aws s3 rm s3://$BUCKET --recursive
aws s3 rb s3://$BUCKET

aws lambda delete-function --function-name lab9-csv-ingest

aws dynamodb delete-table --table-name OrderDocuments

aws sqs delete-queue --queue-url $FAIL_Q_URL

aws iam delete-role-policy --role-name lab9-lambda-role --policy-name lab9-access
aws iam detach-role-policy --role-name lab9-lambda-role \
  --policy-arn arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole
aws iam delete-role --role-name lab9-lambda-role

aws logs delete-log-group --log-group-name /aws/lambda/lab9-csv-ingest
```
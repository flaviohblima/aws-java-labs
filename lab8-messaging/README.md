# Lab8 - Messaging with SNS + SQS + Spring Boot

The goal for this lab is to create a common sequence of receiving a message in AWS. The entry point is an SNS topic that fan outs to an SQS queue, delivers it to an application and, if it fails, redirect the message to a DLQ (dead letter queue).

## Preamble

## Set Up

### Create first the Dead Letter Queue (DLQ)

```shell
DLQ_URL=$(aws sqs create-queue --queue-name lab8-order-dlq \
  --query 'QueueUrl' --output text)
DLQ_ARN=$(aws sqs get-queue-attributes --queue-url $DLQ_URL \
  --attribute-names QueueArn --query 'Attributes.QueueArn' --output text)
```

### Create the main Queue

To create the main queue, first replace the `DLQ_ARN` inside the file [queue-definition.json](queue-definition.json) .

```shell
sed -i 's/<DLQ_ARN>/'${DLQ_ARN}'/g' queue-definition.json
```

Now, we can use that definition file to create the queue:
```shell
QUEUE_URL=$(aws sqs create-queue --queue-name lab8-order-queue \
  --attributes file://queue-definition.json
  --query 'QueueUrl' --output text)
QUEUE_ARN=$(aws sqs get-queue-attributes --queue-url $QUEUE_URL \
  --attribute-names QueueArn --query 'Attributes.QueueArn' --output text)
```

### Create the SNS Topic

```shell
TOPIC_ARN=$(aws sns create-topic --name lab8-orders \
  -query 'TopicArn' --output text)
```

### Connect the Queue to the SNS

First we subscribe the queue to the topic, making it accepts the message in raw format (without the SNS wrapping). 
```shell
aws sns subscribe \
  --topic-arn $TOPIC_ARN \
  --protocol sqs \
  --notification-endpoint $QUEUE_ARN \
  --attributes RawMessageDelivery=true
```

Then, we add the policy to allow SNS send messages to the SQS queue.
```shell
aws sqs set-queue-attributes --queue-url $QUEUE_URL \
  --attributes "{
  \"Policy\": \"{
    \\\"Version\\\": \\\"2012-10-17\\\",
    \\\"Statement\\\": [{
      \\\"Effect\\\": \\\"Allow\\\",
      \\\"Principal\\\": { \\\"Service\\\": \\\"sns.amazonaws.com\\\" },
      \\\"Action\\\": \\\"sqs:SendMessage\\\",
      \\\"Resource\\\": \\\"$QUEUE_ARN\\\",
      \\\"Condition\\\": { \\\"ArnEquals\\\": { \\\"aws:SourceArn\\\": \\\"$TOPIC_ARN\\\" } }
    }]
  }\"
}"
```

### Testing the pipes

To check if the messages reach the queue, try to send it to the topic:
```shell
aws sns publish --topic-arn $TOPIC_ARN --message '{"ping": "pong"}'
```
And then read it from the queue:
```shell
aws sqs receive-message --queue-url $QUEUE_URL --query 'Messages[0].Body'
```

The message will reappear in the queue because reading the message does not delete it automatically here:
```shell
aws sqs purge-queue --queue-url $QUEUE_URL
```

## Testing the app

Make sure you create an `.env` file based on the file [.env.example](.env.example) and replace the `TOPIC_ARN` there.

Start the app:
```shell
mvn -q spring-boot:run
```

To check the happy path, send a message like this:
```shell
curl -s -X POST localhost:8080/orders -H "Content-type: application/json" \
  -d '{"customerId":"cust-100","amount":"12,34"}'
```

To check the DLQ path, send a message with an amount over 900 (a simulated business logic in the [OrderListener.java](src/main/java/br/com/flaviohblima/lab8messaging/OrderListener.java)):
```shell
curl -s -X POST localhost:8080/orders -H "Content-type: application/json" \
  -d '{"customerId":"cust-200","amount":"950,00"}'
```

To find that message in the DLQ, we can count and read the message:
```shell
# count
aws sqs get-queue-attributes --queue-url $DLQ_URL \
  --attribute-names ApproximateNumberOfMessages \
  --query 'Attributes.ApproximateNumberOfMessages'
  
# read the message
aws sqs receive-message --queue-url $DLQ_URL --query 'Messages[0].Body'
```

If you want to simulate a fix in the code, remove that simulated "business logic" from [OrderListener.java](src/main/java/br/com/flaviohblima/lab8messaging/OrderListener.java), restart the app, and then try to process it again with this command:
```shell
aws sqs start-message-move-task --source-arn $DLQ_ARN
```

## Clean Up

```shell
SUB_ARN=$(aws sns list-subscriptions-by-topic --topic-arn $TOPIC_ARN \
  --query 'Subscriptions[0].SubscriptionArn' --output text)

aws sns unsubscribe --subscription-arn $SUB_ARN
aws sns delete-topic --topic-arn $TOPIC_ARN

aws sqs delete-queue --queue-url $QUEUE_URL
aws sqs delete-queue --queue-url $DLQ_URL
```
# lab3-java-lambda

The goal for this lab was to compare initialization of a java application executed in a Lambda Function before and after configuring the SnapStart.

## Preamble

Aws offers a SnapStart for Lambda functions written in Java. It runs the init process in publish time and store a VM snapshot of the fully initialized environment. When we invoke the Lambda, SnapStart resumes from the snapshot in cold starts, reducing its duration.

In order to make SnapStart works, we need to publish a version for the Lambda Function, creating a snapshot for it. In that process, there is some caveats to be considered:
- Uniqueness of variables: If there is some value created/computed in initialization time, it will be equal in every run. Any UUID or Random seed will be the same in every request. If you want them to be really unique, compute them in runtime, not in initialization time.
- Stale/obsolete state: Network and cached credentials may be invalid in runtime. AWS SDK handles those reconnections gracefully, but if we open our own connections, we need to be aware of that and reconnect in every request. To deal with that, there is an OpenJDK project called CRaC (Coordinated Restore at Checkpoint) that lets us coordinate what should happen `beforeCheckpoint` (like closing any connections), and `afterRestore` (like opening connections again).

Another options to solve the long initialization problem is to use Provisioned Concurrency (PC). With PC, Aws allows us to have N environments permanently warm to receive requests. The caveat of that option is that we end up paying per-hour to have them constantly warm. So, the SnapStart is the free option to improve the cold start duration.

Next, I describe my steps in the lab. Be my guest to try it out.

## Create table

Execute the following command to create a table before running this project.
```bash
aws dynamodb create-table \
  --table-name Lab2Orders \
  --attribute-definitions \
    AttributeName=customerId,AttributeType=S \
    AttributeName=orderId,AttributeType=S \
  --key-schema \
    AttributeName=customerId,KeyType=HASH \
    AttributeName=orderId,KeyType=RANGE \
  --billing-mode PAY_PER_REQUEST
```

To check if the creation was successful, execute the following:
```bash
aws dynamodb wait table-exists --table-name Lab2Orders
```

## Create Resources

Create the role for the lambda function:
```bash
aws iam create-role \
  --role-name lab3-lambda-role \
  --assume-role-policy-document file://trust.json
```

Attach Lambda Execution policy to role we've just created:
```bash
aws iam attach-role-policy \
  --role-name lab3-lambda-role \
  --policy-arn arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole
```

Put another policy in the role, to allow the Lambda Function to perform queries just in the DynamoDB table we want:
```bash
aws iam put-role-policy \
  --role-name lab3-lambda-role \
  --policy-name ddb-query-lab2orders \
  --policy-document file://ddb-policy.json
```

## Deploy the Java code to the Lambda Function

Build the java app:
```bash
mvn -q package
```

Copy the role Amazon Resource Number (ARN):
```bash
aws iam get-role --role-name lab3-lambda-role --query 'Role.Arn' --output text
```

Deploy the Java Lambda Function:
```bash
aws lambda create-function \
  --function-name lab3-order-count \
  --runtime java21 \
  --role <REPLACE_THE_ROLE_ARN> \
  --handler br.com.flaviohblima.lab3javalambda.OrderCountHandler::handleRequest \
  --zip-file fileb://target/lab3-java-lambda-1.0.jar \
  --memory-size 512 \
  --timeout 30
```

## Invoke the Lambda Function

Invoke the Lambda Function with the following command:
```bash
aws lambda invoke \
  --function-name lab3-order-count \
  --payload '{"customerId":"cust-001"}' \
  --cli-binary-format raw-in-base64-out \
  out.json && cat out.json
```

Every invocation will produce a file `out.json` with the output from the Lambda Function.

To check the Init Duration from a cold start of the function, I've run the following command.
```bash
aws logs tail /aws/lambda/lab3-order-count --since 15m --filter-pattern REPORT
```

In my case, the results were like this:
```shell
>> REPORT RequestId: ...   Duration: 2670.27 ms  ...   Init Duration: 1499.33 ms
>> REPORT RequestId: ...   Duration: 103.68 ms   ...
>> REPORT RequestId: ...   Duration: 81.07 ms    ...
>> REPORT RequestId: ...   Duration: 92.00 ms    ...
```

That result shows the cold start time for the function, a real disadvantage for java when applied in an Aws Lambda.

## SnapStart

Here is where the java can be more competitive. Let's update the configuration:
```bash
aws lambda update-function-configuration \
  --function-name lab3-order-count \
  --snap-start ApplyOn=PublishedVersions
```

SnapStart works only for published versions. Let's publish the first one:
```bash
aws lambda publish-version --function-name lab3-order-count
```

This command returns the version number (the first one will be `1`).

To check if the Function is ready, run:
```bash
aws lambda get-function --function-name lab3-order-count:1 \
  --query 'Configuration.[State,LastUpdateStatus]'
```

When ready, we can run the invocation again, but this time with the version tag:
```bash
aws lambda invoke \
  --function-name lab3-order-count:1 \
  --payload '{"customerId":"cust-001"}' \
  --cli-binary-format raw-in-base64-out \
  out.json && cat out.json
```

Now, my results were like this:
```shell
>> INIT_REPORT     Init Duration: 5203.16 ms
>> RESTORE_REPORT  Restore Duration: 684.87 ms
>> REPORT RequestId: ...    Duration: 3614.46 ms  ... Restore Duration: 684.87 ms     Billed Restore Duration: 126 ms
>> REPORT RequestId: ...    Duration: 82.89 ms    ...
>> REPORT RequestId: ...    Duration: 10.37 ms    ...
```

The restore duration was around `670 ms`, while the init duration without SnapStart was around `1500 ms`. It is possible to see the improvement in execution time.



## CLEAN UP

To finish the lab, clean up the resources:
```bash
aws lambda delete-function --function-name lab3-order-count
aws dynamodb delete-table --table-name Lab2Orders
aws iam delete-role-policy --role-name lab3-lambda-role --policy-name ddb-query-lab2orders
aws iam detach-role-policy --role-name lab3-lambda-role \
  --policy-arn arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole
aws iam delete-role --role-name lab3-lambda-role
aws logs delete-log-group --log-group-name /aws/lambda/lab3-order-count
```
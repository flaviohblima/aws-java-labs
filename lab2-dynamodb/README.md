# lab1-s3-manager

The goal for this lab was to perform operations from a java cli application to a DynamoDB table.

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

## SDK

The SDK comes with the base DynamoDB client to perform queries (PUT/UPDATE/GET).
In this project, I used both the base and the enhanced clients. To see their differences, compare the `get` and `pureGet` methods.

It worth saying that the Order class is mutable, so the Enhanced client can introspect the bean properties.

Another good practice is found in the method `put-once`. The DynamoDB offers a way to execute an expression before putting a new object in the table. With that feature, we can deduplicate entries in a Event Driven Architecture.

## Testing the project

- Make sure you replace the REGION hardcoded in the OrderApp class.
- Also, make sure you have aws cli installed in your terminal.
- Build the project using `mvn -q package`.
- Then perform the commands below:
```bash
java -jar target/lab2-dynamodb-1.0.jar seed

# Query: efficient, partition-scoped
java -jar target/lab2-dynamodb-1.0.jar query cust-001

# Scan: full table read
java -jar target/lab2-dynamodb-1.0.jar scan PENDING

# Get one (copy an orderId from the query output)
java -jar target/lab2-dynamodb-1.0.jar get cust-001 ord-XXXXXXXX
java -jar target/lab2-dynamodb-1.0.jar pure-get cust-001 ord-XXXXXXXX

# Update it
java -jar target/lab2-dynamodb-1.0.jar update cust-001 ord-XXXXXXXX SHIPPED

# Conditional write — run twice, second must fail
java -jar target/lab2-dynamodb-1.0.jar put-once cust-003 ord-fixed-001 100.00
java -jar target/lab2-dynamodb-1.0.jar put-once cust-003 ord-fixed-001 100.00
```

Count the items in the table with the query:
```bash
aws dynamodb scan --table-name Lab2Orders --select COUNT  
```
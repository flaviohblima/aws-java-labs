# Lab 6.5 - Spring Batch Migration from RDS to DynamoDB

The goal for this lab was to execute a migration of Orders data from RDS to DynamoDB using Spring Batch.

## Preamble

Spring Batch has many applications. One of them is processing large data sets in batches from one databate to another. You can think in generating reports, sending data to a datalake, migrating data to a more suitable database for you new need in the business architecture.

In general, batch jobs have this workflow:
1. Extract data from the source database
2. Transform/process the data
3. Load the data process in the new database

Like an ETL, but with our customized code.

In this project, it was used to migrate a data set from an RDS Postgres to a DynamoDB. I will cover some of the aspects of it.

First of all, the databases differ from architecture: Postgres is relational and DynamoDB is not. So, the first thing to solve is the modeling of how the original data is going to fit in a Document based database.
For that, you need to analyze if the final Document will pass the DynamoDB limits for a single document (400KB). In our case, each `order` has its `order_items` in postgres, so we could choose between index each order with nested items or index item by item, replicating the order data between them. That decision defines our `sortKey` (sk) inside DynamoDB: If indexing orders, the sk could be like this: `ORDER#000001`; but indexing items, the sk could be like this: `ORDER#000001#ITEM#0123456`.

Another criteria you can use to define how to index your documents is the users pattern of access. If it is more frequent to get all orders for a customer with its items, that suggests you should index by order. But if users want to find all items they bought, them index by item could be better.

Going forward, now thinking about the Spring Batch state, we decided to keep it in the source database for simplicity. Know that in production it is not recommended to alter anything in the source database, since this could cause some concurrency to access/alter data there, it could alter business data that should not be altered, etc. So, keep in mind that the Spring Batch should have its own database to keep the migration progress state.

Now, when writing the data to DynamoDB, it is good to know more it. DynamoDB accepts batch writes, like 25 documents at a time (our scenario), but you need to be aware with throttling, what can cause some data to not be written. With that, we know that rewrites of the same document will certainly happen. So, from the first run, we need to prevent data duplication using idempotent writes. It means the same id, same data conversion, ensuring  two writes has the same result.

Finally, to ensure you are going to process one specific "page" of orders, you need to guarantee the same ordering for source data. If the id does not offer that, the order date or created_at could offer you that ordering. Only with that in place, you can restart some job execution knowing where it starts and end. That is particularly important when you have a fleet of batches running in parallel and need to partition the data by some key, ensuring two batches will not process the same data by mistake.

## Set Up

To set up the project, first we are going to create the RDS database and seed some fake data there. After that we are going to decide how the data should be indexed inside the DynamoDB (one order item per row or nested items).

### Create the Source Database in RDS

Given we are going to run the code locally, we need to configure the Security Group allowing our access to the RDS. Let's create it and the database:
```shell
MY_IP=$(curl -s https://checkip.amazonaws.com)
VPC_ID=$(aws ec2 describe-vpcs --filters Name=is-default,Values=true \
  --query 'Vpcs[0].VpcId' --output text)
  
SG_ID=$(aws ec2 create-security-group \
  --group-name lab65-rds-sg \
  --description "Postgres from my IP only" \
  --vpc-id $VPC_ID \
  --query 'GroupId' --output text)
  
aws ec2 authorize-security-group-ingress --group-id $SG_ID \
  --protocol tcp --port 5432 --cidr ${MY_IP}/32
  
aws rds create-db-instance \
  --db-instance-identifier lab65-source-db \
  --db-instance-class db.t4g.micro \
  --engine postgres \
  --engine-version 16.13 \
  --master-username labadmin \
  --manage-master-user-password \
  --allocated-storage 20 \
  --storage-type gp2 \
  --vpc-security-group-ids $SG_ID \
  --publicly-accessible \
  --no-multi-az \
  --backup-retention-period 0
  
aws rds wait db-instance-available --db-instance-identifier lab65-source-db

ENDPOINT=$(aws rds describe-db-instances --db-instance-identifier lab65-source-db \
  --query 'DBInstances[0].Endpoint.Address' --output text)
SECRET_ARN=$(aws rds describe-db-instances --db-instance-identifier lab65-source-db \
  --query 'DBInstances[0].MasterUserSecret.SecretArn' --output text)
  
echo $ENDPOINT
echo $SECRET_ARN
```

**ATTENTION:** Make sure to set the following two env variables to the `.env` file. See [.env.example](.env.example) file.

### Create tables and seed 50K orders with SQL

```shell
aws secretsmanager get-secret-value --secret-id $SECRET_ARN \
  --query 'SecretString' --output text

export PGPASSWORD='<password-value>'

psql -h $ENDPOINT -U labadmin -d postgres -f ./src/main/resources/db/01_create_orders_tables.sql

psql -h $ENDPOINT -U labadmin -d postgres -f ./src/main/resources/db/02_seed_orders_data.sql
```

Since we are using Spring Boot 4.0.0, we need to create the tables that Spring Batch will use, otherwise it will end up running without it, and we can lose the state of the executions.

```shell
psql -h $ENDPOINT -U labadmin -d postgres -f ./src/main/resources/db/03_spring-batch-schema-postgresql.sql
```

### Create DynamoDB table

```bash
aws dynamodb create-table \
  --table-name OrderDocuments \
  --attribute-definitions \
      AttributeName=customerId,AttributeType=S \
      AttributeName=sk,AttributeType=S \
  --key-schema \
      AttributeName=customerId,KeyType=HASH \
      AttributeName=sk,KeyType=RANGE \
  --billing-mode PAY_PER_REQUEST

aws dynamodb wait table-exists --table-name OrderDocuments
```

## Running the migration

In the [MigrationJobConfig.java](src/main/java/br/com/flaviohblima/lab65batchmigration/app/MigrationJobConfig.java), you are going to find that we expect two command line parameters: `fromId` and `toId`.
The seed file creates `50000` orders with a predictable progressive id, so we can split our executions as we like. Let's say we can't run the entire source database migration at once, for memory limitations, availability or because the migration would compete with production usage. We can limit the ammout of data we are going to process with that aproach.

You can run, for instance, in five different batches:
```shell
java -jar ./target/lab65-batch-migration-0.0.1-SNAPSHOT.jar fromId=1 toId=10000
java -jar ./target/lab65-batch-migration-0.0.1-SNAPSHOT.jar fromId=10001 toId=20000
java -jar ./target/lab65-batch-migration-0.0.1-SNAPSHOT.jar fromId=20001 toId=30000
java -jar ./target/lab65-batch-migration-0.0.1-SNAPSHOT.jar fromId=30001 toId=40000
java -jar ./target/lab65-batch-migration-0.0.1-SNAPSHOT.jar fromId=40001 toId=50000
```

Or even in just one:
```shell
java -jar ./target/lab65-batch-migration-0.0.1-SNAPSHOT.jar fromId=1 toId=50000
```

In any case, to see replayability in action, stop the batch in the middle of the execution and check out the `batch_job_execution` table:
```sql
SELECT * FROM batch_job_execution
    WHERE end_time IS NULL
        AND status = 'STARTED';
```

If you forced the execution to stop, it will not know it has failed. You are going to update that line to `'FAILED'` by yourself.
```sql
UPDATE batch_job_execution 
    SET status = 'FAILED' 
    WHERE job_execution_id = <your execution_id>;
```

In a production environment, you would not rely on manually updates. For that, you would need a centralized agent just to check the staleness of executions and set them failed.
When a batch execution is `FAILED`, the batch will retry it. There is another status possible: `ABANDONED`, that means the batch will not be retried.

After updating the status to `FAILED`, you can now re-run that batch and see it finish successfully.

## Checking results

Count the number of orders in DynamoDB with the following query. It should have 50000 results.
```shell
aws dynamodb scan --table-name Lab55Orders --select COUNT --query 'Count'
```

You can also check one specific customer orders with these queries:
```shell
# counting items
aws dynamodb query \
  --table-name Lab55Orders \
  --key-condition-expression "customerId = :c" \
  --expression-attribute-values '{":c":{"S":"cust-42"}}' \
  --query 'Count'

# getting the items
aws dynamodb query \
  --table-name Lab55Orders \
  --key-condition-expression "customerId = :c" \
  --expression-attribute-values '{":c":{"S":"cust-42"}}'
```

## Clean Up

```shell
aws rds delete-db-instance \
  --db-instance-identifier lab65-source-db \
  --skip-final-snapshot \
  --delete-automated-backups
  
aws rds wait db-instance-deleted --db-instance-identifier lab65-source-db

aws ec2 delete-security-group --group-id $SG_ID
aws dynamodb delete-table --table-name OrderDocuments
```
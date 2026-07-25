# Lab 6 - Spring Boot + RDS
In this lab, I've built a simple Spring Boot Project and connected it to an RDS database. The purpose was to compare the Dynamodb approach to a relacional database in Java. The app will be running locally, so your ip will be configured in the Security Group.

## Preamble

RDS databases can be configured to manage their own secrets, by using the Secrets Manager (SSM). The auto-managed password is configured in the database creation and, after that, you can get the password from the AWS SSM when your app need to connect to the database. The app needs to know only the secret ARN (Amazon Resource Number), that is not that sensitive as a hardcoded password would be. The ARN will be always the same, but the RDS will make sure the password rotates from time to time.

In our Spring Boot project, the pair Hikari Config + AWS SSM ensure everytime the app needs to open a connection to the database, it will get the current password from the Secrets Manager. If a connection is already opened, it will finish its work, but if the connection is being opened after password changing, the new one will be got from SSM. Therefore, your app will never be disconnected, it will not need a restart to get the new password.

Some configs in the project were made knowing this is a lab, and shouldn't be used in production. For the app, the `ddl-update=auto` in the properties file should not be used in production, once it could change the schema everytime an instance is initialized.

For the database, we have set the database `publicly-accessible` so the app could connect from the local environment (not the cloud). It would not be configured like that in real world, the database would be accessed from a private subnet only, ensuring data is not access from outside.

Another database config that would not be used in production is the `no-multi-az`. It prevents data to be replicated in different availability zones (AZ). The data replication would increase availability (if one AZ suffers an outage, the other one ensures the data is not lost) and allows you to have read-replicas, for read scaling.

The last configuration that is for lab only is the `backup-retention-period` set with 0 days. It means that our database wouldn't have any backup. In production, the RDS allows us to keep a backup from the entire database up to 35 days. After that time, the backup is deleted.

Last thing that is worth to mention is that each Spring Boot app normally use a pool of 10 database connections by default (See the Hikari default configurations). Considering that, we could have up to 8 instances from the same app connected to the RDS database, knowing it offers roughly 85 connections. The other 5 could be used by migrations or data analysis.
 It means that if we replace the app by a Lambda Function, and it is getting 500 concurrent invocations, we would try to open 500 connections to the database. In that scenario, the lambdas would not be able to connect and would start failing every request past the 85 connections. For that, the correct infrastructure would be to use an RDS Proxy, allowing several apps to connect to it sharing the same connections, improving the scalability/availability problem.

## Set Up

Make sure you are using the same region both in resources creation (aws cli configuration) and in the [Data Source Config file](src/main/java/br/com/flaviohblima/lab6rds/config/DataSourceConfig.java).

### Security Group
First we need to configure the Security Group that will allow the app running locally (from your IP) to connect to the RDS database.

```bash
REGION=$(aws configure get region)
MY_IP=$(curl -s https://checkip.amazonaws.com)

VPC_ID=$(aws ec2 describe-vpcs --filters Name=is-default,Values=true \
  --query 'Vpcs[0].VpcId' --output text)
  
SG_ID=$(aws ec2 create-security-group \
  --group-name lab6-rds-sg \
  --description "Postgres from my IP only" \
  --vpc-id $VPC_ID \
  --query 'GroupId' \
  --output text)
  
aws ec2 authorize-security-group-ingress \
  --group-id $SG_ID \
  --protocol tcp --port 5432 --cidr ${MY_IP}/32
```

### Database

Then, we can create the database:
```bash
aws rds create-db-instance \
  --db-instance-identifier lab6-orders-db \
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
```

Wait for the db creation (it can take between 5 and 10 minutes):
```bash
aws rds wait db-instance-available --db-instance-identifier lab6-orders-db
```

### Env variables
Then get the endpoint and the database secret (password) ARN:
```bash
RDS_ENDPOINT=$(aws rds describe-db-instances --db-instance-identifier lab6-orders-db \
  --query 'DBInstances[0].Endpoint.Address' --output text)

DB_SECRET_ARN=$(aws rds describe-db-instances --db-instance-identifier lab6-orders-db \
  --query 'DBInstances[0].MasterUserSecret.SecretArn' --output text)
```

If you want to see the secret json shape, you can, you are the owner:
```bash
aws secretsmanager get-secret-value --secret-id $DB_SECRET_ARN \
  --query 'SecretString' --output text
```

### Running the app

Make sure you have the `RDS_ENDPOINT` and `DB_SECRET_ARN` environment variables set up and run:
```bash
mvn spring-boot:run
```

Then curl the endpoints we've created:
```bash
curl -s -X POST localhost:8080/orders -H 'Content-Type: application/json' \
  -d '{"customerId":"cust-100","amount":"42.50"}' | jq

curl -s -X POST localhost:8080/orders -H 'Content-Type: application/json' \
  -d '{"customerId":"cust-200","amount":"99.90"}' | jq

curl -s "localhost:8080/orders?customerId=cust-100" | jq
```

## Clean Up

To clean up the resources used, execute:
```bash
aws rds delete-db-instance \
  --db-instance-identifier lab6-orders-db \
  --skip-final-snapshot \
  --delete-automated-backups

aws rds wait db-instance-deleted --db-instance-identifier lab6-orders-db

aws ec2 delete-security-group --group-id $SG_ID
```
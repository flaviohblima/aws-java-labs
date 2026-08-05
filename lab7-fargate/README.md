# Lab 7 - Fargate with two instances

The goal for this lab was to deploy two instances of this very simple Spring Boot service to AWS Fargate.

## Preamble

## Set Up

### Docker image build

### Push to Elastic Container Registry (ECR)

```shell
REGION=$(aws configure get region)
ACCOUNT_ID=$(aws sts get-caller-identity --query 'Account' --output text)
REGISTRY=${ACCOUNT_ID}.dkr.ecr.${REGION}.amazonaws.com

aws ecr create-repository --repository-name lab7-orders

aws ecr get-login-password | docker login --username AWS --password-stdin $REGISTRY

docker tag lab7-orders:v1 $REGISTRY/lab7-orders:v1
docker push $REGISTRY/lab7-orders:v1
```

### Create IAM task execution role

```shell
aws iam create-role --role-name lab7-task-execution-role \
   --assume-role-policy-document file://ecs-trust.json

aws iam attatch-role-policy --role-name lab7-task-execution-role \
   --policy-arn arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy
```

### Create the Cluster, the log group and task definition

```shell
aws ecs create-cluster --cluster-name lab7-cluster
aws logs create-log-group --log-group-name /ecs/lab7-orders
```

Replace the placeholders for `ACCOUNT_ID`, `REGISTRY` and `REGION` in the file [taskdef.json](taskdef.json).

```shell
sed -i 's/<ACCOUNT_ID>/'${ACCOUNT_ID}'/g' taskdef.json
sed -i 's/<REGISTRY>/'${REGISTRY}'/g' taskdef.json
sed -i 's/<REGION>/'${REGION}'/g' taskdef.json
```

Then execute:
```shell
aws ecs register-task-definition --cli-input-json file://taskdef.json
```

### Networking: SGs, ALB, target group

### Create the Service

## Clean Up

```shell
aws ecs update-service --cluster lab7-cluster --service lab7-orders-svc --desired-count 0
aws ecs delete-service --cluster lab7-cluster --service lab7-orders-svc --force
aws ecs delete-cluster --cluster labb7-cluster

aws elbv2 delete-listener --listener-arn $(aws elbv2 describe-listeners \
  --load-balancer-arn $ALB_ARN --query 'Listeners[0].ListenerArn' --output text)
aws elbv2 delete-load-balancer --load-balancer-arn $ALB_ARN
aws elbv2 delete-target-group --target-group-arn $TG_aRN

aws ec2 delete-security-group --group-id $TASK_SG
aws ec2 delete-security-group --group-id $ALB_SG

aws ecr delete-repository --repository-name lab7-orders --force
aws logs delete-log-group --log-group-name /ecs/lab7-orders
aws iam detach-role-policy --role-name lab7-task-execution-role \
    --policy-arn arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy
aws iam delete-role --role-name lab7-task-execution-role
```
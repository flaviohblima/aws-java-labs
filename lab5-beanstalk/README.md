# lab5-beanstalk

In this lab, I've built a spring boot project and deployed it using Aws Beanstalk.
You will find an in memory state, representing a cache of data that is not present between replicas of the same service.

## Preamble

The Elastic Beanstalk (EB), is nothing more than a facilitator of deployment, i.e., it helps you to create the infrastructure needed for your app deployment. Under the hood, it is simply a preconfigured Cloud Formation, that creates the needed resources in the cloud in order to get you app running quickly.

If you run this project, you are going to see that the EB creates an EC2 instance (t3.micro in free tier), the EC2 Security Group (SG) and the Auto Scaling Group (ASG) needed for the app. Even though I used a single instance, the ASG is created with a min/max of one instance. The ASG need a health endpoint from the app to say if the instance is healthy and running. If not, it can have a configuration to stop the current instance (unhealthy), and then start another one.

When the EC2 instance is up, it has a Nginx and your app running inside. The configuration for the Nginx is just a proxy from port 80 to port 5000, so make sure your app is running on 5000.

The choice of leaving data in memory had two intentions: make a small and quick app and also to bring attention to in memory caches when scaling your app horizontally. If one app has a cache, another instance of it will not have the same data in memory. That is exactly when an external cache (like ElastiCache) or even a Database is needed, to guarantee that the state will be accessed by different replicas of the same service.

Finally, it is worth saying that the Elastic Beanstalk is very handful to deploy a single app infrastructure, but not when you have more than one microservice in you architecture. Also, Beanstalk will create an opinionated infrastructure in AWS, if you want to change that, you may face tough configurations using `.ebextensions`. And last, but not least, you will not have that much freedom and control like you can have building your own CI/CD pipeline.

## Set Up

Let's begin creating the EC2 instance profile for EB (Elastic Beanstalk).

### Instance Profile
For that, first we create a role: 
```bash
aws iam create-role \
  --role-name lab5-eb-ec2-role \
  --assume-role-policy-document file://eb-trust.json
```

Then, we attach a policy to that role:
```bash
aws iam attach-role-policy \
  --role-name lab5-eb-ec2-role \
  --policy-arn arn:aws:iam::aws:policy/AWSElasticBeanstalkWebTier
```

Then we create the profile and add the role we've just created to it:
```bash
aws iam create-instance-profile \
  --instance-profile-name lab5-eb-ec2-profile

aws iam add-role-to-instance-profile \
  --instance-profile-name lab5-eb-ec2-profile \
  --role-name lab5-eb-ec2-role
```

### Deploy

Now we can deploy the app jar to an S3 bucket that will be used by Elastic Beanstalk.

Set up environment variables
```bash
REGION=$(aws configure get region)
ACCOUNT_ID=$(aws sts get-caller-identity --query 'Account' --output text)
```

Create the EB application:
```bash
aws elasticbeanstalk create-application \
  --application-name lab5-orders
```

Then get/create the default bucket EB will use to manage the app versions:
```bash
EB_BUCKET=$(aws elasticbeanstalk create-storage-location \
  --query 'S3Bucket' --output text)
```

Then we can move our built app to this S3 bucket with the command:
```bash
aws s3 cp target/lab5-beanstalk-0.0.1-SNAPSHOT.jar \
  s3://$EB_BUCKET/lab5/app-v1.jar
```

Create one application version pointing to the jar we've deployed:
```bash
aws elasticbeanstalk create-application-version \
  --application-name lab5-orders \
  --version-label v1 \
  --source-bundle S3Bucket=$EB_BUCKET,S3Key=lab5/app-v1.jar
```

Now, we set up the environment where our Spring Boot App will run:
```bash
PLATFORM=$(aws elasticbeanstalk list-available-solution-stacks \
  --query "SolutionStacks[?contains(@, 'running Corretto 21')] | [0]" --output text)
  
aws elasticbeanstalk create-environment \
  --application-name lab5-orders \
  --environment-name lab5-orders-env \
  --version-label v1 \
  --solution-stack-name "$PLATFORM" \
  --option-settings \
    Namespace=aws:autoscaling:launchconfiguration,OptionName=IamInstancePRofile,Value=lab5-eb-ec2-profile \
    Namespace=aws:autoscaling:launchconfiguration,OptionName=InstanceType,Value=t3.micro \
    Namespace=aws:elasticbeanstalk:environment,OptionName=EnvironmentType,Value=SingleInstance \
    Namespace=aws:elasticbeanstalk:environment:process:default,OptionName=HealthCheckPath,Value=/actuator/health
```

To check if the stack is up and running, execute the following command waiting for `Ready` + `Green`. It can like about 5 minutes:
```bash
aws elasticbeanstalk describe-environments \
  --environment-names lab5-orders-env \
  --query 'Environments[0].[Status,Health,CNAME]' --output table
```

If you want, you can check the stack resources Beanstalk created using this two commands:
```bash
STACKNAME=$(aws cloudformation list-stacks \
  --query "StackSummaries[?contains(StackName,'awseb') && StackStatus=='CREATE_COMPLETE'].StackName" --output text)
  
aws cloudformation describe-stack-resources \
  --stack-name $STACKNAME \
  --query 'StackResources[].[ResourceType,LogicalResourceId]' --output table
```

## Test the app

Get your app url querying the environment CNAME:
```bash
CNAME=$(aws elasticbeanstalk describe-environments \
  --environment-names lab5-orders-env \
  --query 'Environments[0].CNAME' --output text)
```

Then, you are able to perform the requests for the app.
```bash
curl -s http://$CNAME/actuator/health | jq

curl -s -X POST http://$CNAME/orders -H 'Content-Type: application/json' \
  -d '{"customerId":"cust-200","amount":150.00}' | jq
  
curl -s http://$CNAME/orders | jq

curl -s http://$CNAME/orders/whoami | jq
```

## Clean Up AWS Resources

```bash
aws elasticbeanstalk terminate-environment --environment-name lab5-orders-env
# wait until it is terminated
aws elasticbeanstalk describe-environments --environment-names lab5-orders-env \
  --query 'Environments[0].Status'

aws elasticbeanstalk delete-application \
  --application-name lab5-orders \
  --terminate-env-by-force
  
aws s3 rm s3://$EB_BUCKET/lab5 --recursive

aws iam remove-role-from-instance-profile \
  --instance-profile-name lab5-eb-ec2-profile \
  --role-name lab5-eb-ec2-role
  
aws iam delete-instance-profile --instance-profile-name lab5-eb-ec2-profile

aws iam detach-role-policy --role-name lab5-eb-ec2-role \
  --policy-arn arn:aws:iam::aws:policy/AWSElasticBeanstalkWebTier
  
aws iam delete-role --role-name lab5-eb-ec2-role
```
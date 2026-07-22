# lab4-serverless-api

The goal for this lab was to create a java rest api with an Aws Lambda Function and an HTTP API Gateway.

## Preamble

In this lab, I've created a Lambda Function using java 21, using the API Gateway V2 library to read the incoming HTTP Event and return an HTTP Response.

In this lab we are using the HTTP API instead of using and REST API for the gateway. The HTTP API can be 70% cheaper, delivering a lower latency and simpler deployment.
While the REST API offers a lot of functionalities, it also adds more processing, what can take a little longer than the HTTP API. Also, the REST API offers canary deployments (dev, stage, prod1, prod2), while an HTTP API offers only one deployment with the default route (`/`).

The REST API also offers: Request/Response transformations; WAF can be attached directly to it (the HTTP API does not allow it, you need to attach to a CloudFront in front); Edge-optimized endpoints (HTTP API is regional only); Private endpoints (inside VPC via interface endpoints); Request validation and caching.

In this lab, we also covered the alias-based deployment for the Lambda Function. We've created an alias called `prod` pointing to the latest published version of the function.

Knowing that, with Snap Start, we need to ensure that every execution is unique, we create different UUID in every POST orders request.

Finally, it is worth mentioning that the `APIGatewayV2HTTPEvent` delivers an RouteKey as a string in the format: `"GET /orders/{customerId}"`. It allows us to use the same Lambda to execute more than one type of request, i.e., more than one endpoint per lambda. When doing so, we avoid to create one function per endpoint, what would multiply the number of roles configured, permissions and routing from the gateway.

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
  
aws dynamodb wait table-exists --table-name Lab2Orders
```

## Create Resources

Create the role for the lambda function:
```bash
aws iam create-role \
  --role-name lab4-lambda-role \
  --assume-role-policy-document file://trust.json
```

Attach Lambda Execution policy to role we've just created:
```bash
aws iam attach-role-policy \
  --role-name lab4-lambda-role \
  --policy-arn arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole
```

Put another policy in the role, to allow the Lambda Function to perform queries AND put items in the DynamoDB table we want:
```bash
aws iam put-role-policy \
  --role-name lab4-lambda-role \
  --policy-name ddb-lab2orders \
  --policy-document file://ddb-policy.json
```

## Deploy the Java code to the Lambda Function

Build the java app:
```bash
mvn -q package
```

Copy the role Amazon Resource Number (ARN) and use it to deploy the Java Lambda Function:
```bash
ROLE_ARN=$(aws iam get-role --role-name lab4-lambda-role --query 'Role.Arn' --output text)

aws lambda create-function \
  --function-name lab4-orders-api \
  --runtime java21 \
  --role $ROLE_ARN \
  --handler br.com.flaviohblima.lab4serverlessapi.OrdersApiHandler::handleRequest \
  --zip-file fileb://target/lab4-serverless-api-1.0.jar \
  --memory-size 512 \
  --timeout 30 \
  --snap-start ApplyOn=PublishedVersions
```

Publish the Lambda Function version and create an alias:
```bash
VERSION=$(aws lambda publish-version --function-name lab4-orders-api --query 'Version' --output text)

aws lambda create-alias --function-name lab4-orders-api \
  --name prod --function-version $VERSION
```

## Create the ApiGateway

Get the lambda ARN and use it to create the api already targeting it:
```bash
LAMBDA_ARN=$(aws lambda get-function --function-name lab4-orders-api \
  --query 'Configuration.FunctionArn' --output text)

API_ID=$(aws apigatewayv2 create-api \
  --name lab4-orders-api \
  --protocol-type HTTP \
  --target "${LAMBDA_ARN}:prod" \
  --query 'ApiId' --output text)
  
echo $API_ID
```

Then, get the integration id and lets create the api routes:
```bash
INTEGRATION_ID=$(aws apigatewayv2 get-integrations --api-id $API_ID \
  --query 'Items[0].IntegrationId' --output text)
  
aws apigatewayv2 create-route --api-id $API_ID \
  --route-key 'GET /orders/{customerId}' \
  --target integrations/$INTEGRATION_ID

aws apigatewayv2 create-route --api-id $API_ID \
  --route-key 'POST /orders' \
  --target integrations/$INTEGRATION_ID
```

The api quick-create (`create-api` command) created a catch-all route. We need to delete it so unmatched paths respond with 404 in the gateway.
```bash
DEFAULT_ROUTE=$(aws apigatewayv2 get-routes --api-id $API_ID \
  --query "Items[?RouteKey=='\$default'].RouteId" --output text)

aws apigatewayv2 delete-route --api-id $API_ID --route-id $DEFAULT_ROUTE
```

Finally, we need to give the API permission to invoke the lambda prod alias:
```bash
ACCOUNT_ID=$(aws sts get-caller-identity --query 'Account' --output text)
REGION=$(aws configure get region)

aws lambda add-permission \
  --function-name lab4-orders-api:prod \
  --statement-id apigw-invoke \
  --action lambda:InvokeFunction \
  --principal apigateway.amazonaws.com \
  --source-arn "arn:aws:execute-api:${REGION}:${ACCOUNT_ID}:${API_ID}/*"
```

## Testing the api

To perform requests to the api, use the following as base url:
```bash
URL="https://${API_ID}.execute-api.${REGION}.amazonaws.com"
```

With it, curl creating and listing orders from the database:
```bash
# Create orders
curl -s -X POST $URL/orders \
  -H 'Content-Type: application/json' \
  -d '{"customerId":"cust-100","amount":42.50}' | jq

curl -s -X POST $URL/orders \
  -H 'Content-Type: application/json' \
  -d '{"customerId":"cust-100","amount":99.90}' | jq
  
# List them
curl -s $URL/orders/cust-100 | jq

# Validation error → 400
curl -s -X POST $URL/orders -H 'Content-Type: application/json' -d '{}' | jq

# Unknown route → 404 from the gateway (never reaches Lambda)
curl -s $URL/nope | jq
```

## Update function code

In order to update the Lambda Function, we need to rebuild it with `mvn package` and then update the code with the command below:
```bash
aws lambda update-function-code \
  --function-name lab4-orders-api \
  --zip-file fileb://target/lab4-serverless-api-1.0.jar
```

Then, publish the new version and update the `prod` alias:
```bash
VERSION=$(aws lambda publish-version --function-name lab4-orders-api --query 'Version' --output text)

aws lambda update-alias --function-name lab4-orders-api \
  --name prod --function-version $VERSION
```

## CLEAN UP

To finish the lab, clean up the resources:
```bash
aws apigatewayv2 delete-api --api-id $API_ID
aws lambda delete-function --function-name lab4-orders-api
aws dynamodb delete-table --table-name Lab2Orders
aws iam delete-role-policy --role-name lab4-lambda-role --policy-name ddb-lab2orders
aws iam detach-role-policy --role-name lab4-lambda-role \
  --policy-arn arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole
aws iam delete-role --role-name lab4-lambda-role
aws logs delete-log-group --log-group-name /aws/lambda/lab4-orders-api
```
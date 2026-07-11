# Aws Tool Chain

- The purpose of this lab was just to verify the credentials chain and the aws sdk v2 behavior.
- The project is a simple java cli runner that lists the buckets from s3.

- The benefits from the sdk credentials chain is that the code does not need any configuration to wire up the code to the aws account. Once the terminal is logged in, our app uses the terminal credentials to log in the aws account.
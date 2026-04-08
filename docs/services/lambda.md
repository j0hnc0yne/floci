# Lambda

**Protocol:** REST JSON
**Endpoint:** `http://localhost:4566/2015-03-31/functions/...`

Lambda runs your function code inside real Docker containers — the same way real AWS Lambda does.

## Supported Operations

| Operation | Description |
|---|---|
| `CreateFunction` | Deploy a Lambda function |
| `GetFunction` | Get function details and download URL |
| `GetFunctionConfiguration` | Get runtime configuration |
| `ListFunctions` | List all functions |
| `UpdateFunctionCode` | Upload new code |
| `DeleteFunction` | Remove a function |
| `Invoke` | Invoke a function synchronously or asynchronously |
| `CreateEventSourceMapping` | Connect SQS / Kinesis / DynamoDB Streams to a function |
| `GetEventSourceMapping` | Get event source mapping details |
| `ListEventSourceMappings` | List all event source mappings |
| `UpdateEventSourceMapping` | Update a mapping |
| `DeleteEventSourceMapping` | Remove a mapping |
| `PublishVersion` | Publish an immutable version |
| `CreateAlias` | Create a named alias pointing to a version |
| `GetAlias` | Get alias details |
| `ListAliases` | List all aliases for a function |
| `UpdateAlias` | Update an alias |
| `DeleteAlias` | Delete an alias |

## Configuration

```yaml
floci:
  services:
    lambda:
      enabled: true
      ephemeral: false                     # Remove container after each invocation
      default-memory-mb: 128
      default-timeout-seconds: 3
      docker-host: unix:///var/run/docker.sock
      runtime-api-base-port: 9200
      runtime-api-max-port: 9299
      code-path: ./data/lambda-code        # ZIP storage location
      poll-interval-ms: 1000
      container-idle-timeout-seconds: 300  # Idle container cleanup
      registry:                            # Optional: for private Docker registries
        url: custom.registry.io            # Registry URL (defaults to Docker Hub)
        username: myuser                   # Registry username
        password: mytoken                  # Registry password or token
        email: user@example.com            # Optional email
```

### Docker registry credentials

When pulling container images from private registries, Floci supports credentials from multiple sources (priority order):

1. **Environment variables** (highest priority):
   - `FLOCI_SERVICES_LAMBDA_REGISTRY_URL`
   - `FLOCI_SERVICES_LAMBDA_REGISTRY_USERNAME`
   - `FLOCI_SERVICES_LAMBDA_REGISTRY_PASSWORD`
   - `FLOCI_SERVICES_LAMBDA_REGISTRY_EMAIL`

2. **Docker config.json** (middle priority):
   - Reads `~/.docker/config.json` automatically
   - Uses the first registry with auth credentials

3. **YAML config** (lowest priority):
   - Configured under `floci.services.lambda.registry`

If no credentials are provided, Floci defaults to Docker Hub.

### Docker socket requirement

Lambda requires the Docker socket. Mount it in your compose file:

```yaml
services:
  floci:
    volumes:
      - /var/run/docker.sock:/var/run/docker.sock
```

## Examples

```bash
export AWS_ENDPOINT=http://localhost:4566

# Package a simple Node.js function
cat > index.mjs << 'EOF'
export const handler = async (event) => {
  console.log("Event:", JSON.stringify(event));
  return { statusCode: 200, body: JSON.stringify({ hello: "world" }) };
};
EOF
zip function.zip index.mjs

# Deploy the function
aws lambda create-function \
  --function-name my-function \
  --runtime nodejs22.x \
  --role arn:aws:iam::000000000000:role/lambda-role \
  --handler index.handler \
  --zip-file fileb://function.zip \
  --endpoint-url $AWS_ENDPOINT

# Invoke synchronously
aws lambda invoke \
  --function-name my-function \
  --payload '{"key":"value"}' \
  --cli-binary-format raw-in-base64-out \
  response.json \
  --endpoint-url $AWS_ENDPOINT

cat response.json

# Invoke asynchronously
aws lambda invoke \
  --function-name my-function \
  --invocation-type Event \
  --payload '{"key":"value"}' \
  --cli-binary-format raw-in-base64-out \
  /dev/null \
  --endpoint-url $AWS_ENDPOINT

# Update code
zip function.zip index.mjs
aws lambda update-function-code \
  --function-name my-function \
  --zip-file fileb://function.zip \
  --endpoint-url $AWS_ENDPOINT
```

## Event Source Mappings

Connect Lambda to SQS, Kinesis, or DynamoDB Streams:

```bash
# SQS trigger
QUEUE_ARN=$(aws sqs get-queue-attributes \
  --queue-url $AWS_ENDPOINT/000000000000/orders \
  --attribute-names QueueArn \
  --query Attributes.QueueArn --output text \
  --endpoint-url $AWS_ENDPOINT)

aws lambda create-event-source-mapping \
  --function-name my-function \
  --event-source-arn $QUEUE_ARN \
  --batch-size 10 \
  --endpoint-url $AWS_ENDPOINT
```

## Supported Runtimes

Any runtime that has an official AWS Lambda container image works with Floci (e.g. `nodejs22.x`, `python3.13`, `java21`, `go1.x`, `provided.al2023`).
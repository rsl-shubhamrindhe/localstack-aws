# Official AWS Emulator vs. LocalStack

Comparison as of November 26, 2026. The LocalStack columns cover the current Hobby, Base, Ultimate, and Enterprise plans.

## Pros and cons

| | Official AWS Emulator | LocalStack Free (Hobby) | LocalStack Paid (Base, Ultimate, Enterprise) |
|---|---|---|---|
| **Pros** | No account, activation token, or emulator fee. Separate instances give tests isolated state. AWS publishes fidelity results against real AWS for each release. | Free for personal, non-commercial use. Includes S3, SQS, SNS, DynamoDB, Lambda, Step Functions, EventBridge, and API Gateway REST APIs. Prebuilt Docker image and a browser UI for managing S3 objects. | Commercial use, local state persistence, IAM policy enforcement, and more services. **Base** adds ECS, ECR, RDS, Cognito, and API Gateway HTTP/WebSocket APIs. **Ultimate and Enterprise** add EKS, Bedrock, Glue, Athena, and more. |
| **Cons** | State is in memory and disappears on stop. On macOS you build the Docker image and configure trust for its HTTPS certificate. IAM policy evaluation and cross-account behavior are limited. Unimplemented operations return HTTP `501`. | Requires a LocalStack account and auth token. No local state persistence or IAM policy enforcement. ECS, ECR, RDS, EKS, and API Gateway HTTP/WebSocket APIs are outside Hobby. | Requires activation. Base and Ultimate are listed at **$39** and **$89 USD per license per month**, respectively, billed annually. Base leaves out services such as EKS, Bedrock, and Athena. Specific API operations and integrations can differ from real AWS. |

[AWS Emulator overview and limits](https://docs.aws.amazon.com/emulator/latest/userguide/what-is-service.html) · [AWS Docker setup](https://docs.aws.amazon.com/emulator/latest/userguide/getting-started-docker.html) · [LocalStack plan entitlements](https://docs.localstack.cloud/aws/licensing/) · [LocalStack pricing](https://www.localstack.cloud/pricing-comparison) · [LocalStack S3 browser](https://docs.localstack.cloud/aws/services/s3/#resource-browser)

## Service coverage

AWS generates emulator endpoints from its published API models and reports which fidelity tests pass. LocalStack publishes which services each plan includes, with API coverage documented per service. The AWS fractions below are **passing test units / total reported units, including skipped units**, for release `v1.0.624`. They measure tested behavior. The release data contains **356 distinct service model IDs**, **349** with at least one passing unit. [AWS fidelity methodology](https://docs.aws.amazon.com/emulator/latest/userguide/fidelity.html) · [Full AWS release results](https://github.com/aws/aws-emulator/releases/tag/v1.0.624) · [LocalStack service matrix](https://docs.localstack.cloud/aws/licensing/#service-coverage-clarification)

| Area | Official AWS Emulator — measured examples | LocalStack Free — included services | LocalStack Paid — additions |
|---|---|---|---|
| **Storage** | S3 **2,630 / 2,988** | S3, S3 Control | **Ultimate/Enterprise:** S3 Glacier, S3 Files, EFS, AWS Backup |
| **Queues, events, and workflows** | SQS **1,426 / 1,604**, SNS **618 / 676**, EventBridge **924 / 1,006**, Scheduler **194 / 204**, Pipes **179 / 190**, Step Functions **957 / 1,035** | SQS, SNS, SWF, Step Functions, EventBridge, EventBridge Scheduler | **Base+:** Amazon MQ. **Ultimate/Enterprise:** EventBridge Pipes, EventBridge custom event buses, Managed Workflows for Apache Airflow |
| **Compute and containers** | Lambda **2,658 / 3,112**, EC2 **1,829 / 2,639**, ECS **713 / 1,012**, ECR **397 / 511**, EKS **344 / 422** | EC2, Lambda, Lambda Web Functions | **Base+:** ECS, ECR, Lambda MicroVMs. **Ultimate/Enterprise:** EKS, Batch, Elastic Beanstalk, Serverless Application Repository |
| **Databases** | DynamoDB **2,518 / 2,778**, DynamoDB Streams **44 / 46**, RDS **622 / 815**, ElastiCache **187 / 229** | DynamoDB, DynamoDB Streams | **Base+:** RDS, RDS Data API, ElastiCache. **Ultimate/Enterprise:** DocumentDB, MemoryDB, Neptune, Timestream |
| **API, DNS, and delivery** | API Gateway REST control service **1,199 / 1,283**, Route 53 **183 / 221**, CloudFront **778 / 904**, Classic Load Balancing **16 / 30** | API Gateway REST API, Route 53, Route 53 Resolver | **Base+:** API Gateway HTTP/WebSocket and Management APIs, Classic Load Balancing, Elastic Load Balancing v2, CloudFront. **Ultimate/Enterprise:** CloudFront KeyValueStore, Cloud Map |
| **Identity, secrets, and encryption** | IAM **1,156 / 1,377**, KMS **525 / 571**, Secrets Manager **442 / 476**, STS **244 / 294** | IAM, KMS, Secrets Manager, STS, Certificate Manager | **Base+:** Cognito Identity Pools and User Pools. **Ultimate/Enterprise:** Verified Permissions, Private CA, WAF, IAM Identity Store, IAM Identity Center, Resource Access Manager, Shield |
| **Infrastructure and observability** | CloudFormation **1,411 / 4,546**, CloudWatch Metrics **1,284 / 1,406**, Systems Manager **636 / 819** | CloudFormation, Cloud Control, CloudWatch Metrics and Logs, Resource Groups, Systems Manager Parameter Store, AWS Config, AWS Support API | **Base+:** Application Auto Scaling, EC2 Auto Scaling, AppConfig. **Ultimate/Enterprise:** CloudTrail, Account Management, Organizations |
| **Analytics and streaming** | Kinesis **396 / 434**, Firehose **322 / 336**, OpenSearch **376 / 463**, Athena **436 / 485**, Glue **796 / 948**, MSK **227 / 289** | Elasticsearch, OpenSearch, Kinesis Data Streams, Firehose, Redshift | **Ultimate/Enterprise:** Athena, EMR, EMR Serverless, Glue, Redshift Data API, Lake Formation, MSK, Managed Service for Apache Flink |
| **Developer and frontend tools** | See per-service results in the AWS release report | — | **Base+:** CodeCommit, CodeArtifact, CodeBuild, CodeConnections. **Ultimate/Enterprise:** Fault Injection Service, CodeDeploy, CodePipeline, X-Ray, Amplify, AppSync |
| **AI and machine learning** | Bedrock control plane **196 / 238** | Transcribe | **Ultimate/Enterprise:** Textract, SageMaker AI, SageMaker Runtime, Bedrock, Bedrock Runtime |
| **Other services** | See per-service results in the AWS release report | SES | **Base+:** SES v2, AWS IoT. **Ultimate/Enterprise:** Managed Blockchain, Cost Explorer, IoT Wireless, IoT Data, MediaConvert, Transfer Family, Database Migration Service |

**Base+** means Base, Ultimate, and Enterprise. The paid column lists additions to Hobby. Related APIs such as S3 and S3 Control remain separate entries where the plan matrix lists them separately. [LocalStack service matrix](https://docs.localstack.cloud/aws/licensing/#service-coverage-clarification)

## What this means for this Spring Boot project

- **S3:** Bucket creation, object upload, listing, and retrieval were verified against the official AWS Emulator. The app’s LocalStack-style HTTP connection was verified against a test fixture. Live LocalStack S3 verification requires an activation token.
- **ECS, ECR, and RDS:** All three appear in the AWS Emulator fidelity report. LocalStack includes them from the Base tier. Creating an ECS service or RDS instance through an API exercises resource management. Running a task or connecting to a database needs its own test. [AWS fidelity methodology](https://docs.aws.amazon.com/emulator/latest/userguide/fidelity.html) · [LocalStack service matrix](https://docs.localstack.cloud/aws/licensing/#service-coverage-clarification)

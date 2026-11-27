# Spring Boot S3 demo

The app uses Amazon S3 by default. Provision a bucket in your AWS account and configure the app with `S3_BUCKET` and the bucket’s `AWS_REGION`. The AWS SDK uses its default credential provider chain. For ECS, give the application’s **task role** `s3:ListBucket` on the bucket ARN and `s3:GetObject` and `s3:PutObject` on the object ARNs. The app expects the bucket to exist. [AWS SDK credential chain](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/credentials-chain.html)

Build the executable JAR with `./mvnw clean package`. Start it on your AWS compute service with `java -jar target/aws.localstack.demo-0.0.1-SNAPSHOT.jar`. It listens on port `8080` by default. Set `SERVER_PORT` to override it. Restrict access to the demo routes at the hosting layer because they expose S3 reads and writes without application-level sign-in.

## Local S3 testing

In IntelliJ, select **AWS**, **Official AWS Emulator**, or **LocalStack** from the run configuration dropdown. All three launch `Application.java`. Set `AWS_REGION` and `S3_BUCKET` in the **AWS** configuration's environment. The emulator configurations include the test classpath and select the corresponding Spring profiles. Run one configuration at a time so the playground stays on port `8080`.

The same `local` Spring profile works with either backend. The test-only adapter and its settings live under `src/test/` and stay outside the deployable JAR.

| Backend | Start the backend | Start Spring Boot |
| --- | --- | --- |
| [Official AWS Emulator](aws-emu/README.md) | `./aws-emu/start.sh` | `./run-local.sh aws-emu` |
| [LocalStack](localstack/README.md) | `./localstack/start.sh` | `./run-local.sh localstack` |

Run the Spring Boot command in a terminal and open [the S3 playground](http://localhost:8080/s3-demo.html). To switch backends, stop Spring Boot and rerun it with the other argument. The two emulators can run side by side on different ports. Each keeps its own bucket and objects, and the playground creates the selected backend's bucket on the first save.

### Keep S3 data across restarts

Use each emulator's `start.sh` and `stop.sh` scripts. They use Python 3.8+ and the AWS CLI Docker image to restore the last saved S3 snapshot on start and save a fresh snapshot before stopping. Docker stores `snapshot.zip` in a separate named volume for each backend.

| Backend | Docker volume |
| --- | --- |
| Official AWS Emulator | `localstack-aws-aws-emu-s3-state` |
| LocalStack | `localstack-aws-localstack-s3-state` |

Each start script mounts its backend's volume directly into that emulator container at `/s3-state`. The scripts import any old `.s3-state/<backend>/snapshot.zip` when the corresponding volume has no snapshot. The original file is kept. If a container predates the mount, its stop script writes one last backup to the old location so the next start can import it. You can see the volumes in Docker Desktop under **Volumes**. They survive container removal. Let uploads finish before stopping the emulator.

The snapshots store buckets, current object contents, and common object headers and metadata. Bucket policies, version history, and other AWS services are outside this backup. A direct `docker stop`, container crash, or forced shutdown skips the save. If the stop script cannot complete a backup, it leaves the container running and retains the previous snapshot. If a restore fails, retry it after fixing the connection with `python3 s3-state.py restore aws-emu` or `python3 s3-state.py restore localstack`.

AWS Emulator stores resources in memory. LocalStack offers native snapshots on paid plans. The scripts use S3 backup and restore for both backends, including LocalStack Hobby. [AWS Emulator state](https://docs.aws.amazon.com/emulator/latest/userguide/what-is-service.html) · [LocalStack persistence](https://docs.localstack.cloud/aws/developer-tools/snapshots/persistence/) · [LocalStack plans](https://docs.localstack.cloud/aws/licensing/)

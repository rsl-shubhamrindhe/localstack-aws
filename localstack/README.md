# LocalStack for local S3 testing

Start Docker Desktop and have your LocalStack developer token ready. The start script prompts for it privately in a terminal if `LOCALSTACK_AUTH_TOKEN` is unset. For non-interactive runs, supply `LOCALSTACK_AUTH_TOKEN` through the environment. The current image requires activation. LocalStack’s Hobby plan includes S3 for personal, non-commercial use. [LocalStack authentication](https://docs.localstack.cloud/aws/getting-started/auth-token/) · [Plan coverage](https://docs.localstack.cloud/aws/licensing/)

From the repository root, start the container and check its health.

```sh
./localstack/start.sh
curl -fsS http://127.0.0.1:4566/_localstack/health
```

If startup fails, check `docker logs localstack-aws` while the container exists. The container publishes only the LocalStack gateway on your Mac's loopback interface. The S3 exercise uses that gateway. [LocalStack Docker setup](https://docs.localstack.cloud/aws/getting-started/installation/)

```sh
./run-local.sh localstack
```

Open [the S3 playground](http://localhost:8080/s3-demo.html), enter an object key and some text, and click **Save object**. The bucket is created on the first save. The launcher selects the HTTP endpoint and tells the local S3 client to sign requests with dummy credentials. The LocalStack token activates the container and stays outside Spring Boot's S3 configuration. [LocalStack S3 endpoint configuration](https://docs.localstack.cloud/aws/services/s3/)

In IntelliJ, select the shared **LocalStack** run configuration. It runs `spring-boot:test-run` with `local,localstack`. The `local` profile enables the shared test-only S3 adapter, and `localstack` loads its connection settings for port `4566`. Stop any Spring Boot instance already using port `8080` before starting this configuration.

To switch back to the official AWS Emulator, stop Spring Boot, make sure the official emulator is running, and run `./run-local.sh aws-emu`. To stop LocalStack, run `./localstack/stop.sh`.

The stop script saves S3 buckets and objects locally, and the start script restores them on the next run. This works with Hobby and paid plans. See [S3 persistence](../README.md#keep-s3-data-across-restarts) for the snapshot location and scope.

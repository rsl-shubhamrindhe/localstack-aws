package rsl.procuris.aws.localstack.demo;

import java.util.function.Function;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

interface S3Connection {

	<T> T execute(Function<S3Client, T> operation);

	default void putObject(PutObjectRequest request, RequestBody body) {
		execute(s3 -> s3.putObject(request, body));
	}
}

package rsl.procuris.aws.localstack.demo;

import java.util.function.Function;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;

@Component
@Profile("!local")
class AwsS3 implements S3Connection {

	private final S3Client client;

	AwsS3(@Value("${AWS_REGION:}") String region) {
		S3ClientBuilder builder = S3Client.builder();
		if (!region.isBlank()) {
			builder.region(Region.of(region));
		}
		client = builder.build();
	}

	@Override
	public <T> T execute(Function<S3Client, T> operation) {
		return operation.apply(client);
	}

	@PreDestroy
	void close() {
		client.close();
	}
}

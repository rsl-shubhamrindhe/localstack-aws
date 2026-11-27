package rsl.procuris.aws.localstack.demo;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.time.Duration;
import java.util.Arrays;
import java.util.function.Function;

import javax.net.ssl.TrustManagerFactory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.apache5.Apache5HttpClient;
import software.amazon.awssdk.profiles.ProfileFile;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Component
@Profile("local")
class EmulatorS3 implements S3Connection {

	private final URI endpoint;
	private final Path directory;
	private S3Client client;
	private byte[] loadedCredentials;
	private byte[] loadedTrustStore;

	EmulatorS3(@Value("${demo.s3.endpoint}") URI endpoint,
			@Value("${demo.s3.emulator-directory}") String emulatorDirectory) {
		this.endpoint = endpoint;
		this.directory = emulatorDirectory.isBlank() ? null : Path.of(emulatorDirectory);
	}

	// Keep the client open until the operation using it has finished.
	@Override
	public synchronized <T> T execute(Function<S3Client, T> operation) {
		S3Client s3;
		try {
			s3 = currentClient();
		} catch (IOException | GeneralSecurityException | IllegalArgumentException exception) {
			throw SdkClientException.create("Could not load local emulator credentials or TLS certificate", exception);
		}
		return operation.apply(s3);
	}

	@Override
	public void putObject(PutObjectRequest request, RequestBody body) {
		execute(s3 -> {
			try {
				return s3.putObject(request, body);
			} catch (S3Exception exception) {
				if (exception.statusCode() != 404) {
					throw exception;
				}
				s3.createBucket(builder -> builder.bucket(request.bucket()));
				return s3.putObject(request, body);
			}
		});
	}

	private S3Client currentClient() throws IOException, GeneralSecurityException {
		if (directory == null) {
			if (client == null) {
				client = clientBuilder()
						.credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test")))
						.build();
			}
			return client;
		}

		byte[] credentials = Files.readAllBytes(directory.resolve("emulator_credentials"));
		byte[] trustStore = Files.readAllBytes(directory.resolve("truststore.p12"));
		if (client == null || !Arrays.equals(credentials, loadedCredentials)
				|| !Arrays.equals(trustStore, loadedTrustStore)) {
			S3Client replacement = buildClient(credentials, trustStore);
			S3Client previous = client;
			client = replacement;
			loadedCredentials = credentials;
			loadedTrustStore = trustStore;
			if (previous != null) {
				previous.close();
			}
		}
		return client;
	}

	@PreDestroy
	synchronized void close() {
		if (client != null) {
			client.close();
		}
	}

	private S3Client buildClient(byte[] credentialBytes, byte[] trustStoreBytes)
			throws IOException, GeneralSecurityException {
		KeyStore trustStore = KeyStore.getInstance("PKCS12");
		trustStore.load(new ByteArrayInputStream(trustStoreBytes), new char[0]);
		TrustManagerFactory trustManagers = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
		trustManagers.init(trustStore);

		ProfileFile credentials = ProfileFile.builder()
				.type(ProfileFile.Type.CREDENTIALS)
				.content(new ByteArrayInputStream(credentialBytes))
				.build();

		return clientBuilder()
				.credentialsProvider(ProfileCredentialsProvider.builder()
						.profileName("default")
						.profileFile(credentials)
						.build())
				.httpClientBuilder(Apache5HttpClient.builder()
						.tlsTrustManagersProvider(trustManagers::getTrustManagers))
				.build();
	}

	private S3ClientBuilder clientBuilder() {
		return S3Client.builder()
				.endpointOverride(endpoint)
				.region(Region.US_EAST_1)
				.forcePathStyle(true)
				.overrideConfiguration(configuration -> configuration.apiCallTimeout(Duration.ofSeconds(5)));
	}
}

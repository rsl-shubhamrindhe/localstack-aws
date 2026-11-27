package rsl.procuris.aws.localstack.demo;

import static software.amazon.awssdk.core.sync.RequestBody.fromString;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;

@RestController
class S3Controller {

	private final S3Connection s3;
	private final String bucket;

	S3Controller(S3Connection s3, @Value("${demo.s3.bucket}") String bucket) {
		this.s3 = s3;
		this.bucket = bucket;
	}

	@GetMapping("/s3")
	BucketListing listObjects() {
		try {
			List<String> keys = s3.execute(client -> client.listObjectsV2Paginator(request -> request.bucket(bucket))
					.contents().stream().map(S3Object::key).toList());
			return new BucketListing(bucket, true, keys.size(), keys);
		} catch (NoSuchBucketException exception) {
			return new BucketListing(bucket, false, 0, List.of());
		} catch (SdkException exception) {
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Could not list S3 objects", exception);
		}
	}

	@GetMapping(value = "/s3/{key}", produces = "text/plain;charset=UTF-8")
	String readObject(@PathVariable String key) {
		try {
			return s3.execute(client -> client.getObjectAsBytes(request -> request.bucket(bucket).key(key)).asUtf8String());
		} catch (NoSuchKeyException | NoSuchBucketException exception) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "S3 object or bucket not found", exception);
		} catch (SdkException exception) {
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Could not read the S3 object", exception);
		}
	}

	@PostMapping("/s3")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void saveObject(@RequestBody TextObject object) {
		if (object.key() == null || object.key().isBlank()
				|| object.key().getBytes(StandardCharsets.UTF_8).length > 1024 || object.text() == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Provide text and a nonblank S3 key of up to 1024 UTF-8 bytes");
		}
		try {
			PutObjectRequest request = PutObjectRequest.builder()
					.bucket(bucket).key(object.key()).contentType("text/plain;charset=UTF-8").build();
			s3.putObject(request, fromString(object.text()));
		} catch (SdkException exception) {
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Could not save the S3 object", exception);
		}
	}

	record BucketListing(String bucket, boolean bucketExists, int count, List<String> objects) {
	}

	record TextObject(String key, String text) {
	}
}

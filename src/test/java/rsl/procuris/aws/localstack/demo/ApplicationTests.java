package rsl.procuris.aws.localstack.demo;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {"AWS_REGION=us-east-1", "S3_BUCKET=application-context-test"})
class ApplicationTests {

	@Test
	void contextLoads() {
	}

}

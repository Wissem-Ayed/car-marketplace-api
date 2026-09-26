package com.carmarketplace;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.time.Duration;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	private static final int S3_PORT = 8333;

	@Bean
	@ServiceConnection
	MongoDBContainer mongoDbContainer() {
		return new MongoDBContainer(DockerImageName.parse("mongo:8.0"));
	}

	@Bean
	GenericContainer<?> seaweedFsContainer() {
		return new GenericContainer<>(DockerImageName.parse("chrislusf/seaweedfs:4.47"))
				.withCommand("server", "-dir=/data", "-s3", "-s3.config=/etc/seaweedfs/s3.json")
				.withCopyFileToContainer(MountableFile.forHostPath("docker/seaweedfs/s3.json"), "/etc/seaweedfs/s3.json")
				.withExposedPorts(S3_PORT)
				.waitingFor(Wait.forHttp("/").forPort(S3_PORT).forStatusCode(403)
						.withStartupTimeout(Duration.ofSeconds(90)));
	}

	@Bean
	DynamicPropertyRegistrar storageProperties(GenericContainer<?> seaweedFsContainer) {
		return registry -> {
			String endpoint = "http://%s:%d".formatted(
					seaweedFsContainer.getHost(), seaweedFsContainer.getMappedPort(S3_PORT));
			registry.add("app.storage.endpoint", () -> endpoint);
			registry.add("app.storage.public-url", () -> endpoint + "/car-photos");
			registry.add("app.rate-limit.enabled", () -> "false");
		};
	}
}

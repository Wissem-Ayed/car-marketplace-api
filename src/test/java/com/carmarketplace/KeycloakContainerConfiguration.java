package com.carmarketplace;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.time.Duration;

@TestConfiguration(proxyBeanMethods = false)
public class KeycloakContainerConfiguration {

	public static final int MAILPIT_WEB_PORT = 8025;
	public static final String ADMIN_USERNAME = "admin";
	public static final String ADMIN_PASSWORD = "admin";

	private static final int KEYCLOAK_PORT = 8080;
	private static final String REALM_FILE = "/opt/keycloak/data/import/car-marketplace-realm.json";

	@Bean(destroyMethod = "close")
	Network identityNetwork() {
		return Network.newNetwork();
	}

	@Bean
	GenericContainer<?> mailpitContainer(Network identityNetwork) {
		return new GenericContainer<>(DockerImageName.parse("axllent/mailpit:v1.31.2"))
				.withNetwork(identityNetwork)
				.withNetworkAliases("mailpit")
				.withExposedPorts(MAILPIT_WEB_PORT)
				.waitingFor(Wait.forHttp("/").forPort(MAILPIT_WEB_PORT));
	}

	@Bean
	GenericContainer<?> keycloakContainer(Network identityNetwork, GenericContainer<?> mailpitContainer) {
		return new GenericContainer<>(DockerImageName.parse("quay.io/keycloak/keycloak:26.7.4"))
				.withNetwork(identityNetwork)
				.dependsOn(mailpitContainer)
				.withEnv("KC_BOOTSTRAP_ADMIN_USERNAME", ADMIN_USERNAME)
				.withEnv("KC_BOOTSTRAP_ADMIN_PASSWORD", ADMIN_PASSWORD)
				.withCommand("start-dev", "--import-realm")
				.withCopyFileToContainer(MountableFile.forHostPath("docker/keycloak/car-marketplace-realm.json"), REALM_FILE)
				.withExposedPorts(KEYCLOAK_PORT)
				.waitingFor(Wait.forHttp("/realms/car-marketplace").forPort(KEYCLOAK_PORT)
						.withStartupTimeout(Duration.ofMinutes(3)));
	}

	@Bean
	DynamicPropertyRegistrar keycloakProperties(GenericContainer<?> keycloakContainer) {
		return registry -> registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri",
				() -> "http://%s:%d/realms/car-marketplace".formatted(
						keycloakContainer.getHost(), keycloakContainer.getMappedPort(KEYCLOAK_PORT)));
	}
}

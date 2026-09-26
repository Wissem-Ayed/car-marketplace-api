package com.carmarketplace.config;

import com.carmarketplace.common.domain.CurrentUser;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.security.OAuthFlow;
import io.swagger.v3.oas.models.security.OAuthFlows;
import io.swagger.v3.oas.models.security.Scopes;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.tags.Tag;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    public static final String CARS_TAG = "Car listings";
    public static final String REFERENCE_DATA_TAG = "Reference data";
    public static final String ACCOUNT_TAG = "My account";
    public static final String SECURITY_SCHEME = "keycloak";

    private static final String PROBLEM_SCHEMA = "Problem";
    private static final String PROBLEM_MEDIA_TYPE = "application/problem+json";

    static {
        SpringDocUtils.getConfig().addRequestWrapperToIgnore(CurrentUser.class);
    }

    @Bean
    OpenAPI carMarketplaceOpenApi(@Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuer) {
        return new OpenAPI()
                .info(new Info()
                        .title("Car Marketplace API")
                        .version("v1")
                        .description("""
                                REST API for a car marketplace targeting the Tunisian market.

                                * Listings reference the **catalog** (brand → model → generation): send ids from \
                                the *Reference data* endpoints, the server resolves the names and checks that the \
                                year matches the generation.
                                * Prices are in **Tunisian dinars (TND)** with up to 3 decimals.
                                * Every error follows **RFC 9457 Problem Details** \
                                (`application/problem+json`).
                                * Browsing is public. Publishing and managing listings requires logging in: click \
                                **Authorize**, then log in on the Keycloak page (demo accounts are listed in the README).
                                """)
                        .contact(new Contact().name("Wissem Ayed").url("https://github.com/Wissem-Ayed")))
                .externalDocs(new ExternalDocumentation()
                        .description("Source code, architecture and design decisions")
                        .url("https://github.com/Wissem-Ayed/car-marketplace-api"))
                .addTagsItem(new Tag().name(CARS_TAG).description("Publish, search and manage car listings"))
                .addTagsItem(new Tag().name(REFERENCE_DATA_TAG)
                        .description("Brands, models, generations and equipment codes used to build a listing"))
                .addTagsItem(new Tag().name(ACCOUNT_TAG).description("The logged-in user and their listings"))
                .components(new Components().addSecuritySchemes(SECURITY_SCHEME, new SecurityScheme()
                        .type(SecurityScheme.Type.OAUTH2)
                        .description("OpenID Connect login with Keycloak (authorization code flow with PKCE)")
                        .flows(new OAuthFlows().authorizationCode(new OAuthFlow()
                                .authorizationUrl(issuer + "/protocol/openid-connect/auth")
                                .tokenUrl(issuer + "/protocol/openid-connect/token")
                                .scopes(new Scopes()
                                        .addString("openid", "Sign in")
                                        .addString("profile", "Your name"))))));
    }

    @Bean
    OpenApiCustomizer problemDetailErrorResponses() {
        return openApi -> {
            openApi.getComponents().addSchemas(PROBLEM_SCHEMA, problemSchema());
            Schema<?> problemRef = new Schema<>().$ref("#/components/schemas/" + PROBLEM_SCHEMA);
            openApi.getPaths().values().forEach(path -> path.readOperations().forEach(operation ->
                    operation.getResponses().forEach((code, response) -> {
                        if (code.startsWith("4")) {
                            response.setContent(new Content()
                                    .addMediaType(PROBLEM_MEDIA_TYPE, new MediaType().schema(problemRef)));
                        }
                    })));
        };
    }

    private static Schema<?> problemSchema() {
        ObjectSchema fieldError = new ObjectSchema();
        fieldError.addProperty("field", new StringSchema().example("price.amount"));
        fieldError.addProperty("message", new StringSchema().example("must be greater than 0"));

        ObjectSchema problem = new ObjectSchema();
        problem.description("RFC 9457 Problem Details");
        problem.addProperty("type", new StringSchema().example("about:blank"));
        problem.addProperty("title", new StringSchema().example("Business rule violated"));
        problem.addProperty("status", new IntegerSchema().example(422));
        problem.addProperty("detail", new StringSchema()
                .example("Mercedes-Benz CLA C118, X118 was produced in 2019–present, not in 2015"));
        problem.addProperty("instance", new StringSchema().example("/api/v1/cars"));
        problem.addProperty("errors", new ArraySchema().items(fieldError)
                .description("Invalid fields, only present on 400 validation errors"));
        return problem;
    }
}

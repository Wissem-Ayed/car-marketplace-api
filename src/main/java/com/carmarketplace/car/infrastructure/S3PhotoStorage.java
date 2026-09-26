package com.carmarketplace.car.infrastructure;

import com.carmarketplace.car.application.PhotoStorage;
import com.carmarketplace.config.StorageProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;

import java.util.Collection;

@Component
@RequiredArgsConstructor
class S3PhotoStorage implements PhotoStorage {

    private static final String CACHE_FOREVER = "public, max-age=31536000, immutable";

    private final S3Client s3Client;
    private final StorageProperties properties;

    @Override
    public void store(String key, byte[] content, String contentType) {
        s3Client.putObject(request -> request
                        .bucket(properties.bucket())
                        .key(key)
                        .contentType(contentType)
                        .cacheControl(CACHE_FOREVER),
                RequestBody.fromBytes(content));
    }

    @Override
    public void delete(Collection<String> keys) {
        if (keys.isEmpty()) {
            return;
        }
        s3Client.deleteObjects(request -> request
                .bucket(properties.bucket())
                .delete(delete -> delete.objects(keys.stream()
                        .map(key -> ObjectIdentifier.builder().key(key).build())
                        .toList())));
    }

    @Override
    public String publicUrl(String key) {
        return properties.publicUrl() + "/" + key;
    }
}

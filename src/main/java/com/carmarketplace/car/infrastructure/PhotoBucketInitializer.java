package com.carmarketplace.car.infrastructure;

import com.carmarketplace.config.StorageProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;

@Slf4j
@Component
@RequiredArgsConstructor
class PhotoBucketInitializer implements ApplicationRunner {

    private final S3Client s3Client;
    private final StorageProperties properties;

    @Override
    public void run(ApplicationArguments args) {
        try {
            s3Client.headBucket(request -> request.bucket(properties.bucket()));
        } catch (NoSuchBucketException e) {
            s3Client.createBucket(request -> request.bucket(properties.bucket()));
            log.info("Created photo bucket '{}'", properties.bucket());
        }
    }
}

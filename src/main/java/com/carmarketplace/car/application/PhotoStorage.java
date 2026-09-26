package com.carmarketplace.car.application;

import java.util.Collection;

public interface PhotoStorage {

    void store(String key, byte[] content, String contentType);

    void delete(Collection<String> keys);

    String publicUrl(String key);
}

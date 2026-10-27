package com.huaweicloud.hdgatewayservice.repository;

import com.huaweicloud.hdgatewayservice.model.ApiKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ApiKeyRepository extends JpaRepository<ApiKey, Long> {

    Optional<ApiKey> findByApiKeyAndEnabledTrue(String apiKey);

    Optional<ApiKey> findBySource(String source);
}

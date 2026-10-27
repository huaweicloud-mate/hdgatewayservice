package com.huaweicloud.hdgatewayservice.controller;

import com.huaweicloud.hdgatewayservice.model.ApiKey;
import com.huaweicloud.hdgatewayservice.repository.ApiKeyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiKeyControllerTest {

    @Mock
    private ApiKeyRepository repository;

    private ApiKeyController controller;

    @BeforeEach
    void setUp() {
        controller = new ApiKeyController(repository);
    }

    @Test
    void createKeyForNewSource() {
        when(repository.findBySource("caller-a")).thenReturn(Optional.empty());
        when(repository.save(any(ApiKey.class))).thenAnswer(inv -> inv.getArgument(0));

        ResponseEntity<Map<String, Object>> resp = controller.createKey("caller-a", null);

        assertEquals(200, resp.getStatusCode().value());
        assertEquals("caller-a", resp.getBody().get("source"));
        assertEquals(false, resp.getBody().get("rotated"));
        assertTrue(((String) resp.getBody().get("apiKey")).length() == 32);
    }

    @Test
    void createKeyWithRoutesStoresAllowedRoutes() {
        when(repository.findBySource("caller-a")).thenReturn(Optional.empty());
        when(repository.save(any(ApiKey.class))).thenAnswer(inv -> inv.getArgument(0));

        ResponseEntity<Map<String, Object>> resp = controller.createKey("caller-a", "HIS-Demo, direct");

        assertEquals(200, resp.getStatusCode().value());
        assertEquals(java.util.Set.of("his-demo", "direct"), resp.getBody().get("routes"));
    }

    @Test
    void recreateSameSourceRotatesKey() {
        ApiKey existing = new ApiKey("old-key-0123456789abcdef012345", "caller-a");
        existing.setId(7L);
        when(repository.findBySource("caller-a")).thenReturn(Optional.of(existing));
        when(repository.save(any(ApiKey.class))).thenAnswer(inv -> inv.getArgument(0));

        ResponseEntity<Map<String, Object>> resp = controller.createKey("caller-a", null);

        assertEquals(200, resp.getStatusCode().value());
        assertEquals(7L, resp.getBody().get("id"));
        assertEquals(true, resp.getBody().get("rotated"));
        assertNotEquals("old-key-0123456789abcdef012345", resp.getBody().get("apiKey"));
        assertTrue((Boolean) existing.getEnabled());
    }

    @Test
    void recreateUpdatesRoutes() {
        ApiKey existing = new ApiKey("old-key", "caller-a");
        existing.setId(7L);
        when(repository.findBySource("caller-a")).thenReturn(Optional.of(existing));
        when(repository.save(any(ApiKey.class))).thenAnswer(inv -> inv.getArgument(0));

        controller.createKey("caller-a", "his-demo");

        assertEquals("his-demo", existing.getAllowedRoutes());
    }

    @Test
    void blankSourceRejected() {
        ResponseEntity<Map<String, Object>> resp = controller.createKey("  ", null);
        assertEquals(400, resp.getStatusCode().value());
    }

    @Test
    void oversizeRoutesRejected() {
        ResponseEntity<Map<String, Object>> resp = controller.createKey("caller-a", "x".repeat(513));
        assertEquals(400, resp.getStatusCode().value());
    }

    @Test
    void disableUnknownKeyReturns404() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        ResponseEntity<Map<String, String>> resp = controller.disableKey(99L);

        assertEquals(404, resp.getStatusCode().value());
    }

    @Test
    void disableKnownKeyDisables() {
        ApiKey existing = new ApiKey("key", "caller-a");
        existing.setId(1L);
        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        when(repository.save(any(ApiKey.class))).thenAnswer(inv -> inv.getArgument(0));

        ResponseEntity<Map<String, String>> resp = controller.disableKey(1L);

        assertEquals(200, resp.getStatusCode().value());
        assertEquals("disabled", resp.getBody().get("status"));
        assertEquals(false, existing.getEnabled());
    }
}

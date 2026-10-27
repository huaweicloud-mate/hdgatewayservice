package com.huaweicloud.hdgatewayservice.config;

import com.huaweicloud.hdgatewayservice.model.RouteInfo;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RouteRegistryTest {

    private RouteRegistry registryOf(GatewayRoutesProperties.RouteDef... defs) {
        GatewayRoutesProperties props = new GatewayRoutesProperties();
        props.setRoutes(new ArrayList<>(Arrays.asList(defs)));
        return new RouteRegistry(props);
    }

    @Test
    void findReturnsDefForRegisteredRoute() {
        RouteRegistry registry = registryOf(new GatewayRoutesProperties.RouteDef(
                "his-demo", "https://his.example.com", "HIS", List.of("GET", "POST"), null));
        GatewayRoutesProperties.RouteDef def = registry.find("his-demo");
        assertEquals("https://his.example.com", def.url());
        assertEquals("HIS", def.auth());
    }

    @Test
    void findIsCaseInsensitive() {
        RouteRegistry registry = registryOf(new GatewayRoutesProperties.RouteDef(
                "his-demo", "https://his.example.com", "HIS", List.of("GET"), null));
        assertEquals("https://his.example.com", registry.find("HIS-DEMO").url());
        assertEquals("https://his.example.com", registry.find(" His-Demo ").url());
    }

    @Test
    void findReturnsNullForUnknownOrBlank() {
        RouteRegistry registry = registryOf(new GatewayRoutesProperties.RouteDef(
                "his-demo", "https://his.example.com", "HIS", List.of("GET"), null));
        assertNull(registry.find("nope"));
        assertNull(registry.find(null));
        assertNull(registry.find("  "));
    }

    @Test
    void blankNameIgnored() {
        RouteRegistry registry = registryOf(new GatewayRoutesProperties.RouteDef(
                "  ", "https://x.example.com", "HIS", List.of("GET"), null));
        assertNull(registry.find(""));
        assertEquals(0, registry.list().size());
    }

    @Test
    void duplicateNameIgnored() {
        RouteRegistry registry = registryOf(
                new GatewayRoutesProperties.RouteDef("r", "https://a.example.com", "HIS", List.of("GET"), null),
                new GatewayRoutesProperties.RouteDef("R", "https://b.example.com", "HIS", List.of("GET"), null));
        assertEquals("https://a.example.com", registry.find("r").url());
        assertEquals(1, registry.list().size());
    }

    @Test
    void authNormalization() {
        assertEquals("HIS", RouteRegistry.authOf(new GatewayRoutesProperties.RouteDef(
                "r", "https://x", "his", List.of("GET"), null)));
        assertEquals("APPCODE", RouteRegistry.authOf(new GatewayRoutesProperties.RouteDef(
                "r", "https://x", "appcode", List.of("GET"), null)));
        assertEquals("NONE", RouteRegistry.authOf(new GatewayRoutesProperties.RouteDef(
                "r", "https://x", null, List.of("GET"), null)));
        assertEquals("NONE", RouteRegistry.authOf(new GatewayRoutesProperties.RouteDef(
                "r", "https://x", "weird", List.of("GET"), null)));
    }

    @Test
    void methodsNormalizedToUpper() {
        List<String> methods = RouteRegistry.methodsOf(new GatewayRoutesProperties.RouteDef(
                "r", "https://x", "HIS", Arrays.asList("get", "Post", " ", null), null));
        assertEquals(List.of("GET", "POST"), methods);
    }

    @Test
    void methodsNullMeansEmptyList() {
        assertTrue(RouteRegistry.methodsOf(new GatewayRoutesProperties.RouteDef(
                "r", "https://x", "HIS", null, null)).isEmpty());
    }

    @Test
    void configuredFalseWhenUrlBlank() {
        assertFalse(RouteRegistry.configured(new GatewayRoutesProperties.RouteDef(
                "r", "", "HIS", List.of("GET"), null)));
        assertFalse(RouteRegistry.configured(new GatewayRoutesProperties.RouteDef(
                "r", null, "HIS", List.of("GET"), null)));
        assertTrue(RouteRegistry.configured(new GatewayRoutesProperties.RouteDef(
                "r", "https://x", "HIS", List.of("GET"), null)));
    }

    @Test
    void listExposesNameAuthMethodsButNeverUrl() {
        RouteRegistry registry = registryOf(new GatewayRoutesProperties.RouteDef(
                "his-demo", "https://his.example.com/secret", "HIS", List.of("GET", "POST"), null));
        List<RouteInfo> routes = registry.list();
        assertEquals(1, routes.size());
        RouteInfo info = routes.get(0);
        assertEquals("his-demo", info.name());
        assertEquals("HIS", info.auth());
        assertEquals(List.of("GET", "POST"), info.methods());
        assertTrue(info.configured());
        assertFalse(info.toString().contains("https://his.example.com"));
    }
}

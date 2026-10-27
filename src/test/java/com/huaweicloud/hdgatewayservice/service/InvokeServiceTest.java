package com.huaweicloud.hdgatewayservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huaweicloud.hdgatewayservice.config.GatewayConfig;
import com.huaweicloud.hdgatewayservice.config.GatewayRoutesProperties;
import com.huaweicloud.hdgatewayservice.config.RouteRegistry;
import com.huaweicloud.hdgatewayservice.model.InvokeRequest;
import com.huaweicloud.hdgatewayservice.model.RouteInfo;
import com.huaweicloud.hdgatewayservice.transport.AppCodeTransport;
import com.huaweicloud.hdgatewayservice.transport.HisTransport;
import com.huaweicloud.hdgatewayservice.transport.PassThroughTransport;
import com.huaweicloud.hdgatewayservice.transport.Transport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvokeServiceTest {

    private static final GatewayRoutesProperties.RouteDef HIS_ROUTE = new GatewayRoutesProperties.RouteDef(
            "his-demo", "https://his.example.com", "HIS", List.of("GET", "POST"), null);
    private static final GatewayRoutesProperties.RouteDef APIG_ROUTE = new GatewayRoutesProperties.RouteDef(
            "apig-demo", "https://apig.example.com/", "APPCODE", List.of("POST"), "route-appcode");
    private static final GatewayRoutesProperties.RouteDef NONE_ROUTE = new GatewayRoutesProperties.RouteDef(
            "direct", "https://direct.example.com", null, List.of("GET"), null);
    private static final GatewayRoutesProperties.RouteDef UNCONFIGURED = new GatewayRoutesProperties.RouteDef(
            "empty", "", "HIS", List.of("GET"), null);

    @Mock
    private HisTransport hisTransport;
    @Mock
    private AppCodeTransport appCodeTransport;
    @Mock
    private PassThroughTransport passThroughTransport;

    private InvokeService service;

    @BeforeEach
    void setUp() {
        GatewayRoutesProperties props = new GatewayRoutesProperties();
        props.setRoutes(new java.util.ArrayList<>(List.of(HIS_ROUTE, APIG_ROUTE, NONE_ROUTE, UNCONFIGURED)));
        RouteRegistry registry = new RouteRegistry(props);

        GatewayConfig config = new GatewayConfig();
        ReflectionTestUtils.setField(config, "callerHeaderDeny",
                "host,content-length,authorization,cookie,connection,transfer-encoding,expect,x-apig,x-hw,x-auth,x-api-key,x-traceid");

        service = new InvokeService(registry, hisTransport, appCodeTransport, passThroughTransport, config);
    }

    private InvokeRequest req(String route, String path, String method, String query,
                              Map<String, String> headers, Object body) throws Exception {
        return new InvokeRequest(route, path, method, query, headers,
                body == null ? null : new ObjectMapper().valueToTree(body));
    }

    /** allowedRoutes 为空（未限制）的默认 invoke */
    private ResponseEntity<String> invoke(InvokeRequest r) throws Exception {
        return service.invoke(r, null);
    }

    @Test
    void hisRouteRoutedToHisTransportAndPassthrough() throws Exception {
        when(hisTransport.exchange(any(), eq("POST"), eq("/v1/check"), eq(""),
                any(), anyString())).thenReturn(new Transport.RawResponse(200, "{\"ok\":true}"));

        ResponseEntity<String> resp = invoke(req("his-demo", "/v1/check", "POST", "",
                null, Map.of("customer_id", "d1")));

        assertEquals(200, resp.getStatusCode().value());
        assertEquals("{\"ok\":true}", resp.getBody());
        verify(hisTransport).exchange(any(), eq("POST"), eq("/v1/check"), eq(""), any(), anyString());
        verify(appCodeTransport, never()).exchange(any(), anyString(), anyString(), anyString(), any(), anyString());
    }

    @Test
    void apigRouteRoutedToAppCodeTransport() throws Exception {
        when(appCodeTransport.exchange(any(), eq("POST"), eq("/v1/signup"), eq(""),
                any(), anyString())).thenReturn(new Transport.RawResponse(200, "{\"id\":1}"));

        ResponseEntity<String> resp = invoke(req("apig-demo", "/v1/signup", null, "", null, Map.of()));

        assertEquals(200, resp.getStatusCode().value());
        verify(appCodeTransport).exchange(any(), eq("POST"), eq("/v1/signup"), eq(""), any(), anyString());
        verify(hisTransport, never()).exchange(any(), anyString(), anyString(), anyString(), any(), anyString());
    }

    @Test
    void noneRouteRoutedToPassThroughTransport() throws Exception {
        when(passThroughTransport.exchange(any(), eq("GET"), eq("/ping"), eq(""),
                any(), eq(""))).thenReturn(new Transport.RawResponse(200, "pong"));

        ResponseEntity<String> resp = invoke(req("direct", "/ping", "GET", "", null, null));

        assertEquals(200, resp.getStatusCode().value());
        assertEquals("pong", resp.getBody());
    }

    @Test
    void defaultMethodIsPost() throws Exception {
        when(hisTransport.exchange(any(), eq("POST"), eq("/x"), eq(""), any(), eq("")))
                .thenReturn(new Transport.RawResponse(204, ""));

        invoke(req("his-demo", "/x", null, "", null, null));

        ArgumentCaptor<String> methodCaptor = ArgumentCaptor.forClass(String.class);
        verify(hisTransport).exchange(any(), methodCaptor.capture(), eq("/x"), eq(""), any(), eq(""));
        assertEquals("POST", methodCaptor.getValue());
    }

    @Test
    void routeNotFound() throws Exception {
        InvokeService.InvokeException e = assertThrows(InvokeService.InvokeException.class,
                () -> invoke(req("nope", "/x", null, "", null, null)));
        assertEquals("HDGW_ROUTE_NOT_FOUND", e.code());
    }

    @Test
    void routeNotConfigured() throws Exception {
        InvokeService.InvokeException e = assertThrows(InvokeService.InvokeException.class,
                () -> invoke(req("empty", "/x", null, "", null, null)));
        assertEquals("HDGW_ROUTE_NOT_CONFIGURED", e.code());
    }

    @Test
    void methodNotAllowed() throws Exception {
        InvokeService.InvokeException e = assertThrows(InvokeService.InvokeException.class,
                () -> invoke(req("apig-demo", "/x", "GET", "", null, null)));
        assertEquals("HDGW_METHOD_NOT_ALLOWED", e.code());
    }

    @Test
    void routeForbiddenWhenKeyNotAuthorized() throws Exception {
        InvokeService.InvokeException e = assertThrows(InvokeService.InvokeException.class,
                () -> service.invoke(req("his-demo", "/x", "POST", "", null, Map.of()),
                        Set.of("apig-demo", "direct")));
        assertEquals("HDGW_ROUTE_FORBIDDEN", e.code());
        verify(hisTransport, never()).exchange(any(), anyString(), anyString(), anyString(), any(), anyString());
    }

    @Test
    void authorizedKeyCanInvokeAllowedRoute() throws Exception {
        when(hisTransport.exchange(any(), eq("POST"), eq("/x"), eq(""), any(), anyString()))
                .thenReturn(new Transport.RawResponse(200, "{}"));

        ResponseEntity<String> resp = service.invoke(req("his-demo", "/x", "POST", "", null, Map.of()),
                Set.of("HIS-DEMO"));

        assertEquals(200, resp.getStatusCode().value());
        verify(hisTransport).exchange(any(), eq("POST"), eq("/x"), eq(""), any(), anyString());
    }

    @Test
    void emptyAllowedRoutesMeansUnrestricted() throws Exception {
        when(hisTransport.exchange(any(), eq("POST"), eq("/x"), eq(""), any(), anyString()))
                .thenReturn(new Transport.RawResponse(200, "{}"));

        assertEquals(200, service.invoke(req("his-demo", "/x", "POST", "", null, Map.of()),
                Set.of()).getStatusCode().value());
        assertEquals(200, service.invoke(req("his-demo", "/x", "POST", "", null, Map.of()),
                null).getStatusCode().value());
    }

    @Test
    void invalidPathRejected() {
        assertInvalidPath("v1/check");
        assertInvalidPath("//evil.example.com/x");
        assertInvalidPath("https://evil.example.com/x");
        assertInvalidPath("/a/../b");
        assertInvalidPath("/a\u0000b");
    }

    private void assertInvalidPath(String path) {
        try {
            InvokeService.InvokeException e = assertThrows(InvokeService.InvokeException.class,
                    () -> invoke(req("his-demo", path, "GET", "", null, null)));
            assertEquals("HDGW_INVALID_REQUEST", e.code());
        } catch (Exception ex) {
            throw new AssertionError(ex);
        }
    }

    @Test
    void callerHeadersSanitizedBeforeForwarding() throws Exception {
        when(hisTransport.exchange(any(), eq("POST"), eq("/x"), eq(""), any(), anyString()))
                .thenReturn(new Transport.RawResponse(200, "{}"));

        invoke(req("his-demo", "/x", "POST", "", Map.of(
                "X-HW-ID", "spoof",
                "X-Apig-AppCode", "spoof",
                "Authorization", "Bearer spoof",
                "X-TraceID", "spoof",
                "X-Biz-Trace", "real",
                "", "ignored"
        ), Map.of()));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> headersCaptor = ArgumentCaptor.forClass(Map.class);
        verify(hisTransport).exchange(any(), eq("POST"), eq("/x"), eq(""), headersCaptor.capture(), anyString());
        Map<String, String> safe = headersCaptor.getValue();
        assertEquals(Map.of("X-Biz-Trace", "real"), safe);
    }

    @Test
    void upstreamExceptionPropagatesForErrorMapping() throws Exception {
        when(hisTransport.exchange(any(), eq("POST"), eq("/x"), eq(""), any(), anyString()))
                .thenThrow(new Transport.UpstreamException("boom", null, false));

        assertThrows(Transport.UpstreamException.class,
                () -> invoke(req("his-demo", "/x", "POST", "", null, Map.of())));
    }

    @Test
    void routesListNeverContainsUpstreamUrl() {
        List<RouteInfo> routes = service.routes();
        assertEquals(4, routes.size());
        for (RouteInfo info : routes) {
            assertNotNull(info.name());
            assertNotNull(info.auth());
            assertNotNull(info.methods());
            assertFalse(info.toString().contains("example.com"));
        }
        assertTrue(routes.stream().anyMatch(r -> "his-demo".equals(r.name()) && "HIS".equals(r.auth())));
    }
}

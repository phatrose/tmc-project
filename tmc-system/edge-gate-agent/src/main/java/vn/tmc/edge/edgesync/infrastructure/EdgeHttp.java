package vn.tmc.edge.edgesync.infrastructure;

import vn.tmc.edge.edgesync.domain.SyncProtocolException;
import vn.tmc.edge.edgesync.domain.SyncTransportException;

import javax.net.ssl.SSLContext;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

final class EdgeHttp {
    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private final HttpClient client;

    EdgeHttp(SSLContext deviceTls) {
        client = HttpClient.newBuilder().sslContext(deviceTls).connectTimeout(CONNECT_TIMEOUT).build();
    }

    static HttpRequest.Builder request(URI endpoint) {
        if (!"https".equalsIgnoreCase(endpoint.getScheme()) || endpoint.getHost() == null) {
            throw new IllegalArgumentException("Central endpoint must be HTTPS");
        }
        return HttpRequest.newBuilder(endpoint).timeout(REQUEST_TIMEOUT).header("Accept", "application/json");
    }

    HttpResponse<byte[]> send(HttpRequest request) {
        var exchange = client.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray());
        try {
            return exchange.get(REQUEST_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            exchange.cancel(true);
            Thread.currentThread().interrupt();
            throw new SyncTransportException(interrupted);
        } catch (TimeoutException timeout) {
            exchange.cancel(true);
            throw new SyncTransportException(timeout);
        } catch (ExecutionException failed) {
            if (failed.getCause() instanceof javax.net.ssl.SSLException tls) {
                throw new SyncProtocolException("Central TLS validation failed", tls);
            }
            throw new SyncTransportException(failed.getCause());
        }
    }
}

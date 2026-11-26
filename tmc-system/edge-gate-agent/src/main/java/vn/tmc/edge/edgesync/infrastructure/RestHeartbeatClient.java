package vn.tmc.edge.edgesync.infrastructure;

import vn.tmc.edge.edgesync.domain.HeartbeatPort;
import vn.tmc.edge.edgesync.domain.SyncProtocolException;
import vn.tmc.edge.edgesync.domain.SyncTransportException;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Objects;
import java.util.function.Function;

public final class RestHeartbeatClient implements HeartbeatPort {
    private final URI endpoint;
    private final Function<HttpRequest, HttpResponse<byte[]>> sender;

    public RestHeartbeatClient(URI endpoint, SSLContext deviceTls) {
        this(endpoint, new EdgeHttp(Objects.requireNonNull(deviceTls))::send);
    }

    RestHeartbeatClient(URI endpoint, Function<HttpRequest, HttpResponse<byte[]>> sender) {
        this.endpoint = Objects.requireNonNull(endpoint);
        EdgeHttp.request(endpoint);
        this.sender = Objects.requireNonNull(sender);
    }

    @Override
    public Receipt send(Heartbeat heartbeat) {
        try {
            var response = sender.apply(EdgeHttp.request(endpoint)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(WireJson.write(heartbeat))).build());
            if (response.statusCode() != 200) {
                throw new SyncTransportException(response.statusCode(), 0);
            }
            return WireJson.readResponse(response.body(), Receipt.class);
        } catch (IOException invalid) {
            throw new SyncProtocolException("Invalid heartbeat data", invalid);
        }
    }
}

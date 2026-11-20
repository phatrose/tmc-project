package vn.tmc.edge.edgesync.infrastructure;

import vn.tmc.edge.edgesync.domain.HeartbeatPort;
import vn.tmc.edge.edgesync.domain.SyncProtocolException;
import vn.tmc.edge.edgesync.domain.SyncTransportException;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.util.Objects;

public final class RestHeartbeatClient implements HeartbeatPort {
    private final URI endpoint;
    private final EdgeHttp http;

    public RestHeartbeatClient(URI endpoint, SSLContext deviceTls) {
        this.endpoint = Objects.requireNonNull(endpoint);
        EdgeHttp.request(endpoint);
        http = new EdgeHttp(Objects.requireNonNull(deviceTls));
    }

    @Override
    public Receipt send(Heartbeat heartbeat) {
        try {
            var response = http.send(EdgeHttp.request(endpoint)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(WireJson.write(heartbeat))).build());
            if (response.statusCode() != 200) {
                throw new SyncTransportException(response.statusCode(), 0);
            }
            return WireJson.read(response.body(), Receipt.class);
        } catch (IOException invalid) {
            throw new SyncProtocolException("Invalid heartbeat data", invalid);
        }
    }
}

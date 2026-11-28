package vn.tmc.edge.edgesync.infrastructure;

import vn.tmc.contracts.BootstrapResponse.KeySet;
import vn.tmc.edge.edgesync.domain.BootstrapProtocolException;
import vn.tmc.edge.edgesync.domain.KeyRefreshPort;
import vn.tmc.edge.edgesync.domain.SyncTransportException;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Objects;
import java.util.function.Function;

public final class RestKeyRefreshClient implements KeyRefreshPort {
    private final URI endpoint;
    private final Function<HttpRequest, HttpResponse<byte[]>> sender;

    public RestKeyRefreshClient(URI endpoint, SSLContext deviceTls) {
        this(endpoint, new EdgeHttp(Objects.requireNonNull(deviceTls))::send);
    }

    RestKeyRefreshClient(URI endpoint, Function<HttpRequest, HttpResponse<byte[]>> sender) {
        this.endpoint = Objects.requireNonNull(endpoint);
        EdgeHttp.request(endpoint);
        if (endpoint.getQuery() != null || endpoint.getFragment() != null) {
            throw new IllegalArgumentException("Key refresh endpoint must omit query and fragment");
        }
        this.sender = Objects.requireNonNull(sender);
    }

    @Override
    public KeySet fetch() {
        var response = sender.apply(EdgeHttp.request(endpoint).GET().build());
        if (response.statusCode() != 200) {
            throw new SyncTransportException(response.statusCode(), 0);
        }
        try {
            var keySet = WireJson.readResponse(response.body(), KeySet.class);
            if (keySet == null) {
                throw new BootstrapProtocolException("Key refresh body is missing");
            }
            return keySet;
        } catch (IOException invalid) {
            throw new BootstrapProtocolException("Malformed key refresh response", invalid);
        }
    }
}

package io.marketplace.sdk.core.http;

public interface HttpClient {
    HttpResponse execute(HttpRequest request);
    void shutdown();
}

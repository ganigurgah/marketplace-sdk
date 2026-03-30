package io.marketplace.sdk.core.http;

import io.marketplace.sdk.core.exception.MarketplaceException;
import okhttp3.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class OkHttpClientImpl implements HttpClient {
    private final OkHttpClient okClient;

    public OkHttpClientImpl(int timeoutSeconds) {
        this.okClient = new OkHttpClient.Builder()
            .connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .writeTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .build();
    }

    @Override
    public HttpResponse execute(HttpRequest request) {
        Request.Builder builder = new Request.Builder().url(request.getUrl());
        request.getHeaders().forEach(builder::addHeader);

        RequestBody requestBody = null;
        if (request.getBody() != null) {
            String cType = request.getHeaders().getOrDefault("Content-Type", "application/json; charset=utf-8");
            requestBody = RequestBody.create(
                request.getBody(),
                MediaType.parse(cType)
            );
        } else if (request.getMethod().equals("POST") || request.getMethod().equals("PUT")) {
            requestBody = RequestBody.create("", null);
        }

        builder.method(request.getMethod(), requestBody);

        try (Response response = okClient.newCall(builder.build()).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            Map<String, String> headers = new HashMap<>();
            response.headers().forEach(pair -> headers.put(pair.getFirst(), pair.getSecond()));
            return new HttpResponse(response.code(), responseBody, headers);
        } catch (IOException e) {
            throw new MarketplaceException("HTTP_ERROR", "HTTP request failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void shutdown() {
        okClient.dispatcher().executorService().shutdown();
        okClient.connectionPool().evictAll();
    }
}

package com.chatopera.bot.sdk;

import kong.unirest.*;
import org.json.JSONObject;

import java.util.HashMap;

public class RestAPI {
    public RestAPI() {
    }

    private static void x(HashMap<String, String> headers) {
        if (headers == null) {
            headers = new HashMap();
            headers.put("accept", "application/json");
        } else {
            if (!headers.containsKey("Content-Type")) {
                headers.put("Content-Type", "application/json");
            }

            if (!headers.containsKey("accept") && !headers.containsKey("Accept")) {
                headers.put("Accept", "application/json");
            }

        }
    }

    public static JSONObject post(String url, JSONObject body, HashMap<String, Object> query, HashMap<String, String> headers) throws UnirestException {
        HttpRequestWithBody request = Unirest.post(url);
        x(headers);
        HttpResponse<JsonNode> resp = ((HttpRequestWithBody) ((HttpRequestWithBody) request.headers(headers)).queryString(query)).body(body.toString()).asJson();
        kong.unirest.json.JSONObject obj = ((JsonNode) resp.getBody()).getObject();
        return new JSONObject(obj.toString());
    }

    public static JSONObject post(String url, JSONObject body) throws UnirestException {
        return post(url, body, (HashMap) null, (HashMap) null);
    }

    public static JSONObject get(String url, HashMap<String, Object> query, HashMap<String, String> headers) throws UnirestException {
        GetRequest request = Unirest.get(url);
        x(headers);
        HttpResponse<JsonNode> resp = ((GetRequest) ((GetRequest) request.headers(headers)).queryString(query)).asJson();
        kong.unirest.json.JSONObject obj = ((JsonNode) resp.getBody()).getObject();
        return new JSONObject(obj.toString());
    }

    public static JSONObject get(String url) throws UnirestException {
        return get(url, (HashMap) null, (HashMap) null);
    }

    public static JSONObject get(String url, HashMap<String, Object> query) throws UnirestException {
        return get(url, query, (HashMap) null);
    }

    public static JSONObject delete(String url, HashMap<String, String> headers) throws UnirestException {
        x(headers);
        return new JSONObject(((JsonNode) ((HttpRequestWithBody) Unirest.delete(url).headers(headers)).asJson().getBody()).getObject().toString());
    }

    public static JSONObject put(String url, HashMap<String, Object> body, HashMap<String, String> headers) throws UnirestException {
        x(headers);
        return new JSONObject(((JsonNode) ((HttpRequestWithBody) Unirest.put(url).headers(headers)).fields(body).asJson().getBody()).getObject().toString());
    }

    public static JSONObject put(String url, JSONObject body, HashMap<String, Object> query, HashMap<String, String> headers) throws UnirestException {
        HttpRequestWithBody request = Unirest.put(url);
        x(headers);
        HttpResponse<JsonNode> resp = ((HttpRequestWithBody) ((HttpRequestWithBody) request.headers(headers)).queryString(query)).body(body.toString()).asJson();
        kong.unirest.json.JSONObject obj = ((JsonNode) resp.getBody()).getObject();
        return new JSONObject(obj.toString());
    }
}

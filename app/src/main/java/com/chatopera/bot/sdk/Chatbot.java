package com.chatopera.bot.sdk;

import com.chatopera.bot.exception.ChatbotException;
import com.chatopera.bot.utils.FileUtil;
import kong.unirest.HttpRequestWithBody;
import kong.unirest.JsonNode;
import kong.unirest.Unirest;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

public class Chatbot {
    private String schema;
    private String hostname;
    private int port;
    private String baseUrl;
    private String clientId;
    private String clientSecret;
    private Credentials credentials;
    private static final int ASR_DEFAULT_NBEST = 5;
    private static final boolean ASR_DEFAULT_POS = false;

    private Chatbot() {
    }

    public Chatbot(String clientId, String clientSecret, String baseUrl) throws ChatbotException, MalformedURLException {
        if (StringUtils.isBlank(baseUrl)) {
            throw new ChatbotException("智能问答引擎URL不能为空。");
        } else {
            if (StringUtils.isNotBlank(clientId) && StringUtils.isNotBlank(clientSecret)) {
                this.clientId = clientId;
                this.clientSecret = clientSecret;
                this.credentials = new Credentials(this.clientId, this.clientSecret);
            }

            this.parseEndpoint(baseUrl);
        }
    }

    public Chatbot(String clientId, String clientSecret) throws MalformedURLException, ChatbotException {
        this(clientId, clientSecret, "https://bot.chatopera.com");
    }

    private void parseEndpoint(String url) throws MalformedURLException {
        URL uri = new URL(url);
        this.schema = uri.getProtocol();
        this.hostname = uri.getHost();
        this.port = uri.getPort();
        if (this.port == -1) {
            this.baseUrl = this.schema + "://" + this.hostname + "/api/v1/chatbot";
        } else {
            this.baseUrl = this.schema + "://" + this.hostname + ":" + this.port + "/api/v1/chatbot";
        }

    }

    public String getSchema() {
        return this.schema;
    }

    public String getHostname() {
        return this.hostname;
    }

    public int getPort() {
        return this.port;
    }

    public String getBaseUrl() {
        return this.baseUrl;
    }

    private HashMap<String, String> auth(String method, String path) throws Exception {
        HashMap<String, String> x = new HashMap();
        if (this.credentials != null) {
            String token = this.credentials.generate(method, path);
            x.put("Authorization", token);
        }

        return x;
    }

    public Response command(String method, String path, JSONObject payload) throws ChatbotException {
        StringBuffer url = this.getUrlPrefix();
        StringBuffer fullPath = this.getPathPrefix();
        if (StringUtils.isNotBlank(path)) {
            String[] pairs = path.split("&");
            if (pairs.length > 1 && path.contains("?")) {
                path = path + "&sdklang=java";
            } else {
                path = path + "?sdklang=java";
            }
        } else {
            path = "/?sdklang=java";
        }

        if (StringUtils.isNotBlank(path)) {
            url.append(path);
            fullPath.append(path);
        }

        JSONObject result;
        try {
            byte var8 = -1;
            switch (method.hashCode()) {
                case 70454:
                    if (method.equals("GET")) {
                        var8 = 0;
                    }
                    break;
                case 79599:
                    if (method.equals("PUT")) {
                        var8 = 3;
                    }
                    break;
                case 2461856:
                    if (method.equals("POST")) {
                        var8 = 1;
                    }
                    break;
                case 2012838315:
                    if (method.equals("DELETE")) {
                        var8 = 2;
                    }
            }

            switch (var8) {
                case 0:
                    result = RestAPI.get(url.toString(), (HashMap) null, this.auth(method, fullPath.toString()));
                    break;
                case 1:
                    if (StringUtils.startsWith(path, "/asr/recognize")) {
                        Optional<JSONObject> resultOpt = this.postAsrRecognize(url.toString(), payload, (String) null, this.auth(method, fullPath.toString()));
                        if (!resultOpt.isPresent()) {
                            throw new ChatbotException("Empty response from ASR Api.");
                        }

                        result = (JSONObject) resultOpt.get();
                    } else {
                        result = RestAPI.post(url.toString(), payload, (HashMap) null, this.auth(method, fullPath.toString()));
                    }
                    break;
                case 2:
                    result = RestAPI.delete(url.toString(), this.auth(method, fullPath.toString()));
                    break;
                case 3:
                    result = RestAPI.put(url.toString(), payload, (HashMap) null, this.auth(method, fullPath.toString()));
                    break;
                default:
                    throw new ChatbotException("Invalid requested method, only GET, POST, DELETE, PUT are supported.");
            }
        } catch (Exception var11) {
            throw new ChatbotException(var11.toString());
        }

        this.purge(result);
        Response resp = new Response();
        resp.setRc(result.getInt("rc"));
        if (result.has("error")) {
            try {
                resp.setError(result.getString("error"));
            } catch (Exception var10) {
                resp.setError(result.get("error").toString());
            }
        }

        if (result.has("msg")) {
            resp.setMsg(result.getString("msg"));
        }

        if (result.has("data")) {
            resp.setData(result.get("data"));
        }

        if (result.has("total")) {
            resp.setTotal(result.getInt("total"));
        }

        if (result.has("current_page")) {
            resp.setTotal(result.getInt("current_page"));
        }

        if (result.has("total_page")) {
            resp.setTotal(result.getInt("total_page"));
        }

        return resp;
    }

    private Optional<JSONObject> postAsrRecognize(String url, JSONObject payload, String query, HashMap<String, String> headers) throws ChatbotException {
        if (payload.has("filepath") && FileUtil.exists(payload.getString("filepath"))) {
            kong.unirest.json.JSONObject obj = ((JsonNode) ((HttpRequestWithBody) Unirest.post(url).header("Authorization", headers.containsKey("Authorization") ? (String) headers.get("Authorization") : "")).field("file", new File(payload.getString("filepath"))).field("nbest", Integer.toString(payload.has("nbest") ? payload.getInt("nbest") : 5)).field("pos", Boolean.toString(payload.has("pos") ? payload.getBoolean("pos") : false)).asJson().getBody()).getObject();
            return Optional.of(new JSONObject(obj.toString()));
        } else if (payload.has("type") && StringUtils.equalsIgnoreCase(payload.getString("type"), "base64")) {
            if (payload.has("data")) {
                String data = payload.getString("data");
                if (StringUtils.isNotBlank(data)) {
                    if (!payload.has("nbest")) {
                        payload.put("nbest", 5);
                    }

                    if (!payload.has("pos")) {
                        payload.put("pos", false);
                    }

                    return Optional.of(RestAPI.post(url, payload, (HashMap) null, headers));
                } else {
                    throw new ChatbotException("Empty data for ASR Api, base64 data is required for base64 type request.");
                }
            } else {
                throw new ChatbotException("`data` is required for base64 type request body.");
            }
        } else {
            throw new ChatbotException("Invalid body for ASR Api, filepath or `type=base64, data=xxx` are required.");
        }
    }

    public Response command(String method, String path) throws ChatbotException {
        return this.command(method, path, (JSONObject) null);
    }

    /**
     * @deprecated
     */
    public JSONObject details() throws ChatbotException {
        Response resp = this.command("GET", "/");
        return resp.toJSON();
    }

    /**
     * @deprecated
     */
    public boolean exists() throws ChatbotException {
        try {
            Response resp = this.command("GET", "/");
            if (resp.getRc() == 0) {
                return true;
            } else if (resp.getRc() == 3) {
                return false;
            } else {
                throw new ChatbotException("查询聊天机器人异常返回。");
            }
        } catch (Exception var2) {
            throw new ChatbotException(var2.toString());
        }
    }

    /**
     * @deprecated
     */
    public JSONObject conversation(String userId, String textMessage) throws ChatbotException {
        return this.conversation(userId, textMessage, 0.8D, 0.6D);
    }

    /**
     * @deprecated
     */
    public JSONObject conversation(String userId, String textMessage, double faqBestReplyThreshold, double faqSuggReplyThreshold) throws ChatbotException {
        this.v(this.clientId, userId, textMessage);
        JSONObject body = new JSONObject();
        body.put("fromUserId", userId);
        body.put("textMessage", textMessage);
        body.put("isDebug", false);
        body.put("faqBestReplyThreshold", faqBestReplyThreshold);
        body.put("faqSuggReplyThreshold", faqSuggReplyThreshold);
        Response resp = this.command("POST", "/conversation/query", body);
        return resp.toJSON();
    }

    /**
     * @deprecated
     */
    public JSONObject faq(String userId, String textMessage) throws ChatbotException {
        return this.faq(userId, textMessage, 0.8D, 0.6D);
    }

    /**
     * @deprecated
     */
    public JSONObject faq(String userId, String textMessage, double faqBestReplyThreshold, double faqSuggReplyThreshold) throws ChatbotException {
        this.v(this.clientId, userId, textMessage);
        JSONObject body = new JSONObject();
        body.put("fromUserId", userId);
        body.put("query", textMessage);
        body.put("isDebug", false);
        body.put("faqBestReplyThreshold", faqBestReplyThreshold);
        body.put("faqSuggReplyThreshold", faqSuggReplyThreshold);
        Response resp = this.command("POST", "/faq/query", body);
        return resp.toJSON();
    }

    /**
     * @deprecated
     */
    public JSONObject faqlist(String query, String category, int page, int pageSize) throws ChatbotException, UnsupportedEncodingException, UnsupportedEncodingException {
        if (page == 0) {
            page = 1;
        }

        if (pageSize == 0) {
            pageSize = 30;
        }

        StringBuffer path = new StringBuffer();
        path.append("/faq/database?page=");
        path.append(page);
        path.append("&limit=");
        path.append(pageSize);
        if (StringUtils.isNotBlank(query)) {
            path.append("&q=");
            path.append(URLEncoder.encode(query, "UTF-8"));
        }

        if (StringUtils.isNotBlank(category)) {
            path.append("&category=");
            path.append(category);
        }

        Response resp = this.command("GET", path.toString());
        return resp.toJSON();
    }

    /**
     * @deprecated
     */
    public JSONObject faqcreate(String post, String reply, boolean enabled, List<String> categories) throws ChatbotException {
        if (!StringUtils.isBlank(post) && !StringUtils.isBlank(reply)) {
            JSONObject body = new JSONObject();
            body.put("post", post);
            JSONArray replies = new JSONArray();
            JSONObject replyObj = new JSONObject();
            replyObj.put("content", reply);
            replyObj.put("rtype", "plain");
            replyObj.put("enabled", true);
            replies.put(replyObj);
            body.put("replies", replies);
            if (enabled) {
                body.put("enabled", true);
            } else {
                body.put("enabled", false);
            }

            if (categories != null && categories.size() > 0) {
                JSONArray ja = new JSONArray();

                for (int i = 0; i < categories.size(); ++i) {
                    ja.put(categories.get(i));
                }

                body.put("categories", ja);
            } else if (categories != null && categories.size() == 0) {
                body.put("categories", new JSONArray());
            }

            Response resp = this.command("POST", "/faq/database", body);
            return resp.toJSON();
        } else {
            throw new ChatbotException("Invalid post or reply");
        }
    }

    /**
     * @deprecated
     */
    public JSONObject faqdetail(String id) throws ChatbotException {
        if (StringUtils.isBlank(id)) {
            throw new ChatbotException("Invalid id");
        } else {
            StringBuffer path = new StringBuffer();
            path.append("/faq/database/");
            path.append(id);
            Response resp = this.command("GET", path.toString());
            return resp.toJSON();
        }
    }

    /**
     * @deprecated
     */
    public JSONObject faqupdate(String id, String post, String reply, boolean enabled, List<String> categories) throws ChatbotException {
        if (StringUtils.isBlank(id)) {
            throw new ChatbotException("Invalid id");
        } else {
            StringBuffer p = new StringBuffer();
            p.append("/faq/database/");
            p.append(id);
            Response prev = this.command("GET", p.toString());
            String replyLastUpdate = ((JSONObject) ((JSONObject) prev.getData())).getString("replyLastUpdate");
            StringBuffer path = new StringBuffer();
            path.append("/faq/database/");
            path.append(id);
            JSONObject obj = new JSONObject();
            obj.put("replyLastUpdate", replyLastUpdate);
            if (StringUtils.isNotBlank(post)) {
                obj.put("post", post);
            }

            JSONArray ja;
            if (StringUtils.isNotBlank(reply)) {
                ja = new JSONArray();
                JSONObject replyObj = new JSONObject();
                replyObj.put("content", reply);
                replyObj.put("rtype", "plain");
                replyObj.put("enabled", true);
                ja.put(replyObj);
                obj.put("replies", ja);
            }

            if (enabled) {
                obj.put("enabled", true);
            } else {
                obj.put("enabled", false);
            }

            if (categories != null && categories.size() > 0) {
                ja = new JSONArray();

                for (int i = 0; i < categories.size(); ++i) {
                    ja.put(categories.get(i));
                }

                obj.put("categories", ja);
            } else if (categories != null && categories.size() == 0) {
                obj.put("categories", new JSONArray());
            }

            Response resp = this.command("PUT", path.toString(), obj);
            return resp.toJSON();
        }
    }

    /**
     * @deprecated
     */
    public JSONObject faqdisable(String id) throws ChatbotException {
        if (StringUtils.isBlank(id)) {
            throw new ChatbotException("Invalid id");
        } else {
            return this.faqupdate(id, (String) null, (String) null, false, (List) null);
        }
    }

    /**
     * @deprecated
     */
    public JSONObject faqenable(String id) throws ChatbotException {
        if (StringUtils.isBlank(id)) {
            throw new ChatbotException("Invalid id");
        } else {
            return this.faqupdate(id, (String) null, (String) null, true, (List) null);
        }
    }

    /**
     * @deprecated
     */
    public JSONObject faqdelete(String id) throws ChatbotException {
        if (StringUtils.isBlank(id)) {
            throw new ChatbotException("Invalid id");
        } else {
            StringBuffer path = new StringBuffer();
            path.append("/faq/database/");
            path.append(id);
            Response resp = this.command("DELETE", path.toString());
            return resp.toJSON();
        }
    }

    /**
     * @deprecated
     */
    public JSONObject faqextend(String id) throws ChatbotException {
        if (StringUtils.isBlank(id)) {
            throw new ChatbotException("Invalid id");
        } else {
            StringBuffer path = new StringBuffer();
            path.append("/faq/database/");
            path.append(id);
            path.append("/extend");
            Response resp = this.command("GET", path.toString());
            return resp.toJSON();
        }
    }

    /**
     * @deprecated
     */
    public JSONObject faqextendcreate(String id, String post) throws ChatbotException {
        if (!StringUtils.isBlank(id) && !StringUtils.isBlank(post)) {
            StringBuffer path = new StringBuffer();
            path.append("/faq/database/");
            path.append(id);
            path.append("/extend");
            JSONObject body = new JSONObject();
            body.put("post", post);
            Response resp = this.command("POST", path.toString(), body);
            return resp.toJSON();
        } else {
            throw new ChatbotException("Invalid id or post");
        }
    }

    /**
     * @deprecated
     */
    public JSONObject faqextendupdate(String id, String extendId, String post) throws ChatbotException {
        if (!StringUtils.isBlank(id) && !StringUtils.isBlank(post) && !StringUtils.isBlank(extendId)) {
            StringBuffer path = new StringBuffer();
            path.append("/faq/database/");
            path.append(id);
            path.append("/extend/");
            path.append(extendId);
            JSONObject body = new JSONObject();
            body.put("post", post);
            Response resp = this.command("PUT", path.toString(), body);
            return resp.toJSON();
        } else {
            throw new ChatbotException("Invalid id, post or extendId");
        }
    }

    /**
     * @deprecated
     */
    public JSONObject faqextenddelete(String id, String extendId) throws ChatbotException {
        if (!StringUtils.isBlank(id) && !StringUtils.isBlank(extendId)) {
            StringBuffer path = new StringBuffer();
            path.append("/faq/database/");
            path.append(id);
            path.append("/extend/");
            path.append(extendId);
            Response resp = this.command("DELETE", path.toString());
            return resp.toJSON();
        } else {
            throw new ChatbotException("Invalid id or extendId");
        }
    }

    /**
     * @deprecated
     */
    public JSONObject faqcategories() throws ChatbotException {
        StringBuffer path = new StringBuffer();
        path.append("/faq/categories");
        Response resp = this.command("GET", path.toString());
        return resp.toJSON();
    }

    /**
     * @deprecated
     */
    public JSONObject faqcategorycreate(String label, String parentId) throws ChatbotException {
        if (StringUtils.isBlank(label)) {
            throw new ChatbotException("Invalid label");
        } else {
            StringBuffer path = this.getPathPrefix();
            path.append("/faq/categories");
            JSONObject body = new JSONObject();
            body.put("label", label);
            if (StringUtils.isNotBlank(parentId)) {
                body.put("parentId", parentId);
            }

            Response resp = this.command("POST", path.toString(), body);
            return resp.toJSON();
        }
    }

    /**
     * @deprecated
     */
    public JSONObject faqcategoryupdate(String value, String label) throws ChatbotException {
        if (!StringUtils.isBlank(label) && !StringUtils.isBlank(value)) {
            StringBuffer path = new StringBuffer();
            path.append("/faq/categories");
            JSONObject body = new JSONObject();
            body.put("label", label);
            body.put("value", value);
            Response resp = this.command("PUT", path.toString(), body);
            return resp.toJSON();
        } else {
            throw new ChatbotException("Invalid label or value");
        }
    }

    /**
     * @deprecated
     */
    public JSONObject faqcategorydelete(String value) throws ChatbotException {
        if (StringUtils.isBlank(value)) {
            throw new ChatbotException("Invalid value");
        } else {
            StringBuffer path = new StringBuffer();
            path.append("/faq/categories/");
            path.append(value);
            Response resp = this.command("DELETE", path.toString());
            return resp.toJSON();
        }
    }

    /**
     * @deprecated
     */
    public JSONObject intentsession(String userId, String channel) throws ChatbotException {
        JSONObject body = new JSONObject();
        body.put("uid", userId);
        body.put("channel", channel);
        StringBuffer path = this.getPathPrefix();
        path.append("/clause/prover/session");
        Response resp = this.command("POST", path.toString(), body);
        return resp.toJSON();
    }

    /**
     * @deprecated
     */
    public JSONObject intentsession(String sessionId) throws ChatbotException {
        StringBuffer path = new StringBuffer();
        path.append("/clause/prover/session/");
        path.append(sessionId);
        Response resp = this.command("GET", path.toString());
        return resp.toJSON();
    }

    /**
     * @deprecated
     */
    public JSONObject intent(String sessionId, String userId, String textMessage) throws ChatbotException {
        if (StringUtils.isBlank(sessionId)) {
            throw new ChatbotException("[intent] 不合法的会话ID。");
        } else if (StringUtils.isBlank(userId)) {
            throw new ChatbotException("[intent] 不合法的用户标识。");
        } else if (StringUtils.isBlank(textMessage)) {
            throw new ChatbotException("[intent] 不合法的消息内容。");
        } else {
            JSONObject body = new JSONObject();
            body.put("fromUserId", userId);
            JSONObject session = new JSONObject();
            session.put("id", sessionId);
            JSONObject message = new JSONObject();
            message.put("textMessage", textMessage);
            body.put("session", session);
            body.put("message", message);
            StringBuffer path = new StringBuffer();
            path.append("/clause/prover/chat");
            Response resp = this.command("POST", path.toString(), body);
            return resp.toJSON();
        }
    }

    /**
     * @deprecated
     */
    public JSONObject users(int page, int pageSize) throws ChatbotException {
        if (page == 0) {
            page = 1;
        }

        if (pageSize == 0) {
            pageSize = 30;
        }

        StringBuffer path = new StringBuffer();
        path.append("/users?page=");
        path.append(page);
        path.append("&limit=");
        path.append(pageSize);
        path.append("&sortby=-lasttime");
        Response resp = this.command("GET", path.toString(), (JSONObject) null);
        return resp.toJSON();
    }

    /**
     * @deprecated
     */
    public JSONObject chats(String userId, int page, int pageSize) throws ChatbotException {
        if (page == 0) {
            page = 1;
        }

        if (pageSize == 0) {
            pageSize = 30;
        }

        StringBuffer path = this.getPathPrefix();
        path.append("/users/");
        path.append(userId);
        path.append("/chats?page=");
        path.append(page);
        path.append("&limit=");
        path.append(pageSize);
        path.append("&sortby=-lasttime");
        Response resp = this.command("GET", path.toString());
        return resp.toJSON();
    }

    /**
     * @deprecated
     */
    public void mute(String userId) throws ChatbotException {
        StringBuffer path = new StringBuffer();
        path.append("/users/");
        path.append(userId);
        path.append("/mute");
        Response resp = this.command("POST", path.toString());
        if (resp.getRc() != 0) {
            throw new ChatbotException("Unable to mute user, bad response from server.");
        }
    }

    /**
     * @deprecated
     */
    public void unmute(String userId) throws ChatbotException {
        StringBuffer path = new StringBuffer();
        path.append("/users/");
        path.append(userId);
        path.append("/unmute");
        Response resp = this.command("POST", path.toString());
        if (resp.getRc() != 0) {
            throw new ChatbotException("Unable to unmute user, bad response from server.");
        }
    }

    /**
     * @deprecated
     */
    public boolean ismute(String userId) throws ChatbotException {
        StringBuffer path = new StringBuffer();
        path.append("/users/");
        path.append(userId);
        path.append("/ismute");
        Response resp = this.command("POST", path.toString());
        if (resp.getRc() != 0) {
            throw new ChatbotException("Unable to check mute status, bad response from server.");
        } else {
            return ((JSONObject) resp.getData()).getBoolean("mute");
        }
    }

    private void purge(JSONObject j) {
        if (j.getInt("rc") == 0 && j.has("data")) {
            Object data = j.get("data");
            if (data instanceof JSONObject) {
                ((JSONObject) data).remove("chatbotID");
            } else if (data instanceof JSONArray) {
                for (int i = 0; i < ((JSONArray) data).length(); ++i) {
                    ((JSONObject) ((JSONArray) data).get(i)).remove("chatbotID");
                }
            }
        }

    }

    private void v(String chatbotID, String fromUserId, String textMessage) throws ChatbotException {
        if (StringUtils.isBlank(chatbotID)) {
            throw new ChatbotException("[conversation] 不合法的聊天机器人标识。");
        } else if (StringUtils.isBlank(fromUserId)) {
            throw new ChatbotException("[conversation] 不合法的用户标识。");
        } else if (StringUtils.isBlank(textMessage)) {
            throw new ChatbotException("[conversation] 不合法的消息内容。");
        }
    }

    private StringBuffer getUrlPrefix() {
        StringBuffer sb = new StringBuffer();
        return sb.append(this.getBaseUrl()).append("/").append(this.clientId);
    }

    private StringBuffer getPathPrefix() {
        StringBuffer sb = new StringBuffer();
        return sb.append("/api/v1/chatbot").append("/").append(this.clientId);
    }
}

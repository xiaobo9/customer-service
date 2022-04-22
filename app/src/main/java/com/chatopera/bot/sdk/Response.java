package com.chatopera.bot.sdk;

import com.chatopera.bot.exception.ChatbotException;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class Response {
    private int rc;
    private String msg;
    private String error;
    private JSONObject dataObj;
    private JSONArray dataArray;
    private int total = -1;
    private int current_page = -1;
    private int total_page = -1;

    public Response() {
    }

    public int getRc() {
        return this.rc;
    }

    public void setRc(int rc) {
        this.rc = rc;
    }

    public String getMsg() {
        return this.msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public String getError() {
        return this.error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public Object getData() {
        if (this.dataObj != null) {
            return this.dataObj;
        } else {
            return this.dataArray != null ? this.dataArray : null;
        }
    }

    public <T> void setData(T data) throws ChatbotException {
        if (data instanceof String) {
            String data2 = (String) data;

            try {
                if (StringUtils.startsWith(data2, "[")) {
                    if (this.dataObj != null) {
                        throw new ChatbotException("Invalid data operations for data property.");
                    }

                    this.dataArray = new JSONArray(data);
                } else if (StringUtils.startsWith(data2, "{")) {
                    if (this.dataArray != null) {
                        throw new ChatbotException("Invalid data operations for data property.");
                    }

                    this.dataObj = new JSONObject(data);
                }
            } catch (JSONException var4) {
                throw new ChatbotException("Unable to parser input as JSON.");
            }
        } else if (data instanceof JSONObject) {
            if (this.dataArray != null) {
                throw new ChatbotException("Invalid data operations for data property.");
            }

            this.dataObj = (JSONObject) data;
        } else {
            if (!(data instanceof JSONArray)) {
                throw new ChatbotException("Unknown class type as JSON.");
            }

            if (this.dataObj != null) {
                throw new ChatbotException("Invalid data operations for data property.");
            }

            this.dataArray = (JSONArray) data;
        }

    }

    public int getTotal() {
        return this.total;
    }

    public void setTotal(int total) {
        this.total = total;
    }

    public int getCurrent_page() {
        return this.current_page;
    }

    public void setCurrent_page(int current_page) {
        this.current_page = current_page;
    }

    public int getTotal_page() {
        return this.total_page;
    }

    public void setTotal_page(int total_page) {
        this.total_page = total_page;
    }

    public JSONObject toJSON() {
        JSONObject obj = new JSONObject();
        obj.put("rc", this.rc);
        obj.put("data", this.getData());
        if (StringUtils.isNotBlank(this.msg)) {
            obj.put("msg", this.msg);
        }

        if (StringUtils.isNotBlank(this.error)) {
            obj.put("error", this.error);
        }

        if (this.total >= 0) {
            obj.put("total", this.total);
        }

        if (this.current_page >= 0) {
            obj.put("current_page", this.current_page);
        }

        if (this.total_page >= 0) {
            obj.put("total_page", this.total_page);
        }

        return obj;
    }
}

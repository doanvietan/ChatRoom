package com.chatapp.service;

import com.chatapp.util.Config;
import com.google.api.client.http.*;
import com.google.api.client.http.javanet.NetHttpTransport;
import org.json.JSONObject;
import org.json.JSONArray;

import java.io.IOException;

public class AIService {
    private static final HttpTransport HTTP_TRANSPORT = new NetHttpTransport();

    public String getAIResponse(String query) {
        try {
            // Khởi tạo URL gốc
            GenericUrl url = new GenericUrl(Config.GEMINI_API_URL);
            // Gắn API Key thông qua tham số query một cách an toàn để tránh lỗi encode dấu "?"
            url.put("key", Config.API_KEY);

            // --- Giữ nguyên toàn bộ cấu trúc JSON chuẩn bạn vừa sửa ---
            JSONObject textPart = new JSONObject();
            textPart.put("text", query);

            JSONObject contentObj = new JSONObject();
            contentObj.put("role", "user");
            contentObj.put("parts", new JSONArray().put(textPart));

            JSONObject requestBody = new JSONObject();
            requestBody.put("contents", new JSONArray().put(contentObj));

            HttpRequestFactory requestFactory = HTTP_TRANSPORT.createRequestFactory();

            HttpRequest request = requestFactory.buildPostRequest(url,
                    ByteArrayContent.fromString("application/json", requestBody.toString()));
            request.getHeaders().setContentType("application/json");

            HttpResponse response = request.execute();
            String result = response.parseAsString();

            JSONObject json = new JSONObject(result);

            if (json.has("candidates") && !json.getJSONArray("candidates").isEmpty()) {
                return json.getJSONArray("candidates")
                        .getJSONObject(0)
                        .getJSONObject("content")
                        .getJSONArray("parts")
                        .getJSONObject(0)
                        .getString("text");
            } else {
                return "AI không phản hồi (Empty response).";
            }

        } catch (HttpResponseException e) {
            // IN CHI TIẾT LỖI RA CONSOLE: Dòng này cực kỳ quan trọng để debug xem Google chê điểm nào
            System.err.println("Chi tiết lỗi từ Google Server: " + e.getContent());

            if (e.getStatusCode() == 429) {
                return "AI đang quá tải (Too Many Requests). Vui lòng đợi 1 phút rồi thử lại.";
            } else if (e.getStatusCode() == 404) {
                return "Lỗi cấu hình Model AI (404). Vui lòng kiểm tra lại Server.";
            } else if (e.getStatusCode() == 400) {
                return "Yêu cầu không hợp lệ (Bad Request).";
            }
            e.printStackTrace();
            return "Lỗi từ phía Google AI: " + e.getStatusMessage();
        } catch (IOException e) {
            e.printStackTrace();
            return "Lỗi kết nối mạng đến AI Server.";
        } catch (Exception e) {
            e.printStackTrace();
            return "Lỗi không xác định khi xử lý AI.";
        }
    }
}
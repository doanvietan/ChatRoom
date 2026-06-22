package com.chatapp.util;

public class Config {
    // API Key của bạn (giữ nguyên)
    private static final String API_KEY = "AIzaSyCbPBhWI_dSbN6BwaF1ldr1mmDLuXw1k8I";

    // Sử dụng model: gemini-2.0-flash (Lấy từ danh sách log của bạn)
    public static final String GEMINI_API_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=" + API_KEY;
}
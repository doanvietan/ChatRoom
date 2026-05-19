package com.chatapp.util;

public class Config {
    // API Key của bạn (giữ nguyên)
    private static final String API_KEY = "AIzaSyD1tTmkM2bBr745r_lmQW8nDe-luWigL14";

    // Sử dụng model: gemini-2.0-flash (Lấy từ danh sách log của bạn)
    public static final String GEMINI_API_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=" + API_KEY;
}
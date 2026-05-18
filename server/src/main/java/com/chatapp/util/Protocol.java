package com.chatapp.util;

public class Protocol {
    public static final String
            LOGIN = "LOGIN",
            REGISTER = "REGISTER",
            GET_USERS = "GET_USERS",
            GET_ALL_USERS = "GET_ALL_USERS",
            GET_ALL_ROOMS = "GET_ALL_ROOMS",
            START_PRIVATE_CHAT = "START_PRIVATE_CHAT",
            JOIN_ROOM = "JOIN_ROOM",
            GET_ROOMS = "GET_ROOMS",
            SEND_MESSAGE = "SEND_MESSAGE",
            GET_HISTORY = "GET_HISTORY",

    // --- VIDEO CALL PROTOCOLS ---
    START_VIDEO_CALL = "START_VIDEO_CALL",
            INCOMING_VIDEO_CALL = "INCOMING_VIDEO_CALL",
            OK_VIDEO_CALL_STARTED = "OK_VIDEO_CALL_STARTED",
            VIDEO_DATA = "VIDEO_DATA", // Dùng để gửi gói tin hình ảnh
            END_CALL = "END_CALL";
}
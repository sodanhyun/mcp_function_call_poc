package com.example.gemini_report.config;

public class UserContextHolder {
    private static final ThreadLocal<String> currentUser = new ThreadLocal<>();

    public static void setUserName(String userName) {
        currentUser.set(userName);
    }

    public static String getUserName() {
        return currentUser.get();
    }

    public static void clear() {
        currentUser.remove();
    }
}
package com.bharat.stockline.model;

public record User(long id, String email, String name, String role) {
    public boolean isAdmin() {
        return "ADMIN".equals(role);
    }
}

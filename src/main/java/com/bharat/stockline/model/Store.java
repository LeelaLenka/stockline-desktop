package com.bharat.stockline.model;

public record Store(long id, String name, String city, String address) {
    @Override
    public String toString() {
        return name + ", " + city; // shown in drop-downs
    }
}

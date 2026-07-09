package com.aakriti.registration.model;

public enum EventCategory {
    SPORTS("Sports"),
    CULTURALS("Culturals"),
    MANAGEMENT("Management"),
    COMBO("Combo");

    private final String sheetTabName;
    EventCategory(String sheetTabName) { this.sheetTabName = sheetTabName; }
    public String getSheetTabName() { return sheetTabName; }
}

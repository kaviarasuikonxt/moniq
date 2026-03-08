package com.moniq.api.parsing.format;

import org.springframework.stereotype.Component;

@Component
public class ReceiptFormatDetector {

    public ReceiptFormat detect(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return ReceiptFormat.UNKNOWN;
        }

        String text = rawText.toUpperCase();

        if (containsAny(text,
                "NTUC", "FAIRPRICE", "GIANT", "COLD STORAGE", "SHENG SIONG", "PRIME",
                "U STARS", "USTAR", "FAIRPRICE CO-OPERATIVE")) {
            return ReceiptFormat.SUPERMARKET;
        }

        if (containsAny(text,
                "SWEE HENG", "ROTIBOY", "BREADTALK", "FOUR LEAVES")) {
            return ReceiptFormat.BAKERY;
        }

        if (containsAny(text,
                "7-ELEVEN", "7 ELEVEN", "CHEERS")) {
            return ReceiptFormat.CONVENIENCE_STORE;
        }

        if (containsAny(text,
                "GUARDIAN", "WATSONS", "UNITY")) {
            return ReceiptFormat.PHARMACY;
        }

        if (containsAny(text,
                "ESSO", "SHELL", "CALTEX", "SPC", "SINOPEC", "SERVICE STATION")) {
            return ReceiptFormat.GAS_STATION;
        }

        if (looksLikeWeightedItems(text)) {
            return ReceiptFormat.MARKET_WEIGHTED_ITEMS;
        }

        if (looksLikeRestaurant(text)) {
            return ReceiptFormat.RESTAURANT;
        }

        if (looksLikeHawker(text)) {
            return ReceiptFormat.HAWKER;
        }

        if (looksLikeRetailGeneral(text)) {
            return ReceiptFormat.RETAIL_GENERAL;
        }

        return ReceiptFormat.UNKNOWN;
    }

    private boolean looksLikeWeightedItems(String text) {
        return (
                containsAny(text, "KG", "KGS", "/KG", "WEIGHT")
                        || text.contains("0.")
                        || text.matches("(?s).*\\d+\\.\\d+KG.*")
        ) && containsAny(text, "PRICE", "TOTAL", "AMOUNT", "QTY", "DESCRIPTION");
    }

    private boolean looksLikeRestaurant(String text) {
        return containsAny(text,
                "DINE IN", "TAKEAWAY", "TAKE AWAY", "SET MEAL", "COMBO",
                "TABLE", "PAX", "SERVICE CHARGE", "SUBTOTAL")
                || containsAny(text,
                "MCDONALD", "KFC", "STARBUCKS", "YA KUN", "TOAST BOX", "BURGER KING",
                "COFFEE BEAN", "SUBWAY", "JOLLIBEE");
    }

    private boolean looksLikeHawker(String text) {
        return containsAny(text,
                "STALL", "COFFEE SHOP", "FOOD CENTRE", "HAWKER", "KOPI", "MEE", "NASI", "TEH")
                && !containsAny(text, "SERVICE CHARGE", "TABLE NO");
    }

    private boolean looksLikeRetailGeneral(String text) {
        return containsAny(text,
                "FASHION", "APPAREL", "CLOTHING", "HOUSEHOLD", "DEPARTMENT",
                "TRADING", "SUPPLIES", "PTE LTD", "PTE. LTD", "STORE", "SHOP");
    }

    private boolean containsAny(String text, String... values) {
        for (String value : values) {
            if (text.contains(value)) {
                return true;
            }
        }
        return false;
    }
}
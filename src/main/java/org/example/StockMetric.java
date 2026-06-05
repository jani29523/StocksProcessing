package org.example;

public class StockMetric {

    private final String label;
    private final String value;

    public StockMetric(String label, String value) {
        this.label = label == null ? null : label.replace("?", "").trim();
        this.value = value;
    }

    public String getLabel() {
        return label;
    }

    public String getValue() {
        return value;
    }

    @Override
    public String toString() {
        return label + " = " + value;
    }
}


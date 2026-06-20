package org.example;

public class MutualFundMetric {

    private String fundName;
    private String date;
    private String value;

    public String getFundName() { return fundName; }
    public void setFundName(String fundName) { this.fundName = fundName; }

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }

    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }

    @Override
    public String toString() {
        return "MutualFundMetric{fund=" + fundName + ", date=" + date + ", value=" + value + '}';
    }
}
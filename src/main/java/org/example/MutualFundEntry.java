package org.example;

public class MutualFundEntry {

    private String mutualFundName;
    private String mutualFundLink;
    private String fundType;
    private String expenseRatio;
    private String fundSize;

    public String getMutualFundName() { return mutualFundName; }
    public void setMutualFundName(String mutualFundName) { this.mutualFundName = mutualFundName; }

    public String getMutualFundLink() { return mutualFundLink; }
    public void setMutualFundLink(String mutualFundLink) { this.mutualFundLink = mutualFundLink; }

    public String getFundType() { return fundType; }
    public void setFundType(String fundType) { this.fundType = fundType; }

    public String getExpenseRatio() { return expenseRatio; }
    public void setExpenseRatio(String expenseRatio) { this.expenseRatio = expenseRatio; }

    public String getFundSize() { return fundSize; }
    public void setFundSize(String fundSize) { this.fundSize = fundSize; }

    private final java.util.Map<String, String> properties = new java.util.LinkedHashMap<>();

    public void addProperty(String key, String value) { properties.put(key, value); }
    public java.util.Map<String, String> getProperties() { return java.util.Collections.unmodifiableMap(properties); }

    @Override
    public String toString() {
        return "MutualFundEntry{name=" + mutualFundName + ", link=" + mutualFundLink + '}';
    }
}
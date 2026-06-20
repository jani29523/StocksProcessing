package org.example;

public class FundProperty {

    private String fundName;
    private String propertyName;
    private String propertyValue;

    public FundProperty(String fundName, String propertyName, String propertyValue) {
        this.fundName      = fundName;
        this.propertyName  = propertyName;
        this.propertyValue = propertyValue;
    }

    public String getFundName()  { return fundName; }
    public void   setFundName(String fundName)  { this.fundName = fundName; }

    public String getPropertyName()  { return propertyName; }
    public void   setPropertyName(String propertyName)  { this.propertyName  = propertyName; }

    public String getPropertyValue() { return propertyValue; }
    public void   setPropertyValue(String propertyValue) { this.propertyValue = propertyValue; }

    @Override
    public String toString() {
        return "FundProperty{fund=" + fundName + ", " + propertyName + " = " + propertyValue + '}';
    }
}
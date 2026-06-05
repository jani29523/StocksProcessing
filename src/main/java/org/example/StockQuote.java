package org.example;

public class StockQuote {

    private String key;
    private String name;
    private String change;
    private String changePercentage;
    private String tradedValuePercentage;
    private String prevClose;
    private String open;
    private String high;
    private String low;
    private String close;
    private String vwap;
    private String adjustedPrice;
    private String tradedVolumeLakhs;
    private String tradedValueCr;
    private String totalMarketCapCr;
    private String freeFloatMarketCapCr;
    private String impactCost;
    private String faceValue;
    private String applicableMarginRate;
    private String deliverableTradedQuantityPct;
    private String weekHigh52;
    private String weekLow52;
    private String upperBand;
    private String lowerBand;
    private String priceBandPct;
    private String tickSize;
    private String dailyVolatility;
    private String annualisedVolatility;

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getChange() { return change; }
    public void setChange(String change) {
        try {
            double closeVal = parseNumeric(this.close);
            double openVal = parseNumeric(this.open);
            this.change = String.format("%.2f", closeVal - openVal);
            System.out.println("Change***************: " + this.change);
        } catch (NumberFormatException e) {
            this.change = "0";
        }
    }

    public String getChangePercentage() { return changePercentage; }
    public void setChangePercentage(String changePercentage) {
        try {
            double closeVal = parseNumeric(this.close);
            double openVal = parseNumeric(this.open);
            if (openVal == 0d) {
                this.changePercentage = "0%";
                return;
            }
            double pct = ((closeVal - openVal) / openVal) * 100d;
            this.changePercentage = String.format("%.2f%%", pct);
            System.out.println("Change Percentage***************: " + this.changePercentage
                    + " (close=" + this.close + ", open=" + this.open + ")");
        } catch (NumberFormatException e) {
            this.changePercentage = "0%";
        }
    }

    public String getTradedValuePercentage() { return tradedValuePercentage; }
    public void setTradedValuePercentage(String tradedValuePercentage) {
        try {
            double trdValue = parseNumeric(this.tradedValueCr);
            double capitalValue = parseNumeric(this.freeFloatMarketCapCr);
            if (trdValue == 0d) {
                this.tradedValuePercentage = "0%";
                return;
            }
            double pct = (trdValue / capitalValue) * 100d;
            this.tradedValuePercentage = String.format("%.2f%%", pct);
            System.out.println("Change Percentage***************: " + this.tradedValuePercentage
                    + " (tradedValueCr=" + this.tradedValueCr
                    + ", freeFloatMarketCapCr=" + this.freeFloatMarketCapCr + ")");
        } catch (NumberFormatException e) {
            this.tradedValuePercentage = "0%";
        }
    }

    private static double parseNumeric(String s) {
        if (s == null || s.isBlank()) {
            return 0d;
        }
        String cleaned = s.replace(",", "").replace("%", "").trim();
        return Double.parseDouble(cleaned);
    }

    public String getPrevClose() { return prevClose; }
    public void setPrevClose(String prevClose) { this.prevClose = prevClose; }

    public String getOpen() { return open; }
    public void setOpen(String open) { this.open = open; }

    public String getHigh() { return high; }
    public void setHigh(String high) { this.high = high; }

    public String getLow() { return low; }
    public void setLow(String low) { this.low = low; }

    public String getClose() { return close; }
    public void setClose(String close) { this.close = close; }

    public String getVwap() { return vwap; }
    public void setVwap(String vwap) { this.vwap = vwap; }

    public String getAdjustedPrice() { return adjustedPrice; }
    public void setAdjustedPrice(String adjustedPrice) { this.adjustedPrice = adjustedPrice; }

    public String getTradedVolumeLakhs() { return tradedVolumeLakhs; }
    public void setTradedVolumeLakhs(String tradedVolumeLakhs) { this.tradedVolumeLakhs = tradedVolumeLakhs; }

    public String getTradedValueCr() { return tradedValueCr; }
    public void setTradedValueCr(String tradedValueCr) { this.tradedValueCr = tradedValueCr; }

    public String getTotalMarketCapCr() { return totalMarketCapCr; }
    public void setTotalMarketCapCr(String totalMarketCapCr) { this.totalMarketCapCr = totalMarketCapCr; }

    public String getFreeFloatMarketCapCr() { return freeFloatMarketCapCr; }
    public void setFreeFloatMarketCapCr(String freeFloatMarketCapCr) { this.freeFloatMarketCapCr = freeFloatMarketCapCr; }

    public String getImpactCost() { return impactCost; }
    public void setImpactCost(String impactCost) { this.impactCost = impactCost; }

    public String getFaceValue() { return faceValue; }
    public void setFaceValue(String faceValue) { this.faceValue = faceValue; }

    public String getApplicableMarginRate() { return applicableMarginRate; }
    public void setApplicableMarginRate(String applicableMarginRate) { this.applicableMarginRate = applicableMarginRate; }

    public String getDeliverableTradedQuantityPct() { return deliverableTradedQuantityPct; }
    public void setDeliverableTradedQuantityPct(String deliverableTradedQuantityPct) { this.deliverableTradedQuantityPct = deliverableTradedQuantityPct; }

    public String getWeekHigh52() { return weekHigh52; }
    public void setWeekHigh52(String weekHigh52) { this.weekHigh52 = weekHigh52; }

    public String getWeekLow52() { return weekLow52; }
    public void setWeekLow52(String weekLow52) { this.weekLow52 = weekLow52; }

    public String getUpperBand() { return upperBand; }
    public void setUpperBand(String upperBand) { this.upperBand = upperBand; }

    public String getLowerBand() { return lowerBand; }
    public void setLowerBand(String lowerBand) { this.lowerBand = lowerBand; }

    public String getPriceBandPct() { return priceBandPct; }
    public void setPriceBandPct(String priceBandPct) { this.priceBandPct = priceBandPct; }

    public String getTickSize() { return tickSize; }
    public void setTickSize(String tickSize) { this.tickSize = tickSize; }

    public String getDailyVolatility() { return dailyVolatility; }
    public void setDailyVolatility(String dailyVolatility) { this.dailyVolatility = dailyVolatility; }

    public String getAnnualisedVolatility() { return annualisedVolatility; }
    public void setAnnualisedVolatility(String annualisedVolatility) { this.annualisedVolatility = annualisedVolatility; }

    @Override
    public String toString() {
        return "StockQuote{"
                + "key=" + key
                + ", name=" + name
                + ", prevClose=" + prevClose
                + ", open=" + open
                + ", high=" + high
                + ", low=" + low
                + ", close=" + close
                + ", vwap=" + vwap
                + ", adjustedPrice=" + adjustedPrice
                + ", tradedVolumeLakhs=" + tradedVolumeLakhs
                + ", tradedValueCr=" + tradedValueCr
                + ", totalMarketCapCr=" + totalMarketCapCr
                + ", freeFloatMarketCapCr=" + freeFloatMarketCapCr
                + ", impactCost=" + impactCost
                + ", faceValue=" + faceValue
                + ", applicableMarginRate=" + applicableMarginRate
                + ", deliverableTradedQuantityPct=" + deliverableTradedQuantityPct
                + ", weekHigh52=" + weekHigh52
                + ", weekLow52=" + weekLow52
                + ", upperBand=" + upperBand
                + ", lowerBand=" + lowerBand
                + ", priceBandPct=" + priceBandPct
                + ", tickSize=" + tickSize
                + ", dailyVolatility=" + dailyVolatility
                + ", annualisedVolatility=" + annualisedVolatility
                + '}';
    }
}
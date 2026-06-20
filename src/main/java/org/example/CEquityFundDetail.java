package org.example;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Vector;

/**
 * POJO that aggregates every scraped attribute for one equity mutual fund URL.
 *
 * Multi-row sections (Top Stock Holdings, Sector Holdings, Fund Details strip-table)
 * are stored as Vectors so callers can iterate each row independently.
 * All other properties are keyed by their property name; blank-named properties
 * fall back to "Key-1", "Key-2", … "Key-N".
 */
public class CEquityFundDetail {

    // ── Identity ─────────────────────────────────────────────────────────────
    private String fundName;
    private String fundUrl;

    // ── Known single-value metrics ────────────────────────────────────────────
    private String fundCategory;
    private String expenseRatio;
    private String fundSize;
    private String schemeInfo;       // "Scheme Info"    grouped row
    private String classification;   // "Classification" grouped row

    // ── Multi-row sections ────────────────────────────────────────────────────
    private final Vector<String> fundDetails      = new Vector<>();  // strip-table rows
    private final Vector<String> topStockHoldings = new Vector<>();
    private final Vector<String> sectorHoldings   = new Vector<>();

    // ── Catch-all (blank keys → Key-N, duplicates merged) ────────────────────
    private final Map<String, String> otherProperties = new LinkedHashMap<>();

    // ── Constructor ───────────────────────────────────────────────────────────
    public CEquityFundDetail(String fundName, String fundUrl,
                             String fundCategory, String expenseRatio, String fundSize) {
        this.fundName     = fundName;
        this.fundUrl      = fundUrl;
        this.fundCategory = fundCategory;
        this.expenseRatio = expenseRatio;
        this.fundSize     = fundSize;
    }

    // ── Bulk loader from collected FundProperty list ──────────────────────────
    public void loadFrom(Vector<FundProperty> props) {
        int keyCounter = 1;
        for (FundProperty fp : props) {
            String name  = fp.getPropertyName()  == null ? "" : fp.getPropertyName().trim();
            String value = fp.getPropertyValue() == null ? "" : fp.getPropertyValue().trim();

            if (name.isBlank()) {
                otherProperties.put("Key-" + keyCounter++, value);
                continue;
            }

            switch (name.toLowerCase()) {
                case "scheme info"            -> schemeInfo = value;
                case "classification"         -> classification = value;
                case "fund details"           -> fundDetails.add(value);
                case "top stock holdings"     -> topStockHoldings.add(value);
                case "sector holdings in mf"  -> sectorHoldings.add(value);
                default -> otherProperties.merge(name, value, (prev, v) -> prev + " || " + v);
            }
        }
    }

    // ── Getters ───────────────────────────────────────────────────────────────
    public String getFundName()                     { return fundName; }
    public String getFundUrl()                      { return fundUrl; }
    public String getFundCategory()                 { return fundCategory; }
    public String getExpenseRatio()                 { return expenseRatio; }
    public String getFundSize()                     { return fundSize; }
    public String getSchemeInfo()                   { return schemeInfo; }
    public String getClassification()               { return classification; }
    public Vector<String> getFundDetails()          { return fundDetails; }
    public Vector<String> getTopStockHoldings()     { return topStockHoldings; }
    public Vector<String> getSectorHoldings()       { return sectorHoldings; }
    public Map<String, String> getOtherProperties() { return otherProperties; }

    // ── Setters (single-value fields only) ───────────────────────────────────
    public void setFundName(String v)      { fundName      = v; }
    public void setFundUrl(String v)       { fundUrl       = v; }
    public void setFundCategory(String v)  { fundCategory  = v; }
    public void setExpenseRatio(String v)  { expenseRatio  = v; }
    public void setFundSize(String v)      { fundSize      = v; }
    public void setSchemeInfo(String v)    { schemeInfo    = v; }
    public void setClassification(String v){ classification = v; }

    @Override
    public String toString() {
        return "CEquityFundDetail{name='" + fundName + '\''
                + ", category='" + fundCategory + '\''
                + ", expenseRatio='" + expenseRatio + '\''
                + ", fundSize='" + fundSize + '\''
                + ", fundDetails=" + fundDetails.size()
                + ", stocks=" + topStockHoldings.size()
                + ", sectors=" + sectorHoldings.size()
                + ", other=" + otherProperties.size() + '}';
    }
}
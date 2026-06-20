package org.example;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Comprehensive fundamental analysis POJO for Indian stock market (NSE/BSE).
 *
 * Usage:
 *   CStockFundamentalAnalysis stock = new CStockFundamentalAnalysis("INFY", "Infosys Ltd", SectorType.IT);
 *   // set all relevant fields via setters
 *   stock.performFullAnalysis();
 *   boolean sound = stock.isFundamentallySound();
 *
 * For batch processing:
 *   List<CStockFundamentalAnalysis> results = CStockFundamentalAnalysis.analyzeAll(stocks);
 *   List<CStockFundamentalAnalysis> sound   = CStockFundamentalAnalysis.filterFundamentallySoundStocks(stocks);
 *
 * Score breakdown (max 100):
 *   Valuation         25 pts  (P/E vs sector, P/B, Graham Number, PEG)
 *   Profitability     25 pts  (ROE, ROCE, Net Margin, EBITDA Margin)
 *   Growth            20 pts  (Revenue CAGR 3Y, EPS CAGR 3Y, Net Profit Growth)
 *   Financial Health  20 pts  (D/E, Current Ratio, Interest Coverage; or GNPA/CAR/NIM for banks)
 *   Cash Flow         10 pts  (FCF, OCF quality, earnings quality ratio)
 *   Ownership         10 pts  (Promoter holding, pledge, FII/DII)
 *   Piotroski          bonus  (±3 pts based on F-Score)
 *
 * Hard-fail overrides cap the final score regardless of other criteria.
 *
 * Status thresholds:
 *   75-100  STRONG_BUY   fundamentallySound = true
 *   60-74   BUY          fundamentallySound = true
 *   40-59   NEUTRAL      fundamentallySound = false
 *   25-39   AVOID        fundamentallySound = false
 *   0-24    SELL         fundamentallySound = false
 */
public class CStockFundamentalAnalysis {

    // ─────────────────────────────────────────────────────────────────────────
    // Enumerations
    // ─────────────────────────────────────────────────────────────────────────

    public enum SectorType {
        BANKING, NBFC, IT, PHARMA, FMCG, AUTO, INFRASTRUCTURE,
        REAL_ESTATE, METAL_MINING, OIL_GAS, TELECOM, POWER,
        CHEMICALS, CEMENT, TEXTILE, CAPITAL_GOODS, RETAIL,
        HOSPITALITY, MEDIA, GENERAL
    }

    public enum MarketCapCategory {
        LARGE_CAP,   // > ₹20,000 Cr
        MID_CAP,     // ₹5,000 – ₹20,000 Cr
        SMALL_CAP,   // ₹500 – ₹5,000 Cr
        MICRO_CAP    // < ₹500 Cr
    }

    public enum FundamentalStatus {
        STRONG_BUY, BUY, NEUTRAL, AVOID, SELL
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Identity & Classification
    // ─────────────────────────────────────────────────────────────────────────

    private String stockSymbol;
    private String companyName;
    private SectorType sectorType;
    private String industry;
    private MarketCapCategory marketCapCategory;
    private double marketCapCr;      // in Indian Rupee Crores
    private String exchange;         // "NSE" or "BSE"
    private String isinCode;
    private String nseCode;
    private String bseCode;

    // ─────────────────────────────────────────────────────────────────────────
    // Market Price Data
    // ─────────────────────────────────────────────────────────────────────────

    private double currentPrice;
    private double fiftyTwoWeekHigh;
    private double fiftyTwoWeekLow;
    private double faceValue;

    // ─────────────────────────────────────────────────────────────────────────
    // Per-Share Metrics
    // ─────────────────────────────────────────────────────────────────────────

    private double eps;                  // Earnings Per Share TTM
    private double epsPreviousYear;
    private double bookValuePerShare;    // BVPS
    private double salesPerShare;
    private double cashPerShare;
    private double fcfPerShare;          // Free Cash Flow Per Share

    // ─────────────────────────────────────────────────────────────────────────
    // Valuation Metrics
    // ─────────────────────────────────────────────────────────────────────────

    private double peRatio;              // Price / EPS (TTM)
    private double sectorPeRatio;        // Sector average P/E — used for relative comparison
    private double pbRatio;              // Price / Book Value
    private double psRatio;              // Price / Sales
    private double evEbitda;             // Enterprise Value / EBITDA
    private double pegRatio;             // P/E / EPS Growth Rate
    private double grahamNumber;         // Pre-computed; or computed via computeDerivedMetrics()
    private double dividendYield;        // % (DPS / Current Price × 100)
    private double enterpriseValueCr;    // in Crores

    // ─────────────────────────────────────────────────────────────────────────
    // Income Statement (in ₹ Crores)
    // ─────────────────────────────────────────────────────────────────────────

    private double revenueTTM;
    private double revenuePreviousYear;
    private double revenue3YearsAgo;
    private double revenue5YearsAgo;
    private double ebitdaTTM;            // Operating Profit
    private double netProfitTTM;
    private double netProfitPreviousYear;
    private double grossProfitTTM;
    private double interestExpenseTTM;
    private double taxRatePercent;

    // ─────────────────────────────────────────────────────────────────────────
    // Balance Sheet (in ₹ Crores)
    // ─────────────────────────────────────────────────────────────────────────

    private double totalAssets;
    private double totalDebt;            // Short-term + Long-term
    private double longTermDebt;
    private double shortTermDebt;
    private double shareholdersEquity;
    private double totalCapitalEmployed; // Equity + Long-term Debt
    private double currentAssets;
    private double currentLiabilities;
    private double inventory;
    private double accountsReceivable;
    private double cashAndEquivalents;
    private double retainedEarnings;

    // ─────────────────────────────────────────────────────────────────────────
    // Profitability Metrics (%)
    // ─────────────────────────────────────────────────────────────────────────

    private double roe;                        // Return on Equity %
    private double roePreviousYear;
    private double roce;                       // Return on Capital Employed %
    private double roa;                        // Return on Assets %
    private double roaPreviousYear;            // for Piotroski F4 / F3
    private double netProfitMargin;            // %
    private double netProfitMarginPreviousYear;
    private double ebitdaMargin;               // Operating Profit Margin %
    private double ebitdaMarginPreviousYear;
    private double grossMargin;                // %
    private double grossMarginPreviousYear;

    // ─────────────────────────────────────────────────────────────────────────
    // Growth Metrics (%)
    // ─────────────────────────────────────────────────────────────────────────

    private double revenueGrowthYoY;
    private double revenueCagr3Y;
    private double revenueCagr5Y;
    private double netProfitGrowthYoY;
    private double epsCagr3Y;
    private double epsCagr5Y;
    private double ebitdaGrowthYoY;

    // ─────────────────────────────────────────────────────────────────────────
    // Financial Health / Leverage
    // ─────────────────────────────────────────────────────────────────────────

    private double debtToEquity;
    private double debtToEquityPreviousYear;
    private double currentRatio;
    private double currentRatioPreviousYear;
    private double quickRatio;                 // (Current Assets − Inventory) / Current Liabilities
    private double interestCoverageRatio;      // EBIT / Interest Expense
    private double debtToEbitda;
    private double debtToAssets;
    private double longTermDebtRatioPreviousYear; // for Piotroski F5
    private int    consecutiveDebtReductionYears;

    // ─────────────────────────────────────────────────────────────────────────
    // Banking / NBFC Specific Metrics
    // (only evaluated when sectorType == BANKING or NBFC)
    // ─────────────────────────────────────────────────────────────────────────

    private double netInterestMargin;          // NIM %
    private double grossNpaPercent;            // GNPA %
    private double netNpaPercent;              // NNPA %
    private double capitalAdequacyRatio;       // CAR/CRAR % — RBI minimum 11.5%
    private double provisionCoverageRatio;     // PCR % — RBI guidance 70%+
    private double costToIncomeRatio;          // % — lower is better
    private double casaRatio;                  // % — higher is better (low-cost deposits)

    // ─────────────────────────────────────────────────────────────────────────
    // Efficiency Metrics
    // ─────────────────────────────────────────────────────────────────────────

    private double assetTurnoverRatio;
    private double assetTurnoverPreviousYear;  // for Piotroski F9
    private double inventoryTurnoverRatio;
    private double debtorDays;                 // Receivable days = AR / Revenue × 365
    private double payableDays;
    private double cashConversionCycle;        // Debtor days + Inventory days − Payable days
    private double workingCapitalTurnover;

    // ─────────────────────────────────────────────────────────────────────────
    // Cash Flow Metrics (in ₹ Crores)
    // ─────────────────────────────────────────────────────────────────────────

    private double operatingCashFlowTTM;
    private double freeCashFlowTTM;            // OCF − Capex
    private double capexTTM;
    private double cashFlowToNetIncomeRatio;   // OCF / Net Income (earnings quality)
    private double fcfYieldPercent;            // FCF / Market Cap × 100
    private double capexToRevenuePercent;
    private boolean consistentPositiveOcf;     // true if OCF > 0 for 3+ consecutive years

    // ─────────────────────────────────────────────────────────────────────────
    // Dividend Metrics
    // ─────────────────────────────────────────────────────────────────────────

    private double dividendPerShare;
    private double dividendPayoutRatio;        // %
    private double dividendGrowthRate;         // %
    private int    dividendConsistencyYears;   // consecutive years of dividend payment

    // ─────────────────────────────────────────────────────────────────────────
    // Shareholding Pattern (%) — latest quarter
    // ─────────────────────────────────────────────────────────────────────────

    private double promoterHoldingPercent;
    private double promoterPledgePercent;      // % of promoter shares pledged — red flag if > 10%
    private double fiiHoldingPercent;
    private double diiHoldingPercent;
    private double publicHoldingPercent;
    private boolean newSharesIssuedLastYear;   // dilution flag — used in Piotroski F7

    // ─────────────────────────────────────────────────────────────────────────
    // Derived / Computed Results (populated by performFullAnalysis)
    // ─────────────────────────────────────────────────────────────────────────

    private double grahamNumberComputed;       // √(22.5 × EPS × BVPS)
    private double priceToGrahamNumber;        // currentPrice / grahamNumberComputed
    private int    piotroskiFScore;            // 0–9 Piotroski score
    private int    fundamentalScore;           // 0–100 weighted composite score
    private boolean fundamentallySound;        // true if score ≥ 60
    private FundamentalStatus fundamentalStatus;

    private final List<String> passedCriteria  = new ArrayList<>();
    private final List<String> failedCriteria  = new ArrayList<>();
    private final List<String> warningFlags    = new ArrayList<>();
    private final List<String> analysisNotes   = new ArrayList<>();

    // ─────────────────────────────────────────────────────────────────────────
    // Constructors
    // ─────────────────────────────────────────────────────────────────────────

    public CStockFundamentalAnalysis() {}

    public CStockFundamentalAnalysis(String stockSymbol, String companyName, SectorType sectorType) {
        this.stockSymbol = stockSymbol;
        this.companyName = companyName;
        this.sectorType  = sectorType != null ? sectorType : SectorType.GENERAL;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Step 0 — Derive metrics that can be computed from raw inputs
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Derives every computable metric from raw inputs so callers can
     * populate just the raw balance-sheet / income-statement numbers
     * and let this method fill in the ratios automatically.
     * Called automatically by performFullAnalysis().
     */
    public void computeDerivedMetrics() {

        // Graham Number: √(22.5 × EPS × BVPS) — Benjamin Graham's intrinsic value formula
        if (eps > 0 && bookValuePerShare > 0) {
            grahamNumberComputed = Math.sqrt(22.5 * eps * bookValuePerShare);
            priceToGrahamNumber  = currentPrice > 0 ? currentPrice / grahamNumberComputed : 0;
        }

        // P/E Ratio
        if (peRatio <= 0 && eps > 0 && currentPrice > 0) peRatio = currentPrice / eps;

        // P/B Ratio
        if (pbRatio <= 0 && bookValuePerShare > 0 && currentPrice > 0) pbRatio = currentPrice / bookValuePerShare;

        // PEG Ratio = P/E / EPS CAGR 3Y
        if (pegRatio <= 0 && peRatio > 0 && epsCagr3Y > 0) pegRatio = peRatio / epsCagr3Y;

        // Revenue growth YoY
        if (revenueGrowthYoY == 0 && revenueTTM > 0 && revenuePreviousYear > 0)
            revenueGrowthYoY = ((revenueTTM - revenuePreviousYear) / revenuePreviousYear) * 100;

        // Revenue CAGR 3Y: (CurrentRevenue / Revenue3YearsAgo)^(1/3) − 1
        if (revenueCagr3Y == 0 && revenueTTM > 0 && revenue3YearsAgo > 0)
            revenueCagr3Y = (Math.pow(revenueTTM / revenue3YearsAgo, 1.0 / 3) - 1) * 100;

        // Revenue CAGR 5Y
        if (revenueCagr5Y == 0 && revenueTTM > 0 && revenue5YearsAgo > 0)
            revenueCagr5Y = (Math.pow(revenueTTM / revenue5YearsAgo, 1.0 / 5) - 1) * 100;

        // Net Profit Growth YoY
        if (netProfitGrowthYoY == 0 && netProfitTTM != 0 && netProfitPreviousYear != 0)
            netProfitGrowthYoY = ((netProfitTTM - netProfitPreviousYear) / Math.abs(netProfitPreviousYear)) * 100;

        // EBITDA Margin
        if (ebitdaMargin == 0 && ebitdaTTM != 0 && revenueTTM > 0)
            ebitdaMargin = (ebitdaTTM / revenueTTM) * 100;

        // Net Profit Margin
        if (netProfitMargin == 0 && netProfitTTM != 0 && revenueTTM > 0)
            netProfitMargin = (netProfitTTM / revenueTTM) * 100;

        // Gross Margin
        if (grossMargin == 0 && grossProfitTTM > 0 && revenueTTM > 0)
            grossMargin = (grossProfitTTM / revenueTTM) * 100;

        // Interest Coverage = EBITDA / Interest Expense (proxy when EBIT not available)
        if (interestCoverageRatio == 0 && ebitdaTTM > 0 && interestExpenseTTM > 0)
            interestCoverageRatio = ebitdaTTM / interestExpenseTTM;

        // Debt/Equity
        if (debtToEquity == 0 && shareholdersEquity > 0)
            debtToEquity = (totalDebt < 0 ? 0 : totalDebt) / shareholdersEquity;

        // Current Ratio
        if (currentRatio == 0 && currentLiabilities > 0)
            currentRatio = currentAssets / currentLiabilities;

        // Quick Ratio = (Current Assets − Inventory) / Current Liabilities
        if (quickRatio == 0 && currentLiabilities > 0)
            quickRatio = (currentAssets - inventory) / currentLiabilities;

        // ROE = Net Profit / Shareholders Equity
        if (roe == 0 && shareholdersEquity > 0 && netProfitTTM != 0)
            roe = (netProfitTTM / shareholdersEquity) * 100;

        // ROCE = EBITDA / Capital Employed
        if (roce == 0 && totalCapitalEmployed > 0 && ebitdaTTM > 0)
            roce = (ebitdaTTM / totalCapitalEmployed) * 100;

        // ROA = Net Profit / Total Assets
        if (roa == 0 && totalAssets > 0 && netProfitTTM != 0)
            roa = (netProfitTTM / totalAssets) * 100;

        // Debt/EBITDA
        if (debtToEbitda == 0 && ebitdaTTM > 0 && totalDebt > 0)
            debtToEbitda = totalDebt / ebitdaTTM;

        // Debt/Assets
        if (debtToAssets == 0 && totalAssets > 0 && totalDebt > 0)
            debtToAssets = totalDebt / totalAssets;

        // FCF = OCF − Capex
        if (freeCashFlowTTM == 0 && operatingCashFlowTTM != 0)
            freeCashFlowTTM = operatingCashFlowTTM - capexTTM;

        // FCF Yield = FCF / Market Cap
        if (fcfYieldPercent == 0 && marketCapCr > 0 && freeCashFlowTTM != 0)
            fcfYieldPercent = (freeCashFlowTTM / marketCapCr) * 100;

        // Cash Flow to Net Income ratio
        if (cashFlowToNetIncomeRatio == 0 && netProfitTTM > 0 && operatingCashFlowTTM > 0)
            cashFlowToNetIncomeRatio = operatingCashFlowTTM / netProfitTTM;

        // Capex / Revenue
        if (capexToRevenuePercent == 0 && capexTTM > 0 && revenueTTM > 0)
            capexToRevenuePercent = (capexTTM / revenueTTM) * 100;

        // Cash Conversion Cycle = Debtor days + Inventory days − Payable days
        if (cashConversionCycle == 0) {
            double inventoryDays = inventoryTurnoverRatio > 0 ? 365.0 / inventoryTurnoverRatio : 0;
            cashConversionCycle = debtorDays + inventoryDays - payableDays;
        }

        // Market Cap Category (SEBI classification — Indian context)
        if (marketCapCategory == null && marketCapCr > 0) {
            if      (marketCapCr >= 20_000) marketCapCategory = MarketCapCategory.LARGE_CAP;
            else if (marketCapCr >= 5_000)  marketCapCategory = MarketCapCategory.MID_CAP;
            else if (marketCapCr >= 500)    marketCapCategory = MarketCapCategory.SMALL_CAP;
            else                            marketCapCategory = MarketCapCategory.MICRO_CAP;
        }

        // Dividend Yield
        if (dividendYield == 0 && dividendPerShare > 0 && currentPrice > 0)
            dividendYield = (dividendPerShare / currentPrice) * 100;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Step 1 — Valuation Analysis  (max 25 pts)
    // ─────────────────────────────────────────────────────────────────────────

    private int analyzeValuation() {
        int score = 0;

        // P/E vs Sector P/E (10 pts) — core relative-valuation check
        if (peRatio > 0) {
            double refPe = sectorPeRatio > 0 ? sectorPeRatio : getSectorDefaultPe();
            if      (peRatio < refPe * 0.70) { score += 10; passedCriteria.add("P/E: Significantly below sector (" + f(peRatio) + "x vs sector " + f(refPe) + "x)"); }
            else if (peRatio < refPe)         { score += 7;  passedCriteria.add("P/E: Below sector avg (" + f(peRatio) + "x vs sector " + f(refPe) + "x)"); }
            else if (peRatio < refPe * 1.20)  { score += 4;  warningFlags.add("P/E: Slightly above sector (" + f(peRatio) + "x)"); }
            else if (peRatio < refPe * 1.50)  { score += 1;  warningFlags.add("P/E: Above sector (" + f(peRatio) + "x vs sector " + f(refPe) + "x)"); }
            else                               { failedCriteria.add("P/E: Significantly overvalued vs sector (" + f(peRatio) + "x vs sector " + f(refPe) + "x)"); }
        } else if (peRatio < 0) {
            failedCriteria.add("P/E: Negative earnings — loss-making");
        } else {
            warningFlags.add("P/E: Not available");
        }

        // P/B Ratio (5 pts)
        if (pbRatio > 0) {
            if      (pbRatio < 1.0) { score += 5; passedCriteria.add("P/B: Trading below book value (" + f(pbRatio) + "x) — potential undervalue"); }
            else if (pbRatio < 2.5) { score += 4; passedCriteria.add("P/B: Fair value (" + f(pbRatio) + "x)"); }
            else if (pbRatio < 4.0) { score += 2; warningFlags.add("P/B: Moderate premium (" + f(pbRatio) + "x)"); }
            else if (pbRatio < 6.0) { score += 1; warningFlags.add("P/B: High premium (" + f(pbRatio) + "x)"); }
            else                     { failedCriteria.add("P/B: Overvalued (" + f(pbRatio) + "x)"); }
        } else {
            warningFlags.add("P/B: Not available");
        }

        // Graham Number (5 pts) — √(22.5 × EPS × BVPS) — Benjamin Graham intrinsic value
        if (grahamNumberComputed > 0 && currentPrice > 0) {
            if      (currentPrice <= grahamNumberComputed)           { score += 5; passedCriteria.add("Graham Number: ₹" + f(currentPrice) + " ≤ Graham ₹" + f(grahamNumberComputed) + " — undervalued"); }
            else if (currentPrice <= grahamNumberComputed * 1.20)    { score += 3; warningFlags.add("Graham Number: Marginally above (" + f(priceToGrahamNumber) + "x)"); }
            else if (currentPrice <= grahamNumberComputed * 1.50)    { score += 1; warningFlags.add("Graham Number: Above intrinsic value (" + f(priceToGrahamNumber) + "x)"); }
            else                                                       { failedCriteria.add("Graham Number: Price " + f(priceToGrahamNumber) + "x Graham value — expensive"); }
        } else {
            warningFlags.add("Graham Number: Cannot compute (need EPS and BVPS)");
        }

        // PEG Ratio (5 pts) — Lynch: PEG < 1 = undervalued relative to growth
        if (pegRatio > 0) {
            if      (pegRatio < 0.5)  { score += 5; passedCriteria.add("PEG: Very attractive (" + f(pegRatio) + ")"); }
            else if (pegRatio < 1.0)  { score += 4; passedCriteria.add("PEG: Undervalued relative to growth (" + f(pegRatio) + ")"); }
            else if (pegRatio < 1.5)  { score += 2; warningFlags.add("PEG: Fair (" + f(pegRatio) + ")"); }
            else if (pegRatio < 2.0)  { score += 1; warningFlags.add("PEG: Slightly stretched (" + f(pegRatio) + ")"); }
            else                       { failedCriteria.add("PEG: Overvalued vs growth (" + f(pegRatio) + ")"); }
        } else {
            warningFlags.add("PEG: Not available");
        }

        analysisNotes.add("[Valuation]     score: " + score + "/25");
        return score;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Step 2 — Profitability Analysis  (max 25 pts)
    // ─────────────────────────────────────────────────────────────────────────

    private int analyzeProfitability() {
        int score = 0;

        // ROE — Return on Equity (8 pts)
        // India benchmark: > 15% good, > 20% strong, Buffett: prefers > 20% consistently
        if (roe != 0) {
            if      (roe >= 25) { score += 8; passedCriteria.add("ROE: Excellent (" + f(roe) + "%)"); }
            else if (roe >= 18) { score += 6; passedCriteria.add("ROE: Strong (" + f(roe) + "%)"); }
            else if (roe >= 12) { score += 4; passedCriteria.add("ROE: Acceptable (" + f(roe) + "%)"); }
            else if (roe >= 8)  { score += 2; warningFlags.add("ROE: Below benchmark (" + f(roe) + "%)"); }
            else if (roe > 0)   { warningFlags.add("ROE: Weak (" + f(roe) + "%)"); }
            else                 { failedCriteria.add("ROE: Negative (" + f(roe) + "%)"); }

            // Trend bonus
            if (roePreviousYear > 0 && roe > roePreviousYear) { score += 1; passedCriteria.add("ROE: Improving YoY"); }
        } else {
            warningFlags.add("ROE: Not available");
        }

        // ROCE — Return on Capital Employed (8 pts)
        // Should exceed India WACC (~12–14%); ROCE > 20% signals moat
        if (roce > 0) {
            if      (roce >= 25) { score += 8; passedCriteria.add("ROCE: Excellent (" + f(roce) + "%) — strong economic moat"); }
            else if (roce >= 18) { score += 6; passedCriteria.add("ROCE: Strong (" + f(roce) + "%)"); }
            else if (roce >= 12) { score += 4; passedCriteria.add("ROCE: Adequate (" + f(roce) + "%)"); }
            else if (roce >= 8)  { score += 2; warningFlags.add("ROCE: Below cost of capital (" + f(roce) + "%)"); }
            else                  { failedCriteria.add("ROCE: Very weak (" + f(roce) + "%)"); }
        } else {
            warningFlags.add("ROCE: Not available");
        }

        // Net Profit Margin (5 pts) — compared to sector benchmark
        if (netProfitMargin != 0) {
            double sectorMargin = getSectorBenchmarkNetMargin();
            if      (netProfitMargin >= sectorMargin * 1.5)   { score += 5; passedCriteria.add("Net Margin: Well above sector (" + f(netProfitMargin) + "%)"); }
            else if (netProfitMargin >= sectorMargin)          { score += 4; passedCriteria.add("Net Margin: Above sector (" + f(netProfitMargin) + "% vs ~" + f(sectorMargin) + "%)"); }
            else if (netProfitMargin >= sectorMargin * 0.7)    { score += 2; warningFlags.add("Net Margin: Near sector (" + f(netProfitMargin) + "%)"); }
            else if (netProfitMargin > 0)                      { score += 1; warningFlags.add("Net Margin: Below sector (" + f(netProfitMargin) + "% vs ~" + f(sectorMargin) + "%)"); }
            else                                                { failedCriteria.add("Net Margin: Negative (" + f(netProfitMargin) + "%)"); }

            if (netProfitMarginPreviousYear > 0 && netProfitMargin > netProfitMarginPreviousYear)
                { score += 1; passedCriteria.add("Net Margin: Expanding trend"); }
        } else {
            warningFlags.add("Net Profit Margin: Not available");
        }

        // EBITDA Margin — operating leverage indicator (4 pts)
        if (ebitdaMargin != 0) {
            if      (ebitdaMargin >= 30) { score += 4; passedCriteria.add("EBITDA Margin: Excellent (" + f(ebitdaMargin) + "%)"); }
            else if (ebitdaMargin >= 20) { score += 3; passedCriteria.add("EBITDA Margin: Strong (" + f(ebitdaMargin) + "%)"); }
            else if (ebitdaMargin >= 12) { score += 2; warningFlags.add("EBITDA Margin: Average (" + f(ebitdaMargin) + "%)"); }
            else if (ebitdaMargin > 0)   { score += 1; warningFlags.add("EBITDA Margin: Thin (" + f(ebitdaMargin) + "%)"); }
            else                          { failedCriteria.add("EBITDA Margin: Negative (" + f(ebitdaMargin) + "%)"); }

            if (ebitdaMarginPreviousYear > 0 && ebitdaMargin > ebitdaMarginPreviousYear)
                { score += 1; passedCriteria.add("EBITDA Margin: Expanding"); }
        }

        analysisNotes.add("[Profitability]  score: " + score + "/25");
        return score;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Step 3 — Growth Analysis  (max 20 pts)
    // ─────────────────────────────────────────────────────────────────────────

    private int analyzeGrowth() {
        int score = 0;

        // Revenue CAGR 3Y (7 pts) — India GDP growth ~7%, outperformer should be 15%+
        if (revenueCagr3Y != 0) {
            if      (revenueCagr3Y >= 20) { score += 7; passedCriteria.add("Revenue CAGR 3Y: Excellent (" + f(revenueCagr3Y) + "%)"); }
            else if (revenueCagr3Y >= 15) { score += 6; passedCriteria.add("Revenue CAGR 3Y: Strong (" + f(revenueCagr3Y) + "%)"); }
            else if (revenueCagr3Y >= 10) { score += 4; passedCriteria.add("Revenue CAGR 3Y: Good (" + f(revenueCagr3Y) + "%)"); }
            else if (revenueCagr3Y >= 5)  { score += 2; warningFlags.add("Revenue CAGR 3Y: Moderate (" + f(revenueCagr3Y) + "%)"); }
            else if (revenueCagr3Y > 0)   { score += 1; warningFlags.add("Revenue CAGR 3Y: Below GDP growth (" + f(revenueCagr3Y) + "%)"); }
            else                           { failedCriteria.add("Revenue CAGR 3Y: Contracting (" + f(revenueCagr3Y) + "%)"); }
        } else if (revenueGrowthYoY != 0) {
            // Fallback to YoY
            if      (revenueGrowthYoY >= 15) { score += 5; passedCriteria.add("Revenue Growth YoY: Strong (" + f(revenueGrowthYoY) + "%)"); }
            else if (revenueGrowthYoY >= 8)   { score += 3; passedCriteria.add("Revenue Growth YoY: Good (" + f(revenueGrowthYoY) + "%)"); }
            else if (revenueGrowthYoY > 0)    { score += 1; warningFlags.add("Revenue Growth YoY: Slow (" + f(revenueGrowthYoY) + "%)"); }
            else                               { failedCriteria.add("Revenue Growth YoY: Negative (" + f(revenueGrowthYoY) + "%)"); }
        } else {
            warningFlags.add("Revenue Growth: Not available");
        }

        // EPS CAGR 3Y (7 pts) — earnings compounding is the engine of long-term returns
        if (epsCagr3Y != 0) {
            if      (epsCagr3Y >= 20) { score += 7; passedCriteria.add("EPS CAGR 3Y: Excellent (" + f(epsCagr3Y) + "%)"); }
            else if (epsCagr3Y >= 15) { score += 5; passedCriteria.add("EPS CAGR 3Y: Strong (" + f(epsCagr3Y) + "%)"); }
            else if (epsCagr3Y >= 10) { score += 3; passedCriteria.add("EPS CAGR 3Y: Good (" + f(epsCagr3Y) + "%)"); }
            else if (epsCagr3Y >= 5)  { score += 2; warningFlags.add("EPS CAGR 3Y: Moderate (" + f(epsCagr3Y) + "%)"); }
            else if (epsCagr3Y > 0)   { score += 1; warningFlags.add("EPS CAGR 3Y: Weak (" + f(epsCagr3Y) + "%)"); }
            else                       { failedCriteria.add("EPS CAGR 3Y: Declining (" + f(epsCagr3Y) + "%)"); }
        } else {
            warningFlags.add("EPS CAGR 3Y: Not available");
        }

        // Net Profit Growth YoY (6 pts)
        if (netProfitGrowthYoY != 0) {
            if      (netProfitGrowthYoY >= 25) { score += 6; passedCriteria.add("Net Profit Growth YoY: Excellent (" + f(netProfitGrowthYoY) + "%)"); }
            else if (netProfitGrowthYoY >= 15) { score += 5; passedCriteria.add("Net Profit Growth YoY: Strong (" + f(netProfitGrowthYoY) + "%)"); }
            else if (netProfitGrowthYoY >= 10) { score += 3; passedCriteria.add("Net Profit Growth YoY: Good (" + f(netProfitGrowthYoY) + "%)"); }
            else if (netProfitGrowthYoY >= 0)  { score += 1; warningFlags.add("Net Profit Growth YoY: Flat/Slow (" + f(netProfitGrowthYoY) + "%)"); }
            else                                { failedCriteria.add("Net Profit Growth YoY: Declining (" + f(netProfitGrowthYoY) + "%)"); }
        } else {
            warningFlags.add("Net Profit Growth: Not available");
        }

        analysisNotes.add("[Growth]         score: " + score + "/20");
        return score;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Step 4 — Financial Health  (max 20 pts)
    //          Banking sector uses a separate method (NIM, GNPA, CAR, PCR)
    // ─────────────────────────────────────────────────────────────────────────

    private int analyzeFinancialHealth() {
        int score = 0;

        if (sectorType == SectorType.BANKING || sectorType == SectorType.NBFC) {
            score += analyzeBankingHealth();
        } else {
            // Debt to Equity (8 pts) — sector-aware upper limit
            double deMax = getSectorMaxDebtToEquity();
            if (debtToEquity >= 0) {
                if      (debtToEquity == 0)                   { score += 8; passedCriteria.add("D/E: Zero debt — pristine balance sheet"); }
                else if (debtToEquity < 0.30)                 { score += 7; passedCriteria.add("D/E: Very low (" + f(debtToEquity) + ")"); }
                else if (debtToEquity < 0.50)                 { score += 6; passedCriteria.add("D/E: Low (" + f(debtToEquity) + ")"); }
                else if (debtToEquity < deMax)                { score += 4; passedCriteria.add("D/E: Manageable (" + f(debtToEquity) + ")"); }
                else if (debtToEquity < deMax * 1.5)          { score += 2; warningFlags.add("D/E: Elevated (" + f(debtToEquity) + "), sector limit " + f(deMax) + ")"); }
                else                                           { failedCriteria.add("D/E: High debt (" + f(debtToEquity) + " > sector max " + f(deMax) + ")"); }

                if (debtToEquityPreviousYear > debtToEquity && consecutiveDebtReductionYears > 0)
                    { score += 1; passedCriteria.add("D/E: Debt reduction trend (" + consecutiveDebtReductionYears + " yr)"); }
            } else {
                warningFlags.add("D/E: Not available");
            }

            // Current Ratio (6 pts) — liquidity health
            if (currentRatio > 0) {
                if      (currentRatio >= 2.5) { score += 6; passedCriteria.add("Current Ratio: Very strong (" + f(currentRatio) + ")"); }
                else if (currentRatio >= 2.0) { score += 5; passedCriteria.add("Current Ratio: Strong (" + f(currentRatio) + ")"); }
                else if (currentRatio >= 1.5) { score += 4; passedCriteria.add("Current Ratio: Good (" + f(currentRatio) + ")"); }
                else if (currentRatio >= 1.2) { score += 2; warningFlags.add("Current Ratio: Adequate (" + f(currentRatio) + ")"); }
                else if (currentRatio >= 1.0) { score += 1; warningFlags.add("Current Ratio: Tight (" + f(currentRatio) + ")"); }
                else                           { failedCriteria.add("Current Ratio: Below 1.0 — liquidity risk (" + f(currentRatio) + ")"); }

                if (currentRatioPreviousYear > 0 && currentRatio > currentRatioPreviousYear)
                    { score += 1; passedCriteria.add("Current Ratio: Improving"); }
            } else {
                warningFlags.add("Current Ratio: Not available");
            }

            // Interest Coverage Ratio (6 pts) — debt serviceability
            if (interestCoverageRatio > 0) {
                if      (interestCoverageRatio >= 10) { score += 6; passedCriteria.add("Interest Coverage: Excellent (" + f(interestCoverageRatio) + "x)"); }
                else if (interestCoverageRatio >= 5)  { score += 5; passedCriteria.add("Interest Coverage: Strong (" + f(interestCoverageRatio) + "x)"); }
                else if (interestCoverageRatio >= 3)  { score += 3; passedCriteria.add("Interest Coverage: Adequate (" + f(interestCoverageRatio) + "x)"); }
                else if (interestCoverageRatio >= 2)  { score += 1; warningFlags.add("Interest Coverage: Thin (" + f(interestCoverageRatio) + "x)"); }
                else if (interestCoverageRatio >= 1)  { warningFlags.add("Interest Coverage: Danger zone (" + f(interestCoverageRatio) + "x)"); }
                else                                   { failedCriteria.add("Interest Coverage: Cannot service debt (" + f(interestCoverageRatio) + "x)"); }
            } else if (totalDebt == 0) {
                score += 6; passedCriteria.add("Interest Coverage: Debt-free — not applicable");
            } else {
                warningFlags.add("Interest Coverage: Not available");
            }
        }

        analysisNotes.add("[Fin. Health]    score: " + score + "/20");
        return score;
    }

    /**
     * Banking-specific health metrics replacing standard leverage checks.
     * Uses RBI regulatory benchmarks.
     */
    private int analyzeBankingHealth() {
        int score = 0;

        // GNPA % — Gross Non-Performing Assets (6 pts)
        if (grossNpaPercent >= 0) {
            if      (grossNpaPercent < 1.0) { score += 6; passedCriteria.add("GNPA: Very clean book (" + f(grossNpaPercent) + "%)"); }
            else if (grossNpaPercent < 2.5) { score += 5; passedCriteria.add("GNPA: Good (" + f(grossNpaPercent) + "%)"); }
            else if (grossNpaPercent < 4.0) { score += 3; warningFlags.add("GNPA: Moderate stress (" + f(grossNpaPercent) + "%)"); }
            else if (grossNpaPercent < 7.0) { score += 1; failedCriteria.add("GNPA: Elevated (" + f(grossNpaPercent) + "%)"); }
            else                             { failedCriteria.add("GNPA: Severe stress (" + f(grossNpaPercent) + "%)"); }
        }

        // CAR / CRAR % — Capital Adequacy Ratio: RBI minimum 11.5% (6 pts)
        if (capitalAdequacyRatio > 0) {
            if      (capitalAdequacyRatio >= 17) { score += 6; passedCriteria.add("CAR: Strongly capitalised (" + f(capitalAdequacyRatio) + "%)"); }
            else if (capitalAdequacyRatio >= 14) { score += 4; passedCriteria.add("CAR: Well capitalised (" + f(capitalAdequacyRatio) + "%)"); }
            else if (capitalAdequacyRatio >= 11.5) { score += 2; warningFlags.add("CAR: Near RBI minimum (" + f(capitalAdequacyRatio) + "%)"); }
            else                                   { failedCriteria.add("CAR: Below RBI floor 11.5% (" + f(capitalAdequacyRatio) + "%)"); }
        }

        // NIM % — Net Interest Margin (4 pts) — private banks target 3.5%+
        if (netInterestMargin > 0) {
            if      (netInterestMargin >= 4.5) { score += 4; passedCriteria.add("NIM: Excellent (" + f(netInterestMargin) + "%)"); }
            else if (netInterestMargin >= 3.5) { score += 3; passedCriteria.add("NIM: Good (" + f(netInterestMargin) + "%)"); }
            else if (netInterestMargin >= 2.5) { score += 2; warningFlags.add("NIM: Average (" + f(netInterestMargin) + "%)"); }
            else                                { failedCriteria.add("NIM: Low (" + f(netInterestMargin) + "%)"); }
        }

        // PCR % — Provision Coverage Ratio: RBI guidance 70%+ (4 pts)
        if (provisionCoverageRatio > 0) {
            if      (provisionCoverageRatio >= 80) { score += 4; passedCriteria.add("PCR: Excellent (" + f(provisionCoverageRatio) + "%)"); }
            else if (provisionCoverageRatio >= 70) { score += 3; passedCriteria.add("PCR: Good — meets RBI guidance (" + f(provisionCoverageRatio) + "%)"); }
            else if (provisionCoverageRatio >= 60) { score += 1; warningFlags.add("PCR: Below RBI guidance (" + f(provisionCoverageRatio) + "%)"); }
            else                                    { failedCriteria.add("PCR: Low — under-provisioned (" + f(provisionCoverageRatio) + "%)"); }
        }

        // CASA Ratio — low-cost funding advantage (bonus)
        if (casaRatio > 0) {
            if (casaRatio >= 40) { score += 2; passedCriteria.add("CASA: Strong low-cost funding (" + f(casaRatio) + "%)"); }
            else if (casaRatio >= 30) { score += 1; warningFlags.add("CASA: Adequate (" + f(casaRatio) + "%)"); }
            else { warningFlags.add("CASA: Low — higher funding cost (" + f(casaRatio) + "%)"); }
        }

        return score;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Step 5 — Cash Flow Quality  (max 10 pts)
    // ─────────────────────────────────────────────────────────────────────────

    private int analyzeCashFlow() {
        int score = 0;

        // Free Cash Flow (4 pts) — king of all metrics
        if (freeCashFlowTTM > 0) {
            score += 3; passedCriteria.add("FCF: Positive (₹" + f(freeCashFlowTTM) + " Cr)");
            if      (fcfYieldPercent >= 5) { score += 1; passedCriteria.add("FCF Yield: High (" + f(fcfYieldPercent) + "%)"); }
            else if (fcfYieldPercent >= 2) { warningFlags.add("FCF Yield: Moderate (" + f(fcfYieldPercent) + "%)"); }
            else                            { warningFlags.add("FCF Yield: Low (" + f(fcfYieldPercent) + "%)"); }
        } else if (freeCashFlowTTM < 0) {
            failedCriteria.add("FCF: Negative (₹" + f(freeCashFlowTTM) + " Cr) — possible cash burn or heavy capex phase");
        } else {
            warningFlags.add("FCF: Not available");
        }

        // OCF / Net Income ratio > 1 = high earnings quality (3 pts)
        // When OCF > Net Income, earnings are backed by real cash (not accruals)
        if (cashFlowToNetIncomeRatio > 0) {
            if      (cashFlowToNetIncomeRatio >= 1.2) { score += 3; passedCriteria.add("Earnings Quality (OCF/NI): High (" + f(cashFlowToNetIncomeRatio) + "x) — cash-backed earnings"); }
            else if (cashFlowToNetIncomeRatio >= 0.8) { score += 2; passedCriteria.add("Earnings Quality (OCF/NI): Good (" + f(cashFlowToNetIncomeRatio) + "x)"); }
            else if (cashFlowToNetIncomeRatio >= 0.5) { score += 1; warningFlags.add("Earnings Quality (OCF/NI): Moderate (" + f(cashFlowToNetIncomeRatio) + "x)"); }
            else                                       { failedCriteria.add("Earnings Quality (OCF/NI): Low (" + f(cashFlowToNetIncomeRatio) + "x) — accrual-heavy, check receivables"); }
        }

        // Consistent positive OCF (2 pts) — reliability of cash generation
        if (consistentPositiveOcf) { score += 2; passedCriteria.add("OCF: Consistently positive over 3+ years"); }

        // Capex/Revenue — healthy reinvestment band (1 pt)
        if (capexToRevenuePercent > 0) {
            if (capexToRevenuePercent >= 2 && capexToRevenuePercent <= 12)
                { score += 1; passedCriteria.add("Capex/Revenue: Healthy reinvestment (" + f(capexToRevenuePercent) + "%)"); }
            else if (capexToRevenuePercent > 20)
                { warningFlags.add("Capex/Revenue: Very high (" + f(capexToRevenuePercent) + "%) — heavy expansion phase"); }
        }

        analysisNotes.add("[Cash Flow]      score: " + score + "/10");
        return score;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Step 6 — Ownership / Shareholding Pattern  (max 10 pts)
    // ─────────────────────────────────────────────────────────────────────────

    private int analyzeOwnership() {
        int score = 0;

        // Promoter Holding % (4 pts) — higher holding = skin-in-the-game
        if (promoterHoldingPercent > 0) {
            if      (promoterHoldingPercent >= 65) { score += 4; passedCriteria.add("Promoter Holding: Very high (" + f(promoterHoldingPercent) + "%)"); }
            else if (promoterHoldingPercent >= 50) { score += 3; passedCriteria.add("Promoter Holding: High (" + f(promoterHoldingPercent) + "%)"); }
            else if (promoterHoldingPercent >= 35) { score += 2; warningFlags.add("Promoter Holding: Moderate (" + f(promoterHoldingPercent) + "%)"); }
            else if (promoterHoldingPercent >= 20) { score += 1; warningFlags.add("Promoter Holding: Low (" + f(promoterHoldingPercent) + "%)"); }
            else                                    { failedCriteria.add("Promoter Holding: Very low (" + f(promoterHoldingPercent) + "%) — governance risk"); }
        } else {
            warningFlags.add("Promoter Holding: Not available");
        }

        // Promoter Pledge % (3 pts) — pledged shares are a major Indian-market red flag
        // When stock falls, lenders liquidate pledged shares → price collapse spiral
        if (promoterHoldingPercent > 0) {
            if      (promoterPledgePercent == 0)   { score += 3; passedCriteria.add("Promoter Pledge: Zero — excellent"); }
            else if (promoterPledgePercent < 5)    { score += 2; warningFlags.add("Promoter Pledge: Minimal (" + f(promoterPledgePercent) + "%)"); }
            else if (promoterPledgePercent < 15)   { score += 1; warningFlags.add("Promoter Pledge: Elevated (" + f(promoterPledgePercent) + "%) — monitor closely"); }
            else if (promoterPledgePercent < 30)   { failedCriteria.add("Promoter Pledge: High (" + f(promoterPledgePercent) + "%) — significant risk"); }
            else                                    { failedCriteria.add("Promoter Pledge: Very high (" + f(promoterPledgePercent) + "%) — MAJOR RED FLAG"); }
        }

        // FII Holding (2 pts) — foreign institutional buying signals quality
        if (fiiHoldingPercent > 0) {
            if      (fiiHoldingPercent >= 20) { score += 2; passedCriteria.add("FII Holding: Strong foreign confidence (" + f(fiiHoldingPercent) + "%)"); }
            else if (fiiHoldingPercent >= 8)  { score += 1; passedCriteria.add("FII Holding: Moderate (" + f(fiiHoldingPercent) + "%)"); }
            else                               { warningFlags.add("FII Holding: Low (" + f(fiiHoldingPercent) + "%)"); }
        }

        // DII Holding (1 pt) — domestic mutual funds / insurance companies
        if (diiHoldingPercent >= 10) { score += 1; passedCriteria.add("DII Holding: Significant (" + f(diiHoldingPercent) + "%)"); }

        // Dilution flag — reduces EPS per share
        if (newSharesIssuedLastYear) warningFlags.add("Dilution Alert: New shares issued last year — EPS diluted");

        analysisNotes.add("[Ownership]      score: " + score + "/10");
        return score;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Step 7 — Piotroski F-Score  (0–9; contributes ±3 bonus pts)
    //
    // Nine binary signals: each criterion that is true adds 1 point.
    // 7–9 = strong, 4–6 = neutral, 0–3 = weak
    // ─────────────────────────────────────────────────────────────────────────

    private int computePiotroskiFScore() {
        int f = 0;

        // ── Profitability signals ──────────────────────────────────────────
        // F1: ROA > 0 (profitable)
        if (roa > 0) f++;

        // F2: Operating Cash Flow > 0 (real cash generation)
        if (operatingCashFlowTTM > 0) f++;

        // F3: ROA increasing YoY
        if (roaPreviousYear > 0 && roa > roaPreviousYear) f++;

        // F4: Accrual ratio — OCF/TotalAssets > Net Profit/TotalAssets (cash earnings quality)
        if (totalAssets > 0 && netProfitTTM > 0 && operatingCashFlowTTM > 0
                && (operatingCashFlowTTM / totalAssets) > (netProfitTTM / totalAssets)) f++;

        // ── Leverage / Liquidity signals ──────────────────────────────────
        // F5: Long-term debt ratio decreasing (less leveraged)
        double ltdRatioCurrent = totalAssets > 0 ? longTermDebt / totalAssets : 0;
        if (ltdRatioCurrent < longTermDebtRatioPreviousYear) f++;

        // F6: Current ratio improving
        if (currentRatioPreviousYear > 0 && currentRatio > currentRatioPreviousYear) f++;

        // F7: No new shares issued — no dilution
        if (!newSharesIssuedLastYear) f++;

        // ── Operating efficiency signals ──────────────────────────────────
        // F8: Gross margin improving
        if (grossMarginPreviousYear > 0 && grossMargin > grossMarginPreviousYear) f++;

        // F9: Asset turnover improving — generating more revenue per rupee of assets
        if (assetTurnoverPreviousYear > 0 && assetTurnoverRatio > assetTurnoverPreviousYear) f++;

        piotroskiFScore = f;

        if      (f >= 7) passedCriteria.add("Piotroski F-Score: STRONG (" + f + "/9) — multiple quality signals confirmed");
        else if (f >= 4) warningFlags.add("Piotroski F-Score: Neutral (" + f + "/9)");
        else             failedCriteria.add("Piotroski F-Score: WEAK (" + f + "/9) — multiple quality signals absent");

        analysisNotes.add("[Piotroski]      F-Score: " + f + "/9");
        return f;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Hard-Fail Overrides  (cap the score regardless of other criteria)
    // ─────────────────────────────────────────────────────────────────────────

    private int applyHardFailOverrides(int rawScore) {
        int score = rawScore;

        // Two consecutive years of net losses
        if (netProfitTTM < 0 && netProfitPreviousYear < 0) {
            score = Math.min(score, 25);
            failedCriteria.add("HARD FAIL: Net loss for 2+ consecutive years");
        }

        // Cannot service interest payments
        if (interestCoverageRatio > 0 && interestCoverageRatio < 1.0) {
            score = Math.min(score, 30);
            failedCriteria.add("HARD FAIL: Interest coverage < 1x — debt service risk");
        }

        // Massive promoter pledge in India → cascading sell-off risk
        if (promoterPledgePercent >= 50) {
            score = Math.min(score, 30);
            failedCriteria.add("HARD FAIL: >50% promoter pledge — severe collapse risk");
        }

        // Banking sector: high NPA is existential
        if ((sectorType == SectorType.BANKING || sectorType == SectorType.NBFC) && grossNpaPercent >= 7) {
            score = Math.min(score, 30);
            failedCriteria.add("HARD FAIL: GNPA ≥ 7% — severe asset quality crisis");
        }

        // Negative book value (liabilities > assets)
        if (bookValuePerShare < 0) {
            score = Math.min(score, 20);
            failedCriteria.add("HARD FAIL: Negative book value — technically insolvent");
        }

        // CAR below RBI minimum for banks
        if (sectorType == SectorType.BANKING && capitalAdequacyRatio > 0 && capitalAdequacyRatio < 9.0) {
            score = Math.min(score, 20);
            failedCriteria.add("HARD FAIL: CAR below RBI threshold (" + f(capitalAdequacyRatio) + "%)");
        }

        return score;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 52-Week Range Context
    // ─────────────────────────────────────────────────────────────────────────

    private void analyze52WeekRange() {
        if (fiftyTwoWeekLow > 0 && fiftyTwoWeekHigh > 0 && currentPrice > 0) {
            double range   = fiftyTwoWeekHigh - fiftyTwoWeekLow;
            double rangePos = range > 0 ? (currentPrice - fiftyTwoWeekLow) / range * 100 : 0;
            if      (rangePos < 15)  warningFlags.add("52W Range: Near 52-week low (" + f(rangePos) + "% of range) — possible value or distress");
            else if (rangePos < 35)  passedCriteria.add("52W Range: Lower band — reasonable entry (" + f(rangePos) + "% of range)");
            else if (rangePos > 90)  warningFlags.add("52W Range: Near 52-week high (" + f(rangePos) + "% of range) — limited near-term upside");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Master Orchestrator — call this after setting all fields
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Runs all fundamental analysis criteria in sequence:
     * 1. Derives missing ratios from raw inputs
     * 2. Scores valuation, profitability, growth, financial health, cash flow, ownership
     * 3. Applies Piotroski F-Score bonus/penalty
     * 4. Applies hard-fail overrides
     * 5. Determines fundamentalScore (0–100), fundamentallySound, and fundamentalStatus
     *
     * Idempotent — can be called multiple times (clears previous results first).
     */
    public void performFullAnalysis() {
        passedCriteria.clear();
        failedCriteria.clear();
        warningFlags.clear();
        analysisNotes.clear();

        computeDerivedMetrics();

        int score = 0;
        score += analyzeValuation();
        score += analyzeProfitability();
        score += analyzeGrowth();
        score += analyzeFinancialHealth();
        score += analyzeCashFlow();
        score += analyzeOwnership();

        // Piotroski bonus: +3 for strong (≥7), −3 for weak (≤2)
        int pScore = computePiotroskiFScore();
        if      (pScore >= 7) score += 3;
        else if (pScore <= 2) score -= 3;

        analyze52WeekRange();

        // Apply hard-fail caps AFTER all scoring
        score = applyHardFailOverrides(score);

        // Clamp 0–100
        fundamentalScore = Math.max(0, Math.min(100, score));

        // Determine status and boolean flag
        if      (fundamentalScore >= 75) { fundamentalStatus = FundamentalStatus.STRONG_BUY; fundamentallySound = true; }
        else if (fundamentalScore >= 60) { fundamentalStatus = FundamentalStatus.BUY;        fundamentallySound = true; }
        else if (fundamentalScore >= 40) { fundamentalStatus = FundamentalStatus.NEUTRAL;    fundamentallySound = false; }
        else if (fundamentalScore >= 25) { fundamentalStatus = FundamentalStatus.AVOID;      fundamentallySound = false; }
        else                             { fundamentalStatus = FundamentalStatus.SELL;       fundamentallySound = false; }

        analysisNotes.add("[FINAL] Score: " + fundamentalScore + "/100 | Status: " + fundamentalStatus
                + " | Fundamentally Sound: " + fundamentallySound
                + " | Passed: " + passedCriteria.size()
                + " | Failed: " + failedCriteria.size()
                + " | Warnings: " + warningFlags.size());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Static Batch Methods
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Calls performFullAnalysis() on every stock in the collection,
     * then sorts descending by fundamentalScore.
     */
    public static List<CStockFundamentalAnalysis> analyzeAll(List<CStockFundamentalAnalysis> stocks) {
        if (stocks == null || stocks.isEmpty()) return Collections.emptyList();
        stocks.forEach(CStockFundamentalAnalysis::performFullAnalysis);
        stocks.sort((a, b) -> Integer.compare(b.getFundamentalScore(), a.getFundamentalScore()));
        return stocks;
    }

    /**
     * Returns only the stocks that are fundamentally sound (score ≥ 60),
     * sorted by descending score.
     */
    public static List<CStockFundamentalAnalysis> filterFundamentallySoundStocks(List<CStockFundamentalAnalysis> stocks) {
        List<CStockFundamentalAnalysis> result = new ArrayList<>();
        analyzeAll(stocks).stream()
                .filter(CStockFundamentalAnalysis::isFundamentallySound)
                .forEach(result::add);
        return result;
    }

    /**
     * Returns stocks matching a specific status after running analysis.
     */
    public static List<CStockFundamentalAnalysis> filterByStatus(List<CStockFundamentalAnalysis> stocks, FundamentalStatus status) {
        List<CStockFundamentalAnalysis> result = new ArrayList<>();
        analyzeAll(stocks).stream()
                .filter(s -> s.getFundamentalStatus() == status)
                .forEach(result::add);
        return result;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Sector-aware benchmark helpers
    // ─────────────────────────────────────────────────────────────────────────

    /** Typical trailing P/E for each Indian sector; used when sectorPeRatio is not set. */
    private double getSectorDefaultPe() {
        if (sectorType == null) return 20;
        return switch (sectorType) {
            case IT              -> 35;
            case PHARMA          -> 30;
            case FMCG            -> 45;
            case BANKING         -> 15;
            case NBFC            -> 18;
            case AUTO            -> 22;
            case INFRASTRUCTURE,
                 CEMENT,
                 CAPITAL_GOODS   -> 25;
            case REAL_ESTATE     -> 20;
            case METAL_MINING    -> 12;
            case OIL_GAS         -> 10;
            case POWER           -> 15;
            case CHEMICALS       -> 28;
            case TELECOM         -> 20;
            case RETAIL          -> 30;
            case TEXTILE         -> 15;
            case HOSPITALITY     -> 25;
            case MEDIA           -> 20;
            default              -> 20;
        };
    }

    /** Typical net profit margin % for each Indian sector. */
    private double getSectorBenchmarkNetMargin() {
        if (sectorType == null) return 10;
        return switch (sectorType) {
            case IT              -> 18;
            case PHARMA          -> 12;
            case FMCG            -> 12;
            case BANKING, NBFC   -> 16;
            case AUTO            -> 6;
            case INFRASTRUCTURE  -> 5;
            case REAL_ESTATE     -> 8;
            case METAL_MINING    -> 8;
            case OIL_GAS         -> 5;
            case POWER           -> 8;
            case CHEMICALS       -> 10;
            case CEMENT          -> 8;
            case CAPITAL_GOODS   -> 7;
            case RETAIL          -> 4;
            case TEXTILE         -> 5;
            case TELECOM         -> 8;
            case HOSPITALITY     -> 6;
            case MEDIA           -> 8;
            default              -> 10;
        };
    }

    /** Maximum acceptable Debt/Equity for each sector (capital-intensive sectors allowed higher). */
    private double getSectorMaxDebtToEquity() {
        if (sectorType == null) return 1.0;
        return switch (sectorType) {
            case IT, FMCG          -> 0.3;
            case PHARMA            -> 0.5;
            case AUTO              -> 0.8;
            case INFRASTRUCTURE,
                 POWER,
                 REAL_ESTATE       -> 2.5;
            case CEMENT,
                 CAPITAL_GOODS     -> 1.0;
            case METAL_MINING      -> 1.5;
            case OIL_GAS           -> 1.5;
            case CHEMICALS         -> 0.8;
            case TELECOM           -> 2.0;
            case RETAIL            -> 0.5;
            case TEXTILE           -> 1.2;
            case HOSPITALITY       -> 2.0;
            case MEDIA             -> 0.5;
            default                -> 1.0;
        };
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Utility
    // ─────────────────────────────────────────────────────────────────────────

    private static String f(double v) { return String.format("%.2f", v); }

    @Override
    public String toString() {
        return "CStockFundamentalAnalysis{" +
                "symbol='" + stockSymbol + '\'' +
                ", company='" + companyName + '\'' +
                ", sector=" + sectorType +
                ", marketCap=" + marketCapCategory +
                ", score=" + fundamentalScore + "/100" +
                ", status=" + fundamentalStatus +
                ", sound=" + fundamentallySound +
                ", piotroski=" + piotroskiFScore + "/9" +
                ", graham=" + f(grahamNumberComputed) +
                ", passed=" + passedCriteria.size() +
                ", failed=" + failedCriteria.size() +
                ", warnings=" + warningFlags.size() +
                '}';
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Getters and Setters
    // ─────────────────────────────────────────────────────────────────────────

    // Identity
    public String getStockSymbol()                      { return stockSymbol; }
    public void   setStockSymbol(String v)              { this.stockSymbol = v; }
    public String getCompanyName()                      { return companyName; }
    public void   setCompanyName(String v)              { this.companyName = v; }
    public SectorType getSectorType()                   { return sectorType; }
    public void   setSectorType(SectorType v)           { this.sectorType = v; }
    public String getIndustry()                         { return industry; }
    public void   setIndustry(String v)                 { this.industry = v; }
    public MarketCapCategory getMarketCapCategory()     { return marketCapCategory; }
    public void   setMarketCapCategory(MarketCapCategory v) { this.marketCapCategory = v; }
    public double getMarketCapCr()                      { return marketCapCr; }
    public void   setMarketCapCr(double v)              { this.marketCapCr = v; }
    public String getExchange()                         { return exchange; }
    public void   setExchange(String v)                 { this.exchange = v; }
    public String getIsinCode()                         { return isinCode; }
    public void   setIsinCode(String v)                 { this.isinCode = v; }
    public String getNseCode()                          { return nseCode; }
    public void   setNseCode(String v)                  { this.nseCode = v; }
    public String getBseCode()                          { return bseCode; }
    public void   setBseCode(String v)                  { this.bseCode = v; }

    // Market Price
    public double getCurrentPrice()                     { return currentPrice; }
    public void   setCurrentPrice(double v)             { this.currentPrice = v; }
    public double getFiftyTwoWeekHigh()                 { return fiftyTwoWeekHigh; }
    public void   setFiftyTwoWeekHigh(double v)         { this.fiftyTwoWeekHigh = v; }
    public double getFiftyTwoWeekLow()                  { return fiftyTwoWeekLow; }
    public void   setFiftyTwoWeekLow(double v)          { this.fiftyTwoWeekLow = v; }
    public double getFaceValue()                        { return faceValue; }
    public void   setFaceValue(double v)                { this.faceValue = v; }

    // Per-Share
    public double getEps()                              { return eps; }
    public void   setEps(double v)                      { this.eps = v; }
    public double getEpsPreviousYear()                  { return epsPreviousYear; }
    public void   setEpsPreviousYear(double v)          { this.epsPreviousYear = v; }
    public double getBookValuePerShare()                { return bookValuePerShare; }
    public void   setBookValuePerShare(double v)        { this.bookValuePerShare = v; }
    public double getSalesPerShare()                    { return salesPerShare; }
    public void   setSalesPerShare(double v)            { this.salesPerShare = v; }
    public double getCashPerShare()                     { return cashPerShare; }
    public void   setCashPerShare(double v)             { this.cashPerShare = v; }
    public double getFcfPerShare()                      { return fcfPerShare; }
    public void   setFcfPerShare(double v)              { this.fcfPerShare = v; }

    // Valuation
    public double getPeRatio()                          { return peRatio; }
    public void   setPeRatio(double v)                  { this.peRatio = v; }
    public double getSectorPeRatio()                    { return sectorPeRatio; }
    public void   setSectorPeRatio(double v)            { this.sectorPeRatio = v; }
    public double getPbRatio()                          { return pbRatio; }
    public void   setPbRatio(double v)                  { this.pbRatio = v; }
    public double getPsRatio()                          { return psRatio; }
    public void   setPsRatio(double v)                  { this.psRatio = v; }
    public double getEvEbitda()                         { return evEbitda; }
    public void   setEvEbitda(double v)                 { this.evEbitda = v; }
    public double getPegRatio()                         { return pegRatio; }
    public void   setPegRatio(double v)                 { this.pegRatio = v; }
    public double getGrahamNumber()                     { return grahamNumber; }
    public void   setGrahamNumber(double v)             { this.grahamNumber = v; }
    public double getDividendYield()                    { return dividendYield; }
    public void   setDividendYield(double v)            { this.dividendYield = v; }
    public double getEnterpriseValueCr()                { return enterpriseValueCr; }
    public void   setEnterpriseValueCr(double v)        { this.enterpriseValueCr = v; }

    // Income Statement
    public double getRevenueTTM()                       { return revenueTTM; }
    public void   setRevenueTTM(double v)               { this.revenueTTM = v; }
    public double getRevenuePreviousYear()              { return revenuePreviousYear; }
    public void   setRevenuePreviousYear(double v)      { this.revenuePreviousYear = v; }
    public double getRevenue3YearsAgo()                 { return revenue3YearsAgo; }
    public void   setRevenue3YearsAgo(double v)         { this.revenue3YearsAgo = v; }
    public double getRevenue5YearsAgo()                 { return revenue5YearsAgo; }
    public void   setRevenue5YearsAgo(double v)         { this.revenue5YearsAgo = v; }
    public double getEbitdaTTM()                        { return ebitdaTTM; }
    public void   setEbitdaTTM(double v)                { this.ebitdaTTM = v; }
    public double getNetProfitTTM()                     { return netProfitTTM; }
    public void   setNetProfitTTM(double v)             { this.netProfitTTM = v; }
    public double getNetProfitPreviousYear()            { return netProfitPreviousYear; }
    public void   setNetProfitPreviousYear(double v)    { this.netProfitPreviousYear = v; }
    public double getGrossProfitTTM()                   { return grossProfitTTM; }
    public void   setGrossProfitTTM(double v)           { this.grossProfitTTM = v; }
    public double getInterestExpenseTTM()               { return interestExpenseTTM; }
    public void   setInterestExpenseTTM(double v)       { this.interestExpenseTTM = v; }
    public double getTaxRatePercent()                   { return taxRatePercent; }
    public void   setTaxRatePercent(double v)           { this.taxRatePercent = v; }

    // Balance Sheet
    public double getTotalAssets()                      { return totalAssets; }
    public void   setTotalAssets(double v)              { this.totalAssets = v; }
    public double getTotalDebt()                        { return totalDebt; }
    public void   setTotalDebt(double v)                { this.totalDebt = v; }
    public double getLongTermDebt()                     { return longTermDebt; }
    public void   setLongTermDebt(double v)             { this.longTermDebt = v; }
    public double getShortTermDebt()                    { return shortTermDebt; }
    public void   setShortTermDebt(double v)            { this.shortTermDebt = v; }
    public double getShareholdersEquity()               { return shareholdersEquity; }
    public void   setShareholdersEquity(double v)       { this.shareholdersEquity = v; }
    public double getTotalCapitalEmployed()             { return totalCapitalEmployed; }
    public void   setTotalCapitalEmployed(double v)     { this.totalCapitalEmployed = v; }
    public double getCurrentAssets()                    { return currentAssets; }
    public void   setCurrentAssets(double v)            { this.currentAssets = v; }
    public double getCurrentLiabilities()               { return currentLiabilities; }
    public void   setCurrentLiabilities(double v)       { this.currentLiabilities = v; }
    public double getInventory()                        { return inventory; }
    public void   setInventory(double v)                { this.inventory = v; }
    public double getAccountsReceivable()               { return accountsReceivable; }
    public void   setAccountsReceivable(double v)       { this.accountsReceivable = v; }
    public double getCashAndEquivalents()               { return cashAndEquivalents; }
    public void   setCashAndEquivalents(double v)       { this.cashAndEquivalents = v; }
    public double getRetainedEarnings()                 { return retainedEarnings; }
    public void   setRetainedEarnings(double v)         { this.retainedEarnings = v; }

    // Profitability
    public double getRoe()                              { return roe; }
    public void   setRoe(double v)                      { this.roe = v; }
    public double getRoePreviousYear()                  { return roePreviousYear; }
    public void   setRoePreviousYear(double v)          { this.roePreviousYear = v; }
    public double getRoce()                             { return roce; }
    public void   setRoce(double v)                     { this.roce = v; }
    public double getRoa()                              { return roa; }
    public void   setRoa(double v)                      { this.roa = v; }
    public double getRoaPreviousYear()                  { return roaPreviousYear; }
    public void   setRoaPreviousYear(double v)          { this.roaPreviousYear = v; }
    public double getNetProfitMargin()                  { return netProfitMargin; }
    public void   setNetProfitMargin(double v)          { this.netProfitMargin = v; }
    public double getNetProfitMarginPreviousYear()      { return netProfitMarginPreviousYear; }
    public void   setNetProfitMarginPreviousYear(double v) { this.netProfitMarginPreviousYear = v; }
    public double getEbitdaMargin()                     { return ebitdaMargin; }
    public void   setEbitdaMargin(double v)             { this.ebitdaMargin = v; }
    public double getEbitdaMarginPreviousYear()         { return ebitdaMarginPreviousYear; }
    public void   setEbitdaMarginPreviousYear(double v) { this.ebitdaMarginPreviousYear = v; }
    public double getGrossMargin()                      { return grossMargin; }
    public void   setGrossMargin(double v)              { this.grossMargin = v; }
    public double getGrossMarginPreviousYear()          { return grossMarginPreviousYear; }
    public void   setGrossMarginPreviousYear(double v)  { this.grossMarginPreviousYear = v; }

    // Growth
    public double getRevenueGrowthYoY()                 { return revenueGrowthYoY; }
    public void   setRevenueGrowthYoY(double v)         { this.revenueGrowthYoY = v; }
    public double getRevenueCagr3Y()                    { return revenueCagr3Y; }
    public void   setRevenueCagr3Y(double v)            { this.revenueCagr3Y = v; }
    public double getRevenueCagr5Y()                    { return revenueCagr5Y; }
    public void   setRevenueCagr5Y(double v)            { this.revenueCagr5Y = v; }
    public double getNetProfitGrowthYoY()               { return netProfitGrowthYoY; }
    public void   setNetProfitGrowthYoY(double v)       { this.netProfitGrowthYoY = v; }
    public double getEpsCagr3Y()                        { return epsCagr3Y; }
    public void   setEpsCagr3Y(double v)                { this.epsCagr3Y = v; }
    public double getEpsCagr5Y()                        { return epsCagr5Y; }
    public void   setEpsCagr5Y(double v)                { this.epsCagr5Y = v; }
    public double getEbitdaGrowthYoY()                  { return ebitdaGrowthYoY; }
    public void   setEbitdaGrowthYoY(double v)          { this.ebitdaGrowthYoY = v; }

    // Financial Health / Leverage
    public double getDebtToEquity()                     { return debtToEquity; }
    public void   setDebtToEquity(double v)             { this.debtToEquity = v; }
    public double getDebtToEquityPreviousYear()         { return debtToEquityPreviousYear; }
    public void   setDebtToEquityPreviousYear(double v) { this.debtToEquityPreviousYear = v; }
    public double getCurrentRatio()                     { return currentRatio; }
    public void   setCurrentRatio(double v)             { this.currentRatio = v; }
    public double getCurrentRatioPreviousYear()         { return currentRatioPreviousYear; }
    public void   setCurrentRatioPreviousYear(double v) { this.currentRatioPreviousYear = v; }
    public double getQuickRatio()                       { return quickRatio; }
    public void   setQuickRatio(double v)               { this.quickRatio = v; }
    public double getInterestCoverageRatio()            { return interestCoverageRatio; }
    public void   setInterestCoverageRatio(double v)    { this.interestCoverageRatio = v; }
    public double getDebtToEbitda()                     { return debtToEbitda; }
    public void   setDebtToEbitda(double v)             { this.debtToEbitda = v; }
    public double getDebtToAssets()                     { return debtToAssets; }
    public void   setDebtToAssets(double v)             { this.debtToAssets = v; }
    public double getLongTermDebtRatioPreviousYear()    { return longTermDebtRatioPreviousYear; }
    public void   setLongTermDebtRatioPreviousYear(double v) { this.longTermDebtRatioPreviousYear = v; }
    public int    getConsecutiveDebtReductionYears()    { return consecutiveDebtReductionYears; }
    public void   setConsecutiveDebtReductionYears(int v) { this.consecutiveDebtReductionYears = v; }

    // Banking Specific
    public double getNetInterestMargin()                { return netInterestMargin; }
    public void   setNetInterestMargin(double v)        { this.netInterestMargin = v; }
    public double getGrossNpaPercent()                  { return grossNpaPercent; }
    public void   setGrossNpaPercent(double v)          { this.grossNpaPercent = v; }
    public double getNetNpaPercent()                    { return netNpaPercent; }
    public void   setNetNpaPercent(double v)            { this.netNpaPercent = v; }
    public double getCapitalAdequacyRatio()             { return capitalAdequacyRatio; }
    public void   setCapitalAdequacyRatio(double v)     { this.capitalAdequacyRatio = v; }
    public double getProvisionCoverageRatio()           { return provisionCoverageRatio; }
    public void   setProvisionCoverageRatio(double v)   { this.provisionCoverageRatio = v; }
    public double getCostToIncomeRatio()                { return costToIncomeRatio; }
    public void   setCostToIncomeRatio(double v)        { this.costToIncomeRatio = v; }
    public double getCasaRatio()                        { return casaRatio; }
    public void   setCasaRatio(double v)                { this.casaRatio = v; }

    // Efficiency
    public double getAssetTurnoverRatio()               { return assetTurnoverRatio; }
    public void   setAssetTurnoverRatio(double v)       { this.assetTurnoverRatio = v; }
    public double getAssetTurnoverPreviousYear()        { return assetTurnoverPreviousYear; }
    public void   setAssetTurnoverPreviousYear(double v){ this.assetTurnoverPreviousYear = v; }
    public double getInventoryTurnoverRatio()           { return inventoryTurnoverRatio; }
    public void   setInventoryTurnoverRatio(double v)   { this.inventoryTurnoverRatio = v; }
    public double getDebtorDays()                       { return debtorDays; }
    public void   setDebtorDays(double v)               { this.debtorDays = v; }
    public double getPayableDays()                      { return payableDays; }
    public void   setPayableDays(double v)              { this.payableDays = v; }
    public double getCashConversionCycle()              { return cashConversionCycle; }
    public void   setCashConversionCycle(double v)      { this.cashConversionCycle = v; }
    public double getWorkingCapitalTurnover()           { return workingCapitalTurnover; }
    public void   setWorkingCapitalTurnover(double v)   { this.workingCapitalTurnover = v; }

    // Cash Flow
    public double getOperatingCashFlowTTM()             { return operatingCashFlowTTM; }
    public void   setOperatingCashFlowTTM(double v)     { this.operatingCashFlowTTM = v; }
    public double getFreeCashFlowTTM()                  { return freeCashFlowTTM; }
    public void   setFreeCashFlowTTM(double v)          { this.freeCashFlowTTM = v; }
    public double getCapexTTM()                         { return capexTTM; }
    public void   setCapexTTM(double v)                 { this.capexTTM = v; }
    public double getCashFlowToNetIncomeRatio()         { return cashFlowToNetIncomeRatio; }
    public void   setCashFlowToNetIncomeRatio(double v) { this.cashFlowToNetIncomeRatio = v; }
    public double getFcfYieldPercent()                  { return fcfYieldPercent; }
    public void   setFcfYieldPercent(double v)          { this.fcfYieldPercent = v; }
    public double getCapexToRevenuePercent()            { return capexToRevenuePercent; }
    public void   setCapexToRevenuePercent(double v)    { this.capexToRevenuePercent = v; }
    public boolean isConsistentPositiveOcf()            { return consistentPositiveOcf; }
    public void   setConsistentPositiveOcf(boolean v)   { this.consistentPositiveOcf = v; }

    // Dividend
    public double getDividendPerShare()                 { return dividendPerShare; }
    public void   setDividendPerShare(double v)         { this.dividendPerShare = v; }
    public double getDividendPayoutRatio()              { return dividendPayoutRatio; }
    public void   setDividendPayoutRatio(double v)      { this.dividendPayoutRatio = v; }
    public double getDividendGrowthRate()               { return dividendGrowthRate; }
    public void   setDividendGrowthRate(double v)       { this.dividendGrowthRate = v; }
    public int    getDividendConsistencyYears()         { return dividendConsistencyYears; }
    public void   setDividendConsistencyYears(int v)    { this.dividendConsistencyYears = v; }

    // Shareholding
    public double getPromoterHoldingPercent()           { return promoterHoldingPercent; }
    public void   setPromoterHoldingPercent(double v)   { this.promoterHoldingPercent = v; }
    public double getPromoterPledgePercent()            { return promoterPledgePercent; }
    public void   setPromoterPledgePercent(double v)    { this.promoterPledgePercent = v; }
    public double getFiiHoldingPercent()                { return fiiHoldingPercent; }
    public void   setFiiHoldingPercent(double v)        { this.fiiHoldingPercent = v; }
    public double getDiiHoldingPercent()                { return diiHoldingPercent; }
    public void   setDiiHoldingPercent(double v)        { this.diiHoldingPercent = v; }
    public double getPublicHoldingPercent()             { return publicHoldingPercent; }
    public void   setPublicHoldingPercent(double v)     { this.publicHoldingPercent = v; }
    public boolean isNewSharesIssuedLastYear()          { return newSharesIssuedLastYear; }
    public void   setNewSharesIssuedLastYear(boolean v) { this.newSharesIssuedLastYear = v; }

    // Analysis Results (read-only after performFullAnalysis)
    public double getGrahamNumberComputed()             { return grahamNumberComputed; }
    public double getPriceToGrahamNumber()              { return priceToGrahamNumber; }
    public int    getPiotroskiFScore()                  { return piotroskiFScore; }
    public int    getFundamentalScore()                 { return fundamentalScore; }
    public boolean isFundamentallySound()               { return fundamentallySound; }
    public FundamentalStatus getFundamentalStatus()     { return fundamentalStatus; }
    public List<String> getPassedCriteria()             { return Collections.unmodifiableList(passedCriteria); }
    public List<String> getFailedCriteria()             { return Collections.unmodifiableList(failedCriteria); }
    public List<String> getWarningFlags()               { return Collections.unmodifiableList(warningFlags); }
    public List<String> getAnalysisNotes()              { return Collections.unmodifiableList(analysisNotes); }
}
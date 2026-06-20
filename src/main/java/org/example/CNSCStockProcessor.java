package org.example;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitUntilState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);
    private static final String URL = "https://www.nseindia.com/get-quotes/equity?symbol=";
    private static final String DATA_FILE = "automation-data.txt";
    private static final int BATCH_SIZE = 10;
    private static final int MAX_NAV_RETRIES = 3;

    private static Vector<String> stockKeys = new Vector<String>();
    private static Vector<StockQuote> stockQuotes = new Vector<StockQuote>();
    private static Playwright playwright;
    private static Browser browser;
    private static Page page;

    public static void main(String[] args) {
        try {
            readLinesFromResource(DATA_FILE);
            setup();

            int totalSymbols = stockKeys.size();
            int totalSaved = 0;
            int processed = 0;

            for (String key : stockKeys) {
                processed++;
                try {
                    log.info("Processing [{}/{}] stock key: {}", processed, totalSymbols, key);
                    System.out.println(">>> [" + processed + "/" + totalSymbols + "] " + key);

                    readStockPriceInfo(URL + key, key);
                    Thread.sleep(2000); // brief pause to be polite to the server

                    if (stockQuotes.size() >= BATCH_SIZE) {
                        int batch = stockQuotes.size();
                        log.info("Batch threshold reached: saving {} quotes "
                                + "(processed {}/{}, cumulative saved before: {})",
                                batch, processed, totalSymbols, totalSaved);
                        System.out.println(">>> Saving batch of " + batch + " quotes "
                                + "(processed " + processed + "/" + totalSymbols
                                + ", cumulative saved before: " + totalSaved + ")");

                        writeStockQuotesToExcel();
                        totalSaved += batch;
                        stockQuotes.clear();

                        log.info("Batch saved. Cumulative total saved: {}", totalSaved);
                        System.out.println(">>> Batch saved. Cumulative total saved: " + totalSaved);
                    }
                } catch (InterruptedException e) {
                    log.warn("Sleep interrupted for symbol {}", key, e);
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    log.error("Failed to process symbol {}: {}", key, e.getMessage());
                }
            }

            // Final flush — write any remainder (< BATCH_SIZE) that's still pending
            if (!stockQuotes.isEmpty()) {
                int remainder = stockQuotes.size();
                log.info("Final flush: saving {} remaining quotes", remainder);
                System.out.println(">>> Final flush: " + remainder + " quotes...");

                writeStockQuotesToExcel();
                totalSaved += remainder;
                stockQuotes.clear();
            }

            log.info("DONE. Total quotes saved: {} / Total symbols processed: {}",
                    totalSaved, processed);
            System.out.println(">>> DONE. Total quotes saved: " + totalSaved
                    + " / Total symbols processed: " + processed
                    + " / Total symbols in file: " + totalSymbols);
        } catch (Exception e) {
            log.error("Playwright automation failed", e);
        } finally {
            tearDown();
        }
    }

    public static void readStockPriceInfo(String quoteUrl, String key) {
        log.info("Navigating to quote page: {}", quoteUrl);

        navigateWithRetry(quoteUrl, key);
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        page.waitForLoadState(LoadState.LOAD);
        page.waitForLoadState(LoadState.NETWORKIDLE);

        page.locator(".symbol-detail-section").first().waitFor(
                new com.microsoft.playwright.Locator.WaitForOptions().setTimeout(30_000));
        String symbolDetail = safeText(".symbol-detail-section");

        String stockName = "";
        page.locator("h1#top-section").first().waitFor(
                new com.microsoft.playwright.Locator.WaitForOptions().setTimeout(30_000));
        stockName = page.locator("h1#top-section").first().textContent().trim();
        System.out.println("Stock Name: " + stockName);

        StringBuilder sb = new StringBuilder();

        sb.append(symbolDetail).append("\n");
        System.out.println(symbolDetail);

        List<com.microsoft.playwright.Locator> cardBodies = page.locator("div.card-body").all();
        System.out.println("Card Body Elements Count: " + cardBodies.size());
        if (cardBodies.size() >= 4) {
            sb.append(cardBodies.get(0).textContent().trim()).append("\n");
            sb.append(cardBodies.get(1).textContent().trim()).append("\n");
        }
        String cardBodyText = sb.toString().replace("?", "");
        System.out.println(cardBodyText);

        parseAndStoreMetrics(cardBodyText, key, stockName);

        System.out.println("==============================");
    }

    /**
     * Navigates to the given URL, retrying on transient network failures such as
     * net::ERR_HTTP2_PROTOCOL_ERROR / ERR_NETWORK_CHANGED / ERR_CONNECTION_RESET.
     * Each retry uses exponential backoff. Throws the last error if all attempts fail.
     */
    private static void navigateWithRetry(String quoteUrl, String key) {
        RuntimeException lastError = null;
        for (int attempt = 1; attempt <= MAX_NAV_RETRIES; attempt++) {
            try {
                page.navigate(quoteUrl,
                        new Page.NavigateOptions()
                                .setWaitUntil(WaitUntilState.LOAD)
                                .setTimeout(60_000));
                return; // success
            } catch (RuntimeException e) {
                lastError = e;
                String msg = e.getMessage() == null ? "" : e.getMessage();
                boolean transientNetwork = msg.contains("ERR_HTTP2_PROTOCOL_ERROR")
                        || msg.contains("ERR_NETWORK_CHANGED")
                        || msg.contains("ERR_CONNECTION_RESET")
                        || msg.contains("ERR_CONNECTION_CLOSED")
                        || msg.contains("ERR_TIMED_OUT")
                        || msg.contains("Timeout");

                if (!transientNetwork || attempt == MAX_NAV_RETRIES) {
                    break;
                }

                long backoffMs = 2000L * attempt;
                log.warn("Navigation attempt {}/{} for {} failed ({}). Retrying in {} ms",
                        attempt, MAX_NAV_RETRIES, key, msg, backoffMs);
                try {
                    Thread.sleep(backoffMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted during navigation retry for " + key, ie);
                }
            }
        }
        throw lastError;
    }

    public static void parseAndStoreMetrics(String input, String key, String name) {

        Vector<StockMetric> stockMetrics = new Vector<StockMetric>();
        if (input == null || input.isBlank()) {
            log.warn("parseAndStoreMetrics received empty input");
            return;
        }

        // Collapse all whitespace (newlines, tabs, multi-space) into a single space
        String cleaned = input.replaceAll("\\s+", " ").trim();

        // Value patterns (tried left-to-right):
        //  1) Decimal with comma groups — supports BOTH Western (,\d{3}) and Indian
        //     lakh/crore (,\d{2}) groupings, e.g. 10,743.27 / 2,637.10% / 17,70,056.06
        //  2) Plain decimal with 1-2 decimal places, e.g. 569.25 or 28.23%
        //  3) Integer followed by a letter or end-of-string, e.g. 20 in "20Tick"
        //     Negative lookahead skips "52 Week ..." so "52" stays attached to its label.
        //  4) Literal "No Band" — only when it appears right after "(%)" (Price Band)
        //  5) Standalone '-' immediately after '*' (NSE shows "Close *-" for missing)
        Pattern p = Pattern.compile(
                "\\d{1,3}(?:,\\d{2,3})+\\.\\d{1,2}%?"
                        + "|\\d+\\.\\d{1,2}%?"
                        + "|\\d+(?!\\s*Week\\b)(?=\\s*[A-Za-z]|$)"
                        + "|(?<=\\(%\\))\\s*No\\s+Band\\b"
                        + "|(?<=\\*)\\s*-");

        Matcher m = p.matcher(cleaned);
        int last = 0;
        int added = 0;
        while (m.find()) {
            String label = cleaned.substring(last, m.start()).trim();
            String value = m.group().trim();
            last = m.end();
            if (label.isEmpty()) {
                continue;
            }
            StockMetric metric = new StockMetric(label, value);
            stockMetrics.add(metric);
            System.out.println(metric);
            added++;
        }
        StockQuote eachOne = buildStockQuote(stockMetrics);
        eachOne.setKey(key);
        eachOne.setName(name);
        eachOne.setChange("");
        eachOne.setChangePercentage("%");
        eachOne.setTradedValuePercentage("");
        stockQuotes.add(eachOne);

        log.info("parseAndStoreMetrics extracted {} metrics (total stored: {})",
                added, stockMetrics.size());
    }

    public static StockQuote buildStockQuote(Vector<StockMetric> metrics) {
        StockQuote q = new StockQuote();
        if (metrics == null || metrics.isEmpty()) {
            log.warn("buildStockQuote received empty metrics vector");
            return q;
        }

        for (StockMetric m : metrics) {
            String label = m.getLabel() == null ? "" : m.getLabel().trim();
            String value = m.getValue() == null ? "" : m.getValue().trim();

            // Normalise any "(<anything> Cr.)" or "(Cr.)" variation to "(Cr)".
            // Handles literal "?", Indian Rupee sign "₹", stray spaces, etc.
            label = label.replaceAll("\\([^)]*Cr\\.\\)", "(Cr)");

            // Normalise "52 Week High (any-date)" / "52 Week Low (any-date)" by dropping the date
            label = label.replaceAll("^52 Week High\\s*\\([^)]*\\)\\s*$", "52 Week High");
            label = label.replaceAll("^52 Week Low\\s*\\([^)]*\\)\\s*$", "52 Week Low");
            // Fallback: if the "52 " prefix was lost upstream (old parsing), still match
            label = label.replaceAll("^Week High\\s*\\([^)]*\\)\\s*$", "52 Week High");
            label = label.replaceAll("^Week Low\\s*\\([^)]*\\)\\s*$", "52 Week Low");

            switch (label) {
                case "Prev. Close":                         q.setPrevClose(value); break;
                case "Open":                                q.setOpen(value); break;
                case "High":                                q.setHigh(value); break;
                case "Low":                                 q.setLow(value); break;
                case "Close *":                             q.setClose(value); break;
                case "VWAP":                                q.setVwap(value); break;
                case "Adjusted Price *":                    q.setAdjustedPrice(value); break;
                case "Traded Volume (Lakhs)":               q.setTradedVolumeLakhs(value); break;
                case "Traded Value (Cr)":                   q.setTradedValueCr(value); break;
                case "Total Market Cap (Cr)":               q.setTotalMarketCapCr(value); break;
                case "Free Float Market Cap (Cr)":          q.setFreeFloatMarketCapCr(value); break;
                case "Impact cost":                         q.setImpactCost(value); break;
                case "Face Value":                          q.setFaceValue(value); break;
                case "Applicable Margin Rate":              q.setApplicableMarginRate(value); break;
                case "of Deliverable / Traded Quantity":    q.setDeliverableTradedQuantityPct(value); break;
                case "52 Week High":                        q.setWeekHigh52(value); break;
                case "52 Week Low":                         q.setWeekLow52(value); break;
                case "Upper Band":                          q.setUpperBand(value); break;
                case "Lower Band":                          q.setLowerBand(value); break;
                case "Price Band (%)":                      q.setPriceBandPct(value); break;
                case "Tick Size":                           q.setTickSize(value); break;
                case "Daily Volatility":                    q.setDailyVolatility(value); break;
                case "Annualised Volatility":               q.setAnnualisedVolatility(value); break;
                default:
                    log.debug("Unmapped metric label: '{}' -> '{}'", label, value);
                    break;
            }
        }

        System.out.println("Built StockQuote: " + q);
        return q;
    }

    public static void writeStockQuotesToExcel() {
        if (stockQuotes == null || stockQuotes.isEmpty()) {
            log.warn("writeStockQuotesToExcel received empty quotes vector");
            return;
        }

        // Build file name: NSC_STOCKS_MMM_DD.xlsx e.g. NSC_STOCKS_Jun_04.xlsx
        String stamp = LocalDate.now().format(DateTimeFormatter.ofPattern("MMM_dd", Locale.ENGLISH));
        String fileName = "NSC_STOCKS_" + stamp + ".xlsx";
        File file = new File(fileName);

        Field[] fields = StockQuote.class.getDeclaredFields();
        for (Field f : fields) {
            f.setAccessible(true);
        }

        Workbook workbook = null;
        try {
            Sheet sheet;
            if (file.exists()) {
                try (FileInputStream fis = new FileInputStream(file)) {
                    workbook = new XSSFWorkbook(fis);
                }
                sheet = workbook.getSheetAt(0);
            } else {
                workbook = new XSSFWorkbook();
                sheet = workbook.createSheet("NSE Stocks");
            }

            // If first row is empty, write the StockQuote property names as headers
            Row firstRow = sheet.getRow(0);
            boolean firstRowEmpty = isRowEmpty(firstRow);
            if (firstRowEmpty) {
                Row header = sheet.createRow(0);
                for (int i = 0; i < fields.length; i++) {
                    header.createCell(i).setCellValue(fields[i].getName());
                }
            }

            // Append data rows starting after the last existing row
            int nextRow = sheet.getLastRowNum() + 1;
            if (nextRow == 0) {
                nextRow = 1; // headers occupy row 0
            }

            for (StockQuote q : stockQuotes) {
                Row row = sheet.createRow(nextRow++);
                for (int i = 0; i < fields.length; i++) {
                    Object val = fields[i].get(q);
                    row.createCell(i).setCellValue(val == null ? "" : val.toString());
                }
            }

            try (FileOutputStream fos = new FileOutputStream(file)) {
                workbook.write(fos);
            }

            log.info("Wrote {} stock quotes to {}", stockQuotes.size(), file.getAbsolutePath());
            System.out.println("Excel written: " + file.getAbsolutePath());
        } catch (IOException | IllegalAccessException e) {
            log.error("Failed to write Excel file {}", fileName, e);
        } finally {
            if (workbook != null) {
                try {
                    workbook.close();
                } catch (IOException e) {
                    log.warn("Failed to close workbook", e);
                }
            }
        }
    }

    private static boolean isRowEmpty(Row row) {
        if (row == null) {
            return true;
        }
        for (int c = row.getFirstCellNum(); c < row.getLastCellNum(); c++) {
            Cell cell = row.getCell(c);
            if (cell != null && !cell.toString().isBlank()) {
                return false;
            }
        }
        return true;
    }

    private static String safeText(String selector) {
        try {
            return page.locator(selector).first().textContent().trim();
        } catch (Exception e) {
            log.warn("Could not read element {}: {}", selector, e.getMessage());
            return "";
        }
    }

    public static void readLinesFromResource(String resourceName) {
        ClassLoader classLoader = Main.class.getClassLoader();
        try (InputStream is = classLoader.getResourceAsStream(resourceName)) {
            if (is == null) {
                log.error("Resource not found on classpath: {}", resourceName);
                return;
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.isBlank()) {
                        stockKeys.add(line.trim());
                    }
                }
            }
        } catch (IOException e) {
            log.error("Failed to read resource {}", resourceName, e);
        }
    }

    private static void setup() {
        playwright = Playwright.create();
        browser = playwright.chromium()
                .launch(new BrowserType.LaunchOptions()
                        .setHeadless(false)
                        .setArgs(java.util.Arrays.asList(
                                "--disable-gpu",
                                "--no-sandbox",
                                "--disable-dev-shm-usage",
                                "--disable-background-timer-throttling",
                                "--disable-renderer-backgrounding",
                                "--disable-backgrounding-occluded-windows",
                                // Disable HTTP/2 — works around intermittent
                                // net::ERR_HTTP2_PROTOCOL_ERROR from nseindia.com
                                "--disable-http2")));
        page = browser.newPage();
    }

    private static void tearDown() {
        if (page != null) {
            page.close();
        }
        if (browser != null) {
            browser.close();
        }
        if (playwright != null) {
            playwright.close();
        }
    }
}
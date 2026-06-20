package org.example;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitUntilState;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Dimension;
import java.awt.Toolkit;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Vector;

public class CETProcessor {

    private static final Logger log = LoggerFactory.getLogger(CETProcessor.class);
    private static final String ET_SECTORS_URL = "https://economictimes.indiatimes.com/mutual-funds/mutual-funds-search/";
    private static final String MOBILE_NUMBER = "9848869839";
    private static final String PASSWORD = "Nag295238*";
    private static boolean isLoginRequired = false;

    private static Playwright playwright;
    private static Browser browser;
    private static Page page;
    private static Vector<EtSector> sectors = new Vector<>();
    private static Vector<MutualFundEntry> mutualFunds = new Vector<>();
    private static Vector<String>       fundMetricsSummaries = new Vector<>();
    private static Vector<FundProperty>     fundProperties       = new Vector<>();
    private static Vector<CEquityFundDetail> equityFundDetails   = new Vector<>();
    // Key = FundName — populated from existing type file so already-processed URLs are skipped
    private static final java.util.Map<String, MutualFundEntry> processedFunds = new java.util.HashMap<>();

    public static void main(String[] args) {
        try {
            setup();
            if (isLoginRequired) {
                loadAndClickLogin();
            } else {
                //loadDirectly();
                processFromExcel("ET_MUTUAL_FUNDS_Jun_18.xlsx");
            }
            //captureSectorDetails();
        } catch (Exception e) {
            log.error("CETProcessor failed", e);
        } finally {
            tearDown();
        }
    }

    public static void loadAndClickLogin() {
        log.info("Navigating to: {}", ET_SECTORS_URL);
        page.navigate(ET_SECTORS_URL,
                new Page.NavigateOptions()
                        .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
                        .setTimeout(120_000));

        // Wait for the Sign In link inside div.Login_defaultLink__gFZqA and click it
        Locator loginLink = page.locator("div.Login_defaultLink__gFZqA").first();
        loginLink.waitFor(new Locator.WaitForOptions().setTimeout(120_000));
        log.info("Login link found: {}", loginLink.textContent());
        loginLink.click();

        log.info("Login link clicked — waiting for login popup to load");
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        log.info("Current URL after click: {}", page.url());

        enterMobileNumber();
    }

    public static void loadDirectly() {
        log.info("Loading mutual fund pages for letters a–z");
        mutualFunds.clear();

        String dateSuffix = LocalDate.now()
                .format(DateTimeFormatter.ofPattern("MMM_dd", Locale.ENGLISH));
        String filePath = "C:\\StockMarket_Research\\ET_MUTUAL_FUNDS_" + dateSuffix + ".xlsx";

        java.io.File dir = new java.io.File("C:\\StockMarket_Research");
        if (!dir.exists()) dir.mkdirs();

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("MutualFunds");

            Row headerRow = sheet.createRow(0);
            headerRow.createCell(0).setCellValue("mutualFundName");
            headerRow.createCell(1).setCellValue("mutualFundLink");

            int rowIdx = 1;

            for (char letter = 'a'; letter <= 'z'; letter++) {
                String url = ET_SECTORS_URL + letter;
                log.info("Navigating to: {}", url);

                page.navigate(url, new Page.NavigateOptions()
                        .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
                        .setTimeout(120_000));
                log.info("Page loaded for letter '{}' — URL: {}", letter, page.url());

                List<Locator> links = page.locator("ul.companyList a").all();
                log.info("Found {} fund links for letter '{}'", links.size(), letter);

                int addedForLetter = 0;
                for (Locator link : links) {
                    String name = link.textContent().trim();
                    String href = link.getAttribute("href");
                    if (!name.isBlank() && href != null && !href.isBlank()) {
                        MutualFundEntry entry = new MutualFundEntry();
                        entry.setMutualFundName(name);
                        entry.setMutualFundLink(href);
                        mutualFunds.add(entry);

                        Row row = sheet.createRow(rowIdx++);
                        row.createCell(0).setCellValue(name);
                        row.createCell(1).setCellValue(href);

                        log.info("Fund: {} -> {}", name, href);
                        System.out.println(entry);
                        addedForLetter++;
                    }
                }

                // Flush to disk after every letter
                try (FileOutputStream fos = new FileOutputStream(filePath)) {
                    workbook.write(fos);
                }
                log.info("Letter '{}': {} entries written, running total: {}", letter, addedForLetter, rowIdx - 1);
                System.out.println("Letter '" + letter + "' done — " + addedForLetter + " entries, total: " + (rowIdx - 1));
            }

            // Final save with auto-sized columns
            sheet.autoSizeColumn(0);
            sheet.autoSizeColumn(1);
            try (FileOutputStream fos = new FileOutputStream(filePath)) {
                workbook.write(fos);
            }

        } catch (IOException e) {
            log.error("Failed to write mutual funds to Excel", e);
        }

        log.info("Total mutual funds captured: {} — saved to {}", mutualFunds.size(), filePath);
        System.out.println("Total mutual funds captured: " + mutualFunds.size());
        System.out.println("Excel saved: " + filePath);
    }

    public static void processFromExcel(String excelFileName) {
        String filePath = "C:\\StockMarket_Research\\" + excelFileName;
        log.info("Loading mutual fund entries from: {}", filePath);

        // Step 1: read Excel → populate mutualFunds Vector
        mutualFunds.clear();
        DataFormatter fmt = new DataFormatter();

        try (FileInputStream fis = new FileInputStream(filePath);
             Workbook workbook = new XSSFWorkbook(fis)) {

            Sheet sheet = workbook.getSheetAt(0);
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                String name = fmt.formatCellValue(row.getCell(0)).trim();
                String link = fmt.formatCellValue(row.getCell(1)).trim();

                if (!name.isBlank() && !link.isBlank()) {
                    MutualFundEntry entry = new MutualFundEntry();
                    entry.setMutualFundName(name);
                    entry.setMutualFundLink(link);
                    mutualFunds.add(entry);
                }
            }

        } catch (IOException e) {
            log.error("Failed to read Excel file: {}", filePath, e);
            return;
        }

        log.info("Loaded {} mutual fund entries from Excel", mutualFunds.size());
        System.out.println("Loaded " + mutualFunds.size() + " mutual funds from Excel");

        // Step 2: visit each fund URL, collect metrics into a per-fund StringBuilder
        fundMetricsSummaries.clear();
        int total = mutualFunds.size();

        String typeFileSuffix = LocalDate.now().format(DateTimeFormatter.ofPattern("MMM_dd", Locale.ENGLISH));
        String typeFilePath   = "C:\\StockMarket_Research\\ET_MUTUAL_FUNDS_TYPE_"   + typeFileSuffix + ".xlsx";
        String detailFilePath = "C:\\StockMarket_Research\\ET_EQUITY_DETAILS_"      + typeFileSuffix + ".xlsx";
        String rawFilePath    = "C:\\StockMarket_Research\\ET_EQUITY_FUND_RAW_"     + typeFileSuffix + ".xlsx";

        // Load already-processed fund names so we can skip them in the loop
        loadProcessedFunds(typeFilePath);


// && idx < 20
        for (int idx = 0; idx < total; idx++) {
            MutualFundEntry entry = mutualFunds.get(idx);
            int current = idx + 1;

            // Skip if already processed in a previous run
            if (processedFunds.containsKey(entry.getMutualFundName())) {
                //log.info("[{}/{}] Already processed — skipping '{}'", current, total, entry.getMutualFundName());
                System.out.println("[" + current + "/" + total + "] Skipping (already processed): " + entry.getMutualFundName());
                continue;
            }

            try {
                page.navigate(entry.getMutualFundLink(), new Page.NavigateOptions()
                        .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
                        .setTimeout(120_000));
                page.locator("section#mfHeader").waitFor(
                        new Locator.WaitForOptions().setTimeout(20_000));

                List<Locator> items = page.locator("section#mfHeader ul.metrics:not(.select_dropdown) > li").all();

                StringBuilder sb = new StringBuilder();
                sb.append("Fund: ").append(entry.getMutualFundName()).append("\n");
                sb.append("Link: ").append(entry.getMutualFundLink()).append("\n");

                for (Locator item : items) {
                    String text = item.textContent().trim();
                    if (!text.isBlank()) {
                        sb.append("  ").append(text).append("\n");
                    }
                }

                String summary = sb.toString();

                String fundCategory = summary.lines()
                        .filter(line -> line.trim().toLowerCase().startsWith("fund category"))
                        .map(line -> { int ci = line.indexOf(':'); return ci > 0 ? line.substring(ci + 1).trim() : ""; })
                        .findFirst().orElse("");
                entry.setFundType(fundCategory);

                appendTypeRow(typeFilePath, entry.getMutualFundName(), entry.getMutualFundLink(), fundCategory);

                boolean isEquity = fundCategory.toLowerCase().contains("equity");

                if (isEquity) {
                    System.out.println("[" + current + "/" + total + "] Loading: " + entry.getMutualFundName() + " → " + entry.getMutualFundLink());
                    page.waitForLoadState(LoadState.DOMCONTENTLOADED,
                            new Page.WaitForLoadStateOptions().setTimeout(60_000));

                    String expenseRatio = summary.lines()
                            .filter(line -> line.trim().toLowerCase().startsWith("expense ratio"))
                            .map(line -> { int ci = line.indexOf(':'); return ci > 0 ? line.substring(ci + 1).trim() : ""; })
                            .findFirst().orElse("");
                    entry.setExpenseRatio(expenseRatio);

                    String fundSize = summary.lines()
                            .filter(line -> line.trim().toLowerCase().startsWith("fund size"))
                            .map(line -> { int ci = line.indexOf(':'); return ci > 0 ? line.substring(ci + 1).trim() : ""; })
                            .findFirst().orElse("");
                    entry.setFundSize(fundSize);

                    fundMetricsSummaries.add(summary);
                    String fundName = entry.getMutualFundName();
                    Vector<FundProperty> props = new Vector<>();

                    // --- Parse mfHeader summary metrics (Scheme+NAV grouped, Company+Sector+Asset grouped) ---
                    java.util.Map<String, String> summaryMetrics = new java.util.LinkedHashMap<>();
                    for (String line : summary.lines().toList()) {
                        String trimmed = line.trim();
                        if (trimmed.isBlank()) continue;
                        int ci = trimmed.indexOf(':');
                        if (ci > 0) {
                            String key = trimmed.substring(0, ci).trim();
                            String val = trimmed.substring(ci + 1).trim();
                            if (!key.equalsIgnoreCase("fund") && !key.equalsIgnoreCase("link"))
                                summaryMetrics.put(key, val);
                        }
                    }
                    props.addAll(groupMetrics(fundName, summaryMetrics));

                    // --- Extract div.tabsContent details (Scheme+NAV grouped, Company+Sector+Asset grouped) ---
                    java.util.Map<String, String> tabMetrics = new java.util.LinkedHashMap<>();
                    for (Locator tabItem : page.locator("div.tabsContent li").all()) {
                        String text = tabItem.textContent().trim();
                        if (text.isBlank()) continue;
                        int ci = text.indexOf(':');
                        if (ci > 0) tabMetrics.put(text.substring(0, ci).trim(), text.substring(ci + 1).trim());
                        else        tabMetrics.put(text, "");
                    }
                    props.addAll(groupMetrics(fundName, tabMetrics));

                    // --- Extract strip table (one FundProperty per row) ---
                    List<Locator> stripTables = page.locator("table.with_strip.sm_font.no_bottom.first_col_bold").all();
                    if (!stripTables.isEmpty()) {
                        Locator stripTable = stripTables.get(0);
                        List<String> stripHeaders = new ArrayList<>();
                        for (Locator th : stripTable.locator("thead tr th").all()) {
                            stripHeaders.add(th.textContent().trim());
                        }
                        for (Locator tableRow : stripTable.locator("tbody tr").all()) {
                            List<Locator> tds = tableRow.locator("td").all();
                            if (tds.isEmpty()) continue;
                            StringBuilder rowVal = new StringBuilder();
                            for (int col = 0; col < tds.size(); col++) {
                                String colHeader = col < stripHeaders.size() ? stripHeaders.get(col) : "col_" + col;
                                String cellValue = tds.get(col).textContent().trim();
                                if (!cellValue.isBlank()) {
                                    if (rowVal.length() > 0) rowVal.append(" | ");
                                    rowVal.append(colHeader).append(": ").append(cellValue);
                                }
                            }
                            if (rowVal.length() > 0) {
                                props.add(new FundProperty(fundName, "Fund Details", rowVal.toString()));
                            }
                        }
                    }

                    // --- Step 1: Extract Top Stock Holdings from table.table.with_strip ---
                    // Table is already rendered in DOM (active by default before any tab click)
                    int propsBeforeTopStocks = props.size();
                    Locator topStockTableLocator = page.locator("table.table.with_strip");
                    if (topStockTableLocator.count() > 0) {
                        Locator tbl = topStockTableLocator.first();
                        tbl.waitFor(new Locator.WaitForOptions().setTimeout(30_000));

                        List<String> headers = new ArrayList<>();
                        for (Locator th : tbl.locator("thead tr th").all()) {
                            String h = th.textContent().trim().replaceAll("\\s+", " ");
                            headers.add(h.isBlank() ? "Col_" + (headers.size() + 1) : h);
                        }
                       // log.info("Top Stock Holdings headers ({}): {}", headers.size(), headers);
                        //System.out.println("Top Stock Holdings headers: " + headers);

                        for (Locator row : tbl.locator("tbody tr").all()) {
                            List<Locator> tds = row.locator("td").all();
                            if (tds.isEmpty()) continue;
                            StringBuilder rowVal = new StringBuilder();
                            for (int col = 0; col < tds.size(); col++) {
                                String colHeader = col < headers.size() ? headers.get(col) : "Col_" + (col + 1);
                                String cellValue = tds.get(col).textContent().trim().replaceAll("\\s+", " ");
                                if (!cellValue.isBlank()) {
                                    if (!rowVal.isEmpty()) rowVal.append(" | ");
                                    rowVal.append(colHeader).append(": ").append(cellValue);
                                }
                            }
                            if (!rowVal.isEmpty()) {
                                props.add(new FundProperty(fundName, "Top Stock Holdings", rowVal.toString()));
                            }
                        }
                    }
                    //log.info("Top Stock Holdings done — {} rows for '{}'",
                     //       props.size() - propsBeforeTopStocks, fundName);
                    //System.out.println("Top Stock Holdings rows: " + (props.size() - propsBeforeTopStocks));

                    // --- Step 2: Extract Top Stock Holdings from div.tabsCustomMarkets.mt_20 > div.tabsContent ---
                    // div.tabsContent is the default active panel — Top Stock Holdings is loaded in DOM already.
                    // Scoped strictly inside tabsCustomMarkets so we don't pick up any other tabsContent on page.
                    Locator tabsCustomContainer = page.locator("div.tabsCustomMarkets.mt_20").first();
                    if (tabsCustomContainer.isVisible()) {
                        Locator tabsContentPanel = tabsCustomContainer.locator("div.tabsContent").first();
                        tabsContentPanel.waitFor(new Locator.WaitForOptions().setTimeout(30_000));

                        // Collect column headers from thead
                        List<String> tsHeaders = new ArrayList<>();
                        for (Locator th : tabsContentPanel.locator("thead tr th").all()) {
                            String h = th.textContent().trim().replaceAll("\\s+", " ");
                            tsHeaders.add(h.isBlank() ? "Col_" + (tsHeaders.size() + 1) : h);
                        }
                        //log.info("Top Stock Holdings headers ({}): {}", tsHeaders.size(), tsHeaders);
                        //System.out.println("Top Stock Holdings headers: " + tsHeaders);

                        // Extract each tbody tr — one FundProperty per data row
                        int propsBeforeTopStockInner = props.size();
                        for (Locator row : tabsContentPanel.locator("tbody tr").all()) {
                            List<Locator> tds = row.locator("td").all();
                            if (tds.isEmpty()) continue;  // skip spacer / th-only rows
                            StringBuilder rowVal = new StringBuilder();
                            for (int col = 0; col < tds.size(); col++) {
                                String colHeader = col < tsHeaders.size() ? tsHeaders.get(col) : "Col_" + (col + 1);
                                String cellValue = tds.get(col).textContent().trim().replaceAll("\\s+", " ");
                                if (!cellValue.isBlank()) {
                                    if (!rowVal.isEmpty()) rowVal.append(" | ");
                                    rowVal.append(colHeader).append(": ").append(cellValue);
                                }
                            }
                            if (!rowVal.isEmpty()) {
                                props.add(new FundProperty(fundName, "Top Stock Holdings", rowVal.toString()));
                            }
                        }
                       //log.info("Top Stock Holdings (tabsContent) done — {} rows for '{}'",
                               // props.size() - propsBeforeTopStockInner, fundName);
                       // System.out.println("Top Stock Holdings (tabsContent) rows: "
                               // + (props.size() - propsBeforeTopStockInner));
                    }

                    // --- Step 3: Click Sector Holdings in MF tab, extract after active ---
                    // Existing sector extraction — untouched
                    List<Locator> marketTabContainers = page.locator("div.tabsCustomMarkets.mt_20").all();
                    if (!marketTabContainers.isEmpty()) {
                        Locator container = marketTabContainers.get(0);
                        int propsBeforeSector = props.size();
                        extractTabSection(container, "Sector Holdings in MF", fundName, props);
                       // log.info("Sector Holdings in MF done — {} rows extracted for '{}'",
                         //       props.size() - propsBeforeSector, fundName);
                    }

                    // --- Step 3: After sector done, click Top Stock Holdings (h3 tab) ---
                    //             then extract div.tabsContent thead + tbody as single rows
                    Locator tabsMarket = page.locator("div.tabsCustomMarkets.mt_20").first();
                    if (tabsMarket.isVisible()) {
                        Locator topStockTrigger = tabsMarket.locator("li[data-url]")
                                .filter(new Locator.FilterOptions().setHasText("Top Stock Holdings"))
                                .first();
                        if (topStockTrigger.isVisible()) {
                            topStockTrigger.click();
                            tabsMarket.locator("li[data-url].active")
                                    .filter(new Locator.FilterOptions().setHasText("Top Stock Holdings"))
                                    .first()
                                    .waitFor(new Locator.WaitForOptions().setTimeout(30_000));
                          //  log.info("Top Stock Holdings tab is now active");

                            Locator tabsContentDiv = page.locator("div.tabsContent").first();
                            tabsContentDiv.waitFor(new Locator.WaitForOptions().setTimeout(30_000));

                            int propsBeforeTopStockTab = props.size();
                            List<String> tsHeaders = new ArrayList<>();
                            for (Locator th : tabsContentDiv.locator("thead tr th").all()) {
                                String h = th.textContent().trim().replaceAll("\\s+", " ");
                                tsHeaders.add(h.isBlank() ? "Col_" + (tsHeaders.size() + 1) : h);
                            }
                            //log.info("Top Stock Holdings (tab) headers ({}): {}", tsHeaders.size(), tsHeaders);
                           // System.out.println("Top Stock Holdings (tab) headers: " + tsHeaders);

                            for (Locator row : tabsContentDiv.locator("tbody tr").all()) {
                                List<Locator> tds = row.locator("td").all();
                                if (tds.isEmpty()) continue;
                                StringBuilder rowVal = new StringBuilder();
                                for (int col = 0; col < tds.size(); col++) {
                                    String colHeader = col < tsHeaders.size() ? tsHeaders.get(col) : "Col_" + (col + 1);
                                    String cellValue = tds.get(col).textContent().trim().replaceAll("\\s+", " ");
                                    if (!cellValue.isBlank()) {
                                        if (!rowVal.isEmpty()) rowVal.append(" | ");
                                        rowVal.append(colHeader).append(": ").append(cellValue);
                                    }
                                }
                                if (!rowVal.isEmpty()) {
                                    props.add(new FundProperty(fundName, "Top Stock Holdings", rowVal.toString()));
                                }
                            }
                           // log.info("Top Stock Holdings (tab) done — {} rows for '{}'",
                           //         props.size() - propsBeforeTopStockTab, fundName);
                            //System.out.println("Top Stock Holdings (tab) rows: "
                                   // + (props.size() - propsBeforeTopStockTab));
                        }
                    }

                    fundProperties.addAll(props);

                    // Populate entry.properties — use propertyName as key when unique,
                    // fall back to "Property-N" for duplicates or blank names
                    java.util.Map<String, Integer> keyCount = new java.util.HashMap<>();
                    int propCounter = 1;
                    for (FundProperty fp : props) {
                        String key = fp.getPropertyName();
                        if (key == null || key.isBlank()) {
                            key = "Property-" + propCounter++;
                        } else {
                            int cnt = keyCount.merge(key, 1, Integer::sum);
                            if (cnt > 1) key = fp.getPropertyName() + "-" + cnt;
                        }
                        entry.addProperty(key, fp.getPropertyValue());
                    }

                    // Store CEquityFundDetail before moving to next URL
                    CEquityFundDetail detail = new CEquityFundDetail(
                            fundName, entry.getMutualFundLink(),
                            entry.getFundType(), expenseRatio, fundSize);
                    detail.loadFrom(props);
                    equityFundDetails.add(detail);
                   // log.info("Stored CEquityFundDetail '{}' — stocks:{} sectors:{} other:{}",
                    //        fundName, detail.getTopStockHoldings().size(),
                   //         detail.getSectorHoldings().size(), detail.getOtherProperties().size());
                    //System.out.println("Stored: " + detail);

                    // ── Write all 4 sheets via locked append helper ───────────────────
                    appendDetailRows(detailFilePath, detail);
                    appendRawRows(rawFilePath, fundName, props);
                    //log.info("Workbook persisted for '{}' — stocks:{} sectors:{} fundDetails:{}",
                     //       fundName, detail.getTopStockHoldings().size(),
                     //       detail.getSectorHoldings().size(), detail.getFundDetails().size());

                    //log.info("[{}/{}] Equity — {} | {} properties (header+tabs) | summaries: {}", current, total, fundName, props.size(), fundMetricsSummaries.size());
                    System.out.println("[" + current + "/" + total + "] Equity — " + fundName + " | " + props.size() + " properties | summaries so far: " + fundMetricsSummaries.size());
                    //props.forEach(fp -> System.out.println("  " + fp));

                } else {
                    //log.info("[{}/{}] Skipped (not Equity) — {}", current, total, entry.getMutualFundName());
                    System.out.println("[" + current + "/" + total + "] Skipped (not Equity): " + entry.getMutualFundName());
                }

            } catch (Exception e) {
                // Log full stack trace and wait 10 s before moving to next link
                //log.error("[{}/{}] Error processing '{}' — waiting 10 s then skipping to next link",
                        //current, total, entry.getMutualFundName(), e);
                System.out.println("[" + current + "/" + total + "] ERROR (skipped): " + entry.getMutualFundName()
                        + " — " + e.getClass().getSimpleName() + ": " + e.getMessage());
                try { Thread.sleep(10_000); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
            }
        }

       // log.info("All funds processed — type file: {} | detail file: {}", typeFilePath, detailFilePath);
       // System.out.println("Fund type Excel: " + typeFilePath);
       // System.out.println("Equity detail Excel: " + detailFilePath);

       // log.info("Total fund summaries: {} | Total properties: {}", fundMetricsSummaries.size(), fundProperties.size());
       // System.out.println("Total fund summaries: " + fundMetricsSummaries.size() + " | Total properties: " + fundProperties.size());

        try {
            saveEquityFundPropertiesToExcel();
        } catch (IOException e) {
            log.error("Failed to save equity fund properties to Excel", e);
        }
    }

    private static void saveEquityFundPropertiesToExcel() throws IOException {
        String dateSuffix = LocalDate.now().format(DateTimeFormatter.ofPattern("MMM_dd", Locale.ENGLISH));
        String filePath   = "C:\\StockMarket_Research\\ET_EQUITY_FUND_DETAILS_" + dateSuffix + ".xlsx";

        java.io.File dir = new java.io.File("C:\\StockMarket_Research");
        if (!dir.exists()) dir.mkdirs();

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("EquityFundDetails");

            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("fundName");
            header.createCell(1).setCellValue("propertyName");
            header.createCell(2).setCellValue("propertyValue");

            int rowIdx = 1;
            for (FundProperty fp : fundProperties) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(fp.getFundName());
                row.createCell(1).setCellValue(fp.getPropertyName());
                row.createCell(2).setCellValue(fp.getPropertyValue());
            }

            sheet.autoSizeColumn(0);
            sheet.autoSizeColumn(1);
            sheet.autoSizeColumn(2);

            try (FileOutputStream fos = new FileOutputStream(filePath)) {
                workbook.write(fos);
            }
        }

        log.info("Equity fund properties saved to {}", filePath);
        System.out.println("Excel saved: " + filePath);
    }

    /**
     * Appends one row to the MutualFundsType workbook. Acquires an exclusive file lock
     * so multiple JVM instances can run concurrently without corrupting each other's writes.
     */
    private static void appendTypeRow(String filePath, String name, String link, String type) {
        String lockPath = filePath + ".lck";
        try (RandomAccessFile raf = new RandomAccessFile(lockPath, "rw");
             FileChannel channel = raf.getChannel();
             FileLock ignored = channel.lock()) {

            Workbook wb;
            Sheet sheet;
            java.io.File file = new java.io.File(filePath);
            if (file.exists()) {
                try (FileInputStream fis = new FileInputStream(filePath)) {
                    wb = new XSSFWorkbook(fis);
                }
                sheet = wb.getSheetAt(0);
            } else {
                new java.io.File("C:\\StockMarket_Research").mkdirs();
                wb = new XSSFWorkbook();
                sheet = wb.createSheet("MutualFundsType");
                Row hdr = sheet.createRow(0);
                hdr.createCell(0).setCellValue("mutualFundName");
                hdr.createCell(1).setCellValue("mutualFundLink");
                hdr.createCell(2).setCellValue("fundType");
            }

            int rowIdx = sheet.getLastRowNum() + 1;
            Row row = sheet.createRow(rowIdx);
            row.createCell(0).setCellValue(nvl(name));
            row.createCell(1).setCellValue(nvl(link));
            row.createCell(2).setCellValue(nvl(type));

            try (FileOutputStream fos = new FileOutputStream(filePath)) {
                wb.write(fos);
            }
            wb.close();
            log.info("appendTypeRow: saved '{}' (row {}) to {}", name, rowIdx, filePath);

        } catch (IOException e) {
            log.error("appendTypeRow: failed for '{}' at {}", name, filePath, e);
        }
    }

    /**
     * Appends detail rows for one equity fund to all 4 sheets of the detail workbook.
     * Acquires an exclusive file lock before reading, modifying, and writing back.
     */
    private static void appendDetailRows(String filePath, CEquityFundDetail detail) {
        String lockPath = filePath + ".lck";
        try (RandomAccessFile raf = new RandomAccessFile(lockPath, "rw");
             FileChannel channel = raf.getChannel();
             FileLock ignored = channel.lock()) {

            Workbook wb;
            Sheet detailSheet, stockSheet, sectorSheet, fundDetailsSheet;
            java.io.File file = new java.io.File(filePath);
            if (file.exists()) {
                try (FileInputStream fis = new FileInputStream(filePath)) {
                    wb = new XSSFWorkbook(fis);
                }
                detailSheet      = wb.getSheet("FundOverview");
                stockSheet       = wb.getSheet("StockHoldings");
                sectorSheet      = wb.getSheet("SectorHoldings");
                fundDetailsSheet = wb.getSheet("FundDetails");
            } else {
                new java.io.File("C:\\StockMarket_Research").mkdirs();
                wb = new XSSFWorkbook();
                detailSheet      = wb.createSheet("FundOverview");
                stockSheet       = wb.createSheet("StockHoldings");
                sectorSheet      = wb.createSheet("SectorHoldings");
                fundDetailsSheet = wb.createSheet("FundDetails");
                Row hdr = detailSheet.createRow(0);
                hdr.createCell(0).setCellValue("FundName");
                hdr.createCell(1).setCellValue("FundUrl");
                hdr.createCell(2).setCellValue("FundCategory");
                hdr.createCell(3).setCellValue("ExpenseRatio");
                hdr.createCell(4).setCellValue("FundSize");
                hdr.createCell(5).setCellValue("SchemeInfo");
                hdr.createCell(6).setCellValue("Classification");
                hdr.createCell(7).setCellValue("OtherProperties");
            }

            // Sheet 1: FundOverview — one scalar row
            Row detailRow = detailSheet.createRow(detailSheet.getLastRowNum() + 1);
            detailRow.createCell(0).setCellValue(nvl(detail.getFundName()));
            detailRow.createCell(1).setCellValue(nvl(detail.getFundUrl()));
            detailRow.createCell(2).setCellValue(nvl(detail.getFundCategory()));
            detailRow.createCell(3).setCellValue(nvl(detail.getExpenseRatio()));
            detailRow.createCell(4).setCellValue(nvl(detail.getFundSize()));
            detailRow.createCell(5).setCellValue(nvl(detail.getSchemeInfo()));
            detailRow.createCell(6).setCellValue(nvl(detail.getClassification()));
            StringBuilder otherSb = new StringBuilder();
            detail.getOtherProperties().forEach((k, v) -> {
                if (!otherSb.isEmpty()) otherSb.append(" | ");
                otherSb.append(k).append(": ").append(v);
            });
            detailRow.createCell(7).setCellValue(otherSb.toString());

            // Sheet 2: StockHoldings — header from first entry if sheet is empty
            Vector<String> stocks = detail.getTopStockHoldings();
            if (!stocks.isEmpty()) {
                if (stockSheet.getLastRowNum() < 0) {
                    java.util.LinkedHashMap<String, String> first = parsePipeRow(stocks.get(0));
                    Row hRow = stockSheet.createRow(0);
                    hRow.createCell(0).setCellValue("FundName");
                    hRow.createCell(1).setCellValue("FundSize");
                    int hc = 2;
                    for (java.util.Map.Entry<String, String> e : first.entrySet())
                        hRow.createCell(hc++).setCellValue(e.getKey().isEmpty() ? nvl(e.getValue()) : e.getKey());
                }
                int stockRowIdx = stockSheet.getLastRowNum() + 1;
                for (String stockEntry : stocks) {
                    java.util.LinkedHashMap<String, String> fields = parsePipeRow(stockEntry);
                    Row sRow = stockSheet.createRow(stockRowIdx++);
                    sRow.createCell(0).setCellValue(nvl(detail.getFundName()));
                    sRow.createCell(1).setCellValue(nvl(detail.getFundSize()));
                    int c = 2;
                    for (String v : fields.values()) sRow.createCell(c++).setCellValue(nvl(v));
                }
            }

            // Sheet 3: SectorHoldings — header from first entry if sheet is empty
            Vector<String> sectors2 = detail.getSectorHoldings();
            if (!sectors2.isEmpty()) {
                if (sectorSheet.getLastRowNum() < 0) {
                    java.util.LinkedHashMap<String, String> first = parsePipeRow(sectors2.get(0));
                    Row hRow = sectorSheet.createRow(0);
                    hRow.createCell(0).setCellValue("FundName");
                    hRow.createCell(1).setCellValue("FundSize");
                    int hc = 2;
                    for (String key : first.keySet()) hRow.createCell(hc++).setCellValue(key);
                }
                int sectorRowIdx = sectorSheet.getLastRowNum() + 1;
                for (String sectorEntry : sectors2) {
                    java.util.LinkedHashMap<String, String> fields = parsePipeRow(sectorEntry);
                    Row sRow = sectorSheet.createRow(sectorRowIdx++);
                    sRow.createCell(0).setCellValue(nvl(detail.getFundName()));
                    sRow.createCell(1).setCellValue(nvl(detail.getFundSize()));
                    int c = 2;
                    for (String v : fields.values()) sRow.createCell(c++).setCellValue(nvl(v));
                }
            }

            // Sheet 4: FundDetails — header from first entry; blank key → ": Number of Holdings"
            Vector<String> fundDetailRows = detail.getFundDetails();
            if (!fundDetailRows.isEmpty()) {
                if (fundDetailsSheet.getLastRowNum() < 0) {
                    java.util.LinkedHashMap<String, String> first = parsePipeRow(fundDetailRows.get(0));
                    Row hRow = fundDetailsSheet.createRow(0);
                    hRow.createCell(0).setCellValue("FundName");
                    hRow.createCell(1).setCellValue("FundSize");
                    int hc = 2;
                    for (String key : first.keySet())
                        hRow.createCell(hc++).setCellValue(key.isEmpty() ? ": Number of Holdings" : key);
                }
                int fundDetailsRowIdx = fundDetailsSheet.getLastRowNum() + 1;
                for (String fdEntry : fundDetailRows) {
                    java.util.LinkedHashMap<String, String> fields = parsePipeRow(fdEntry);
                    Row fRow = fundDetailsSheet.createRow(fundDetailsRowIdx++);
                    fRow.createCell(0).setCellValue(nvl(detail.getFundName()));
                    fRow.createCell(1).setCellValue(nvl(detail.getFundSize()));
                    int c = 2;
                    for (String v : fields.values()) fRow.createCell(c++).setCellValue(nvl(v));
                }
            }

            try (FileOutputStream fos = new FileOutputStream(filePath)) {
                wb.write(fos);
            }
            wb.close();
            log.info("appendDetailRows: saved '{}' to {}", detail.getFundName(), filePath);

        } catch (IOException e) {
            log.error("appendDetailRows: failed for '{}' at {}", detail.getFundName(), filePath, e);
        }
    }

    /**
     * Appends raw FundProperty rows (fundName, propertyName, propertyValue) for one equity fund.
     * Creates the file with headers on first call; appends on subsequent calls.
     * Acquires an exclusive file lock so concurrent JVM instances don't corrupt each other.
     */
    private static void appendRawRows(String filePath, String fundName, List<FundProperty> props) {
        if (props == null || props.isEmpty()) return;
        String lockPath = filePath + ".lck";
        try (RandomAccessFile raf = new RandomAccessFile(lockPath, "rw");
             FileChannel channel = raf.getChannel();
             FileLock ignored = channel.lock()) {

            Workbook wb;
            Sheet sheet;
            java.io.File file = new java.io.File(filePath);
            if (file.exists()) {
                try (FileInputStream fis = new FileInputStream(filePath)) {
                    wb = new XSSFWorkbook(fis);
                }
                sheet = wb.getSheetAt(0);
            } else {
                new java.io.File("C:\\StockMarket_Research").mkdirs();
                wb = new XSSFWorkbook();
                sheet = wb.createSheet("EquityFundRaw");
                Row hdr = sheet.createRow(0);
                hdr.createCell(0).setCellValue("fundName");
                hdr.createCell(1).setCellValue("propertyName");
                hdr.createCell(2).setCellValue("propertyValue");
            }

            int rowIdx = sheet.getLastRowNum() + 1;
            for (FundProperty fp : props) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(nvl(fp.getFundName()));
                row.createCell(1).setCellValue(nvl(fp.getPropertyName()));
                row.createCell(2).setCellValue(nvl(fp.getPropertyValue()));
            }

            try (FileOutputStream fos = new FileOutputStream(filePath)) {
                wb.write(fos);
            }
            wb.close();
            log.info("appendRawRows: saved {} props for '{}' to {}", props.size(), fundName, filePath);

        } catch (IOException e) {
            log.error("appendRawRows: failed for '{}' at {}", fundName, filePath, e);
        }
    }

    /**
     * Reads ET_MUTUAL_FUNDS_TYPE_<date>.xlsx and populates {@link #processedFunds}
     * so the main loop can skip URLs that were handled in a previous run.
     */
    private static void loadProcessedFunds(String filePath) {
        processedFunds.clear();
        java.io.File file = new java.io.File(filePath);
        if (!file.exists()) {
            log.info("No existing type file at {} — starting fresh", filePath);
            return;
        }
        DataFormatter fmt = new DataFormatter();
        try (FileInputStream fis = new FileInputStream(filePath);
             Workbook wb = new XSSFWorkbook(fis)) {
            Sheet sheet = wb.getSheetAt(0);
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                String name = fmt.formatCellValue(row.getCell(0)).trim();
                String link = fmt.formatCellValue(row.getCell(1)).trim();
                String type = fmt.formatCellValue(row.getCell(2)).trim();
                if (!name.isBlank()) {
                    MutualFundEntry e = new MutualFundEntry();
                    e.setMutualFundName(name);
                    e.setMutualFundLink(link);
                    e.setFundType(type);
                    processedFunds.put(name, e);
                }
            }
            log.info("Loaded {} already-processed fund names from {}", processedFunds.size(), filePath);
            System.out.println("Already processed: " + processedFunds.size() + " funds loaded — these will be skipped");
        } catch (IOException e) {
            log.error("Failed to load processed funds from {} — will process all entries", filePath, e);
        }
    }

    private static String nvl(String s) { return s != null ? s : ""; }

    /** Splits "Key: Value | Key: Value" into an ordered map of key → value. */
    private static java.util.LinkedHashMap<String, String> parsePipeRow(String row) {
        java.util.LinkedHashMap<String, String> map = new java.util.LinkedHashMap<>();
        if (row == null || row.isBlank()) return map;
        for (String part : row.split("\\|")) {
            String trimmed = part.trim();
            int ci = trimmed.indexOf(':');
            if (ci > 0) {
                map.put(trimmed.substring(0, ci).trim(), trimmed.substring(ci + 1).trim());
            } else if (ci == 0) {
                // blank key before colon — e.g. ": 42"; store with empty-string key
                map.put("", trimmed.substring(1).trim());
            } else if (!trimmed.isBlank()) {
                map.put(trimmed, "");
            }
        }
        return map;
    }

    private static List<FundProperty> groupMetrics(String fundName, java.util.Map<String, String> metrics) {
        List<FundProperty> result = new ArrayList<>();
        StringBuilder schemeRow = new StringBuilder();
        StringBuilder classRow  = new StringBuilder();
        metrics.forEach((k, v) -> {
            String kl = k.toLowerCase();
            if (kl.contains("scheme") || kl.contains("nav")) {
                if (!schemeRow.isEmpty()) schemeRow.append(" | ");
                schemeRow.append(k).append(": ").append(v);
            } else if (kl.contains("company") || kl.contains("sector") || kl.contains("asset")) {
                if (!classRow.isEmpty()) classRow.append(" | ");
                classRow.append(k).append(": ").append(v);
            } else if (!v.isBlank()) {
                result.add(new FundProperty(fundName, k, v));
            }
        });
        if (!schemeRow.isEmpty()) result.add(new FundProperty(fundName, "Scheme Info", schemeRow.toString()));
        if (!classRow.isEmpty())  result.add(new FundProperty(fundName, "Classification", classRow.toString()));
        return result;
    }

    private static void extractTabSection(Locator container, String tabLabel,
                                          String fundName, Vector<FundProperty> props) {
        Locator trigger = container.locator("li[data-url]")
                .filter(new Locator.FilterOptions().setHasText(tabLabel))
                .first();
        if (!trigger.isVisible()) {
            log.info("Tab not visible, skipping: {}", tabLabel);
            return;
        }

        // If the tab is already active (default state), skip the click
        String triggerClasses = trigger.getAttribute("class");
        boolean alreadyActive = triggerClasses != null && triggerClasses.contains("active");

        if (alreadyActive) {
            log.info("Tab '{}' is already active — extracting directly", tabLabel);
        } else {
            trigger.click();
            // Wait until the li carries "active" class — confirms tab switch is complete
            container.locator("li[data-url].active")
                    .filter(new Locator.FilterOptions().setHasText(tabLabel))
                    .first()
                    .waitFor(new Locator.WaitForOptions().setTimeout(30_000));
            log.info("Tab '{}' clicked and now active", tabLabel);
        }

        Locator visibleTable = container.locator("table:visible").first();
        visibleTable.waitFor(new Locator.WaitForOptions().setTimeout(30_000));

        List<String> headers = new ArrayList<>();
        for (Locator th : visibleTable.locator("thead tr th").all()) {
            headers.add(th.textContent().trim());
        }

        for (Locator row : visibleTable.locator("tbody tr").all()) {
            List<Locator> tds = row.locator("td").all();
            if (tds.isEmpty()) continue;
            StringBuilder rowVal = new StringBuilder();
            for (int col = 0; col < tds.size(); col++) {
                String colHeader = col < headers.size() ? headers.get(col) : "col_" + col;
                String cellValue = tds.get(col).textContent().trim();
                if (!cellValue.isBlank()) {
                    if (!rowVal.isEmpty()) rowVal.append(" | ");
                    rowVal.append(colHeader).append(": ").append(cellValue);
                }
            }
            if (!rowVal.isEmpty()) {
                props.add(new FundProperty(fundName, tabLabel, rowVal.toString()));
            }
        }
        log.info("Extracted tab '{}' — {} rows added", tabLabel, props.size());
    }

    public static void enterMobileNumber() {
        // Wait for the popup/login form and locate the emailAndMobile input field
        Locator mobileInput = page.locator("#emailAndMobile").first();
        mobileInput.waitFor(new Locator.WaitForOptions().setTimeout(120_000));
        log.info("Mobile/email input found, entering mobile number: {}", MOBILE_NUMBER);
        mobileInput.fill(MOBILE_NUMBER);
        log.info("Mobile number entered successfully");

        Locator signInButton = page.locator("#signInButton").first();
        signInButton.waitFor(new Locator.WaitForOptions().setTimeout(120_000));
        log.info("Sign In button found, clicking...");
        signInButton.click();
        log.info("Sign In button clicked");

        Locator passwordInput = page.locator("#current-password").first();
        passwordInput.waitFor(new Locator.WaitForOptions().setTimeout(120_000));
        log.info("Password field found, entering password");
        passwordInput.fill(PASSWORD);
        log.info("Password entered successfully");

        Locator submitButton = page.locator("#otpPwdSignInBtn").first();
        submitButton.waitFor(new Locator.WaitForOptions().setTimeout(120_000));
        log.info("Submit button found, clicking to login...");
        submitButton.click();
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        log.info("Login submitted — current URL: {}", page.url());
    }

    private static final String TABLE_CLASS = "table.MarketTable_marketsCustomTable__4OuKq";

    public static void captureSectorDetails() {
        log.info("Capturing sector details from {}", TABLE_CLASS);
        sectors.clear();

        // Scope all selectors strictly to the target table
        Locator table = page.locator(TABLE_CLASS).first();
        table.waitFor(new Locator.WaitForOptions().setTimeout(120_000));

        // --- Headers: single pass from thead > tr > th only (no tbody contamination) ---
        List<Locator> headerCells = table.locator("thead tr th").all();
        List<String> headers = new ArrayList<>();
        for (Locator th : headerCells) {
            headers.add(th.textContent().trim());
        }
        log.info("Headers ({}): {}", headers.size(), headers);
        System.out.println("=== HEADERS ===");
        for (int h = 0; h < headers.size(); h++) {
            System.out.println("  [" + h + "] " + headers.get(h));
        }

        // --- Rows: strictly from tbody > tr (no header row leakage) ---
        List<Locator> rows = table.locator("tbody tr").all();
        System.out.println("\n=== ROWS (" + rows.size() + ") ===");

        for (Locator row : rows) {
            List<Locator> cells = row.locator("td").all();
            // skip empty / spacer rows
            if (cells.size() < 2) continue;

            EtSector sector = new EtSector();

            for (int col = 0; col < cells.size(); col++) {
                Locator cell    = cells.get(col);
                String  value   = cell.textContent().trim();
                String  header  = col < headers.size() ? headers.get(col).toLowerCase() : "";

                // Capture the sector link from the first <a> in the cell
                if (sector.getSectorLink() == null) {
                    List<Locator> anchors = cell.locator("a").all();
                    if (!anchors.isEmpty()) {
                        String href = anchors.get(0).getAttribute("href");
                        if (href != null && !href.isBlank()) {
                            sector.setSectorLink(href);
                        }
                    }
                }

                // Index-based mapping (fast path) with header-name fallback
                switch (col) {
                    case 0 -> sector.setSectorName(value);
                    case 1 -> sector.setChange1D(value);
                    case 2 -> sector.setChange1W(value);
                    case 3 -> sector.setChange1M(value);
                    case 4 -> sector.setChange3M(value);
                    case 5 -> sector.setChange6M(value);
                    case 6 -> sector.setChange1Y(value);
                    case 7 -> sector.setMarketCapCr(value);
                    case 8 -> sector.setNumberOfStocks(value);
                    default -> {
                        // fallback: map by header keyword for any extra columns
                        if (header.contains("cap"))   sector.setMarketCapCr(value);
                        else if (header.contains("stock") || header.contains("no.")) sector.setNumberOfStocks(value);
                    }
                }
            }

            sectors.add(sector);
            log.info("Sector: {}", sector);
            System.out.println(sector);
        }

        log.info("Captured {} sectors total", sectors.size());
        System.out.println("\nTotal sectors captured: " + sectors.size());
    }

    private static void setup() {
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        int screenWidth  = (int) screen.getWidth();
        int screenHeight = (int) screen.getHeight();
        log.info("System screen resolution: {}x{}", screenWidth, screenHeight);

        playwright = Playwright.create();
        browser = playwright.chromium()
                .launch(new BrowserType.LaunchOptions()
                        .setHeadless(false)
                        .setArgs(Arrays.asList(
                                "--disable-gpu",
                                "--no-sandbox",
                                "--disable-dev-shm-usage",
                                "--disable-background-timer-throttling",
                                "--disable-renderer-backgrounding",
                                "--disable-backgrounding-occluded-windows",
                                "--disable-http2",
                                "--window-size=" + screenWidth + "," + screenHeight)));
        page = browser.newPage();
        page.setViewportSize(screenWidth, screenHeight);
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
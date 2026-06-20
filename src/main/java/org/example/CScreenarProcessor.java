package org.example;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Vector;

public class CScreenarProcessor {

    private static final Logger log = LoggerFactory.getLogger(CScreenarProcessor.class);

    private static final String LOGIN_URL = "https://www.screener.in/login/?";
    private static final String EMAIL = "janardhana.kadiri@gmail.com";
    private static final String PASSWORD = "Jani295238*";

    private static final String EXPLORE_URL = "https://www.screener.in/explore/";
    private static final String MARKET_URL  = "https://www.screener.in/market/";

    // key = section heading on the market page (e.g. "Large Cap", "Mid Cap")
    // value = all rows from that section's table as DetailedSector POJOs
    private static final Map<String, Vector<DetailedSector>> detailedSectors = new LinkedHashMap<>();

    private static final Map<String, String> sidebarLinks = new LinkedHashMap<>();

    // key = sector name, value = all data rows scraped from that sector's table
    // each row is a Map of { column-header -> cell-value } built dynamically from the first <tr>
    private static final Map<String, Vector<Map<String, String>>> sectorStockData = new LinkedHashMap<>();

    private static Playwright playwright;
    private static Browser browser;
    private static Page page;

    public static void main(String[] args) {
        try {
            setup();
            login();
            processMarketSectors();
            //processSideBar();
        } catch (Exception e) {
            log.error("CScreenarProcessor failed", e);
        } finally {
            tearDown();
        }
    }

    public static void processMarketSectors()
    {
        try {
            loadMarketSectors();
            writeDetailedSectorsToExcel();
            loadAndWriteSectorStocks();
        } catch (Exception e) {
            log.error("CScreenarProcessor failed", e);
        }
    }

    public static void processSideBar()
    {
        try {

            loadSidebarLinks();
            processSectorLinks();
        } catch (Exception e) {
            log.error("CScreenarProcessor failed", e);
        }
    }
    public static void login() throws InterruptedException {
        log.info("Navigating to login URL: {}", LOGIN_URL);
        page.navigate(LOGIN_URL,
                new Page.NavigateOptions()
                        .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
                        .setTimeout(60_000));
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        log.info("Login page loaded — current URL: {}", page.url());

        log.info("Waiting 30 seconds before entering credentials...");
        //Thread.sleep(30_000);
        log.info("Wait complete — entering credentials now");

        Locator usernameInput = page.locator("input[name='username']").first();
        usernameInput.waitFor(new Locator.WaitForOptions().setTimeout(15_000));
        log.info("Username field found, entering email: {}", EMAIL);
        usernameInput.fill(EMAIL);

        Locator passwordInput = page.locator("input[name='password']").first();
        passwordInput.waitFor(new Locator.WaitForOptions().setTimeout(10_000));
        log.info("Password field found, entering password");
        passwordInput.fill(PASSWORD);

        Locator submitButton = page.locator("button[type='submit']").first();
        submitButton.waitFor(new Locator.WaitForOptions().setTimeout(10_000));
        log.info("Submit button found, clicking to login...");
        submitButton.click();

        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        log.info("Login submitted — current URL: {}", page.url());
    }

    // Loads https://www.screener.in/market/, finds every <table> on the page,
    // reads each table's <tbody>: first TR = column headers (→ DetailedSector.put keys),
    // subsequent TRs = one DetailedSector per row (with link captured next to the name).
    // Each table is stored in detailedSectors under the section heading that precedes it
    // in the DOM (resolved via JS); falls back to "Sector Group N" if no heading is found.
    public static void loadMarketSectors() {
        log.info("Navigating to market URL: {}", MARKET_URL);
        page.navigate(MARKET_URL,
                new Page.NavigateOptions()
                        .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
                        .setTimeout(60_000));
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        log.info("Market page loaded — current URL: {}", page.url());

        List<Locator> tables = page.locator("table").all();
        log.info("Found {} table(s) on market page", tables.size());
        detailedSectors.clear();

        for (int t = 0; t < tables.size(); t++) {
            Locator table = tables.get(t);

            // Resolve the nearest preceding heading via JS to use as the group key
            String groupName;
            try {
                Object result = page.evaluate(
                        "(idx) => {" +
                        "  const tbl = document.querySelectorAll('table')[idx];" +
                        "  let el = tbl ? tbl.previousElementSibling : null;" +
                        "  while (el) {" +
                        "    if (/^H[1-6]$/.test(el.tagName)) return el.textContent.trim();" +
                        "    el = el.previousElementSibling;" +
                        "  }" +
                        "  return null;" +
                        "}", t);
                groupName = (result != null && !result.toString().isBlank())
                        ? result.toString().trim()
                        : "Sector Group " + (t + 1);
            } catch (Exception e) {
                groupName = "Sector Group " + (t + 1);
            }

            log.info("Processing table [{}/{}] as group: [{}]", t + 1, tables.size(), groupName);
            System.out.println("\n--- Market group: " + groupName + " ---");

            Locator tbody = table.locator("tbody").first();
            List<Locator> allRows = tbody.locator("tr").all();
            if (allRows.isEmpty()) {
                log.warn("No rows in table for group [{}] — skipping", groupName);
                continue;
            }

            // First TR → column headers
            List<String> headers = new ArrayList<>();
            for (Locator cell : allRows.getFirst().locator("th, td").all()) {
                String h = cell.textContent().trim();
                headers.add(h.isBlank() ? "col_" + headers.size() : h);
            }
            log.info("Headers for [{}]: {}", groupName, headers);
            System.out.println("  Headers: " + headers);

            // Detect which column carries the anchor link (inspect first data row)
            int linkColIndex = -1;
            if (allRows.size() > 1) {
                List<Locator> firstDataCells = allRows.get(1).locator("td").all();
                for (int col = 0; col < Math.min(headers.size(), firstDataCells.size()); col++) {
                    if (firstDataCells.get(col).locator("a").count() > 0) {
                        String sample = firstDataCells.get(col).locator("a").first().getAttribute("href");
                        if (sample != null && !sample.isBlank()) {
                            linkColIndex = col;
                            log.info("Link column at index {} ('{}') for group [{}]",
                                    col, headers.get(col), groupName);
                            break;
                        }
                    }
                }
            }

            // Remaining TRs → one DetailedSector per row
            Vector<DetailedSector> sectorList = new Vector<>();
            for (int i = 1; i < allRows.size(); i++) {
                List<Locator> cells = allRows.get(i).locator("td").all();
                if (cells.isEmpty()) continue;

                DetailedSector ds = new DetailedSector();
                for (int col = 0; col < headers.size(); col++) {
                    String value = col < cells.size() ? cells.get(col).textContent().trim() : "";
                    ds.put(headers.get(col), value);

                    // Capture anchor href from the link column and set on the POJO
                    if (col == linkColIndex && col < cells.size()) {
                        List<Locator> anchors = cells.get(col).locator("a").all();
                        if (!anchors.isEmpty()) {
                            String href = anchors.getFirst().getAttribute("href");
                            if (href != null && !href.isBlank()) {
                                ds.setLink(href.startsWith("http")
                                        ? href : "https://www.screener.in" + href);
                            }
                        }
                    }
                }
                sectorList.add(ds);
                System.out.println("  " + ds);
            }

            detailedSectors.put(groupName, sectorList);
            log.info("Stored {} DetailedSector rows under group [{}]", sectorList.size(), groupName);
            System.out.println("  Rows stored for [" + groupName + "]: " + sectorList.size());
        }

        log.info("loadMarketSectors complete — {} group(s) captured", detailedSectors.size());
        System.out.println("\nTotal market sector groups captured: " + detailedSectors.size());
    }

    // Creates (or opens) Screener_Detailed_Sector_MMM_DD.xlsx and writes all groups
    // from detailedSectors. Column layout: "Group" | all data columns | "Link".
    // Header row is written only once when the sheet is empty; subsequent runs append.
    public static void writeDetailedSectorsToExcel() {
        if (detailedSectors.isEmpty()) {
            log.warn("detailedSectors is empty — nothing to write");
            return;
        }

        String stamp    = LocalDate.now().format(DateTimeFormatter.ofPattern("MMM_dd", Locale.ENGLISH));
        String fileName = "Screener_Detailed_Sector_" + stamp + ".xlsx";
        File   dir      = new File("C:\\StockMarket_Research");
        if (!dir.exists()) dir.mkdirs();
        File file = new File(dir, fileName);

        // Build column list: "Group" + all dynamic data keys + "Link"
        List<String> columnHeaders = new ArrayList<>();
        columnHeaders.add("Group");
        outer:
        for (Vector<DetailedSector> sectors : detailedSectors.values()) {
            for (DetailedSector ds : sectors) {
                columnHeaders.addAll(ds.getData().keySet());
                break outer;
            }
        }
        columnHeaders.add("Link");

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
                sheet = workbook.createSheet("Detailed Sectors");
            }

            // Write header row only when the sheet is empty
            if (isRowEmpty(sheet.getRow(0))) {
                Row headerRow = sheet.createRow(0);
                for (int i = 0; i < columnHeaders.size(); i++) {
                    headerRow.createCell(i).setCellValue(columnHeaders.get(i));
                }
                log.info("Header row written: {}", columnHeaders);
            }

            int nextRowNum = sheet.getLastRowNum() + 1;
            if (nextRowNum == 0) nextRowNum = 1;

            // Data columns = everything between "Group" (col 0) and "Link" (last col)
            List<String> dataKeys = columnHeaders.subList(1, columnHeaders.size() - 1);
            int totalWritten = 0;

            for (Map.Entry<String, Vector<DetailedSector>> entry : detailedSectors.entrySet()) {
                String groupName = entry.getKey();
                for (DetailedSector ds : entry.getValue()) {
                    Row row = sheet.createRow(nextRowNum++);
                    row.createCell(0).setCellValue(groupName);
                    for (int col = 0; col < dataKeys.size(); col++) {
                        row.createCell(col + 1).setCellValue(
                                ds.getData().getOrDefault(dataKeys.get(col), ""));
                    }
                    // "Link" always in the last column
                    row.createCell(columnHeaders.size() - 1).setCellValue(
                            ds.getLink() != null ? ds.getLink() : "");
                    totalWritten++;
                }
            }

            try (FileOutputStream fos = new FileOutputStream(file)) {
                workbook.write(fos);
            }

            log.info("Detailed sectors Excel written: {} ({} rows, {} groups)",
                    file.getAbsolutePath(), totalWritten, detailedSectors.size());
            System.out.println("Excel written: " + file.getAbsolutePath()
                    + "  [rows=" + totalWritten + ", groups=" + detailedSectors.size() + "]");

        } catch (IOException e) {
            log.error("Failed to write detailed sectors Excel file {}", fileName, e);
        } finally {
            if (workbook != null) {
                try { workbook.close(); } catch (IOException e) { log.warn("Failed to close workbook", e); }
            }
        }
    }

    public static void loadSidebarLinks() {
        log.info("Navigating to explore URL: {}", EXPLORE_URL);
        page.navigate(EXPLORE_URL,
                new Page.NavigateOptions()
                        .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
                        .setTimeout(60_000));
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        log.info("Explore page loaded — current URL: {}", page.url());

        Locator sidebar = page.locator("aside#sidebar");
        sidebar.waitFor(new Locator.WaitForOptions().setTimeout(15_000));

        List<Locator> anchors = sidebar.locator("a").all();
        log.info("Found {} links in sidebar", anchors.size());

        sidebarLinks.clear();
        for (Locator anchor : anchors) {
            String text = anchor.textContent().trim();
            String href = anchor.getAttribute("href");
            if (text.isBlank() || href == null || href.isBlank()) {
                continue;
            }
            sidebarLinks.put(text, href);
            log.info("Sidebar link: [{}] -> {}", text, href);
            System.out.println("  " + text + " -> " + href);
        }

        log.info("Total sidebar links captured: {}", sidebarLinks.size());
        System.out.println("Total sidebar links: " + sidebarLinks.size());
    }

    // Iterates every entry in sidebarLinks and delegates scraping to loadSectorTable.
    // Resolves relative hrefs (e.g. /screen/raw/?...) to full screener.in URLs.
    public static void processSectorLinks() {
        if (sidebarLinks.isEmpty()) {
            log.warn("sidebarLinks is empty — run loadSidebarLinks() first");
            return;
        }

        int total     = sidebarLinks.size();
        int completed = 0;
        int failed    = 0;

        System.out.println("\n╔══════════════════════════════════════════════╗");
        System.out.println("  SECTOR PROCESSING STARTED — Total sectors: " + total);
        System.out.println("╚══════════════════════════════════════════════╝");
        log.info("Sector processing started — {} sectors to scrape", total);

        int index = 0;
        for (Map.Entry<String, String> entry : sidebarLinks.entrySet()) {
            index++;
            String sectorName = entry.getKey();
            String href       = entry.getValue();
            String fullUrl    = href.startsWith("http") ? href : "https://www.screener.in" + href;
            int    remaining  = total - index;

            System.out.println("\n┌─────────────────────────────────────────────");
            System.out.printf( "│ SECTOR  : %s%n", sectorName);
            System.out.printf( "│ PROGRESS: %d of %d  |  Remaining: %d%n", index, total, remaining);
            System.out.println("└─────────────────────────────────────────────");
            log.info("[{}/{}] Starting sector: [{}] | remaining: {}", index, total, sectorName, remaining);

            try {
                loadSectorTable(sectorName, fullUrl);
                int rowCount = sectorStockData.getOrDefault(sectorName, new Vector<>()).size();
                appendSectorToExcel(sectorName, sectorStockData.get(sectorName));
                completed++;
                System.out.printf("  DONE [%d/%d] %-30s  rows=%d  remaining=%d%n",
                        index, total, sectorName, rowCount, total - index);
                log.info("[{}/{}] Completed sector: [{}]  rows={}  remaining={}",
                        index, total, sectorName, rowCount, total - index);
            } catch (Exception e) {
                failed++;
                System.out.printf("  FAILED [%d/%d] %-30s  error=%s%n",
                        index, total, sectorName, e.getMessage());
                log.error("[{}/{}] Failed sector: [{}]: {}", index, total, sectorName, e.getMessage());
            }
        }

        System.out.println("\n╔══════════════════════════════════════════════╗");
        System.out.printf( "  SECTOR PROCESSING COMPLETE%n");
        System.out.printf( "  Total   : %d%n", total);
        System.out.printf( "  Success : %d%n", completed);
        System.out.printf( "  Failed  : %d%n", failed);
        System.out.println("╚══════════════════════════════════════════════╝");
        log.info("Sector processing complete — total={} success={} failed={}", total, completed, failed);
    }

    // Navigates to a single sector URL and follows "Next" pagination until no Next link exists.
    // First TR of tbody on page-1 provides column headers; every page re-uses those same headers
    // and skips its own first TR (which repeats the header). All data rows across all pages are
    // collected into one Vector and stored in sectorStockData under sectorName.
    public static void loadSectorTable(String sectorName, String url) {
        Vector<Map<String, String>> rows = new Vector<>();
        List<String> headers = new ArrayList<>();
        String nextUrl = url;
        int pageNum = 0;
        int linkColumnIndex = -1;  // column index whose cells contain the stock anchor link

        while (nextUrl != null) {
            pageNum++;
            log.info("Sector [{}] — loading page {}: {}", sectorName, pageNum, nextUrl);
            System.out.println("  Page " + pageNum + ": " + nextUrl);

            page.navigate(nextUrl,
                    new Page.NavigateOptions()
                            .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
                            .setTimeout(60_000));
            page.waitForLoadState(LoadState.DOMCONTENTLOADED);

            Locator tbody = page.locator("table tbody").first();
            tbody.waitFor(new Locator.WaitForOptions().setTimeout(15_000));

            List<Locator> allRows = tbody.locator("tr").all();
            if (allRows.isEmpty()) {
                log.warn("No <tr> rows found in tbody for sector [{}] page {}", sectorName, pageNum);
                break;
            }

            // Extract headers once from the first TR of the first page only
            if (headers.isEmpty()) {
                for (Locator cell : allRows.getFirst().locator("th, td").all()) {
                    String h = cell.textContent().trim();
                    headers.add(h.isBlank() ? "col_" + headers.size() : h);
                }
                log.info("Headers for [{}]: {}", sectorName, headers);
                System.out.println("  Headers: " + headers);

                // Detect which column holds the stock anchor link (inspect first data row)
                if (allRows.size() > 1) {
                    List<Locator> firstDataCells = allRows.get(1).locator("td").all();
                    for (int col = 0; col < Math.min(headers.size(), firstDataCells.size()); col++) {
                        if (firstDataCells.get(col).locator("a").count() > 0) {
                            String sample = firstDataCells.get(col).locator("a").first().getAttribute("href");
                            if (sample != null && !sample.isBlank()) {
                                linkColumnIndex = col;
                                log.info("Stock link column detected at index {} ('{}') for sector [{}]",
                                        col, headers.get(col), sectorName);
                                break;
                            }
                        }
                    }
                }
            }

            // Skip first TR on every page (it is always the header row)
            int rowsBefore = rows.size();
            for (int i = 1; i < allRows.size(); i++) {
                List<Locator> cells = allRows.get(i).locator("td").all();
                if (cells.isEmpty()) continue;

                Map<String, String> rowData = new LinkedHashMap<>();
                for (int col = 0; col < headers.size(); col++) {
                    rowData.put(headers.get(col),
                            col < cells.size() ? cells.get(col).textContent().trim() : "");
                    // Insert "Stock Link" immediately after the column that carries the anchor,
                    // so it sits next to the name in both the Map keyset and the Excel sheet.
                    if (col == linkColumnIndex && col < cells.size()) {
                        List<Locator> anchors = cells.get(col).locator("a").all();
                        String stockHref = anchors.isEmpty() ? "" : anchors.getFirst().getAttribute("href");
                        if (stockHref != null && !stockHref.isBlank()) {
                            stockHref = stockHref.startsWith("http")
                                    ? stockHref : "https://www.screener.in" + stockHref;
                        }
                        rowData.put("Stock Link", stockHref == null ? "" : stockHref);
                    }
                }
                rows.add(rowData);
                System.out.println("    " + rowData);
            }
            log.info("Page {} added {} rows (running total: {})",
                    pageNum, rows.size() - rowsBefore, rows.size());

            // --- Pagination: resolve the Next href against the current page URL ---
            // href may be absolute, root-relative (/path?p=2), or param-only (?p=2);
            // URI.resolve() handles all three forms correctly.
            nextUrl = null;
            String currentPageUrl = page.url();
            for (Locator a : page.locator("a").all()) {
                String text = a.textContent().trim();
                if (!text.equalsIgnoreCase("Next") && !text.equals("›") && !text.equals("»")) continue;
                String href = a.getAttribute("href");
                if (href == null || href.isBlank() || href.equals("#")) continue;
                try {
                    nextUrl = new java.net.URI(currentPageUrl).resolve(href).toString();
                } catch (java.net.URISyntaxException e) {
                    log.warn("Could not resolve Next href [{}] against [{}]: {}", href, currentPageUrl, e.getMessage());
                    nextUrl = href.startsWith("http") ? href : "https://www.screener.in" + href;
                }
                log.info("Next page link found: {}", nextUrl);
                System.out.println("  → Next: " + nextUrl);
                break;
            }

            if (nextUrl == null) {
                log.info("No Next link on page {} — all pages scraped for sector [{}]", pageNum, sectorName);
                System.out.println("  No Next link — finished sector [" + sectorName + "]");
            }
        }

        sectorStockData.put(sectorName, rows);
        log.info("Stored {} total rows for sector [{}] across {} page(s)", rows.size(), sectorName, pageNum);
        System.out.println("  Total rows for [" + sectorName + "]: " + rows.size() + " (pages: " + pageNum + ")");
    }

    // Opens (or creates) today's Screener_Sector_MMM_DD.xlsx, writes the header row once
    // (only when the sheet is empty), then appends all rows for this one sector and saves.
    // Called immediately after each sector is scraped so partial results are never lost.
    public static void appendSectorToExcel(String sectorName, Vector<Map<String, String>> rows) {
        if (rows == null || rows.isEmpty()) {
            log.warn("No rows to write for sector [{}] — skipping Excel append", sectorName);
            return;
        }

        String stamp    = LocalDate.now().format(DateTimeFormatter.ofPattern("MMM_dd", Locale.ENGLISH));
        String fileName = "Screener_Sector_" + stamp + ".xlsx";
        File   dir      = new File("C:\\StockMarket_Research");
        if (!dir.exists()) dir.mkdirs();
        File file = new File(dir, fileName);

        // "Sector" is always column 0; remaining columns come from this sector's row keyset
        List<String> columnHeaders = new ArrayList<>();
        columnHeaders.add("Sector");
        columnHeaders.addAll(rows.getFirst().keySet());

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
                sheet = workbook.createSheet("Screener Sectors");
            }

            // Write header row only on the very first write (sheet is empty)
            if (isRowEmpty(sheet.getRow(0))) {
                Row headerRow = sheet.createRow(0);
                for (int i = 0; i < columnHeaders.size(); i++) {
                    headerRow.createCell(i).setCellValue(columnHeaders.get(i));
                }
                log.info("Header row written: {}", columnHeaders);
            }

            // Append this sector's rows after whatever is already in the sheet
            int nextRowNum = sheet.getLastRowNum() + 1;
            if (nextRowNum == 0) nextRowNum = 1;

            for (Map<String, String> rowData : rows) {
                Row row = sheet.createRow(nextRowNum++);
                row.createCell(0).setCellValue(sectorName);
                for (int col = 1; col < columnHeaders.size(); col++) {
                    row.createCell(col).setCellValue(
                            rowData.getOrDefault(columnHeaders.get(col), ""));
                }
            }

            try (FileOutputStream fos = new FileOutputStream(file)) {
                workbook.write(fos);
            }

            log.info("Appended {} rows for sector [{}] → {}", rows.size(), sectorName, file.getAbsolutePath());
            System.out.println("  Excel appended: " + rows.size() + " rows for [" + sectorName + "] → " + file.getName());

        } catch (IOException e) {
            log.error("Failed to append sector [{}] to Excel file {}", sectorName, fileName, e);
        } finally {
            if (workbook != null) {
                try { workbook.close(); } catch (IOException e) { log.warn("Failed to close workbook", e); }
            }
        }
    }

    // Iterates every DetailedSector link, scrapes all stock rows (with pagination),
    // and writes everything into ONE sheet "Sector Stocks" in a dedicated new file
    // Screener_Sector_Stocks_MMM_DD.xlsx (separate from the Detailed Sectors file).
    // Every stock row carries the full DetailedSector metrics repeated in the same row
    // so each row is self-contained for filtering/comparing without joining sheets.
    // Layout: Group | Sector | Sector <col>… | stock columns…
    // "Sector " prefix on sector columns avoids label clashes (e.g. both have "P/E").
    // Saves immediately after each sector — crash loses at most one sector's data.
    public static void loadAndWriteSectorStocks() throws IOException {
        if (detailedSectors.isEmpty()) {
            log.warn("detailedSectors is empty — nothing to process in loadAndWriteSectorStocks");
            return;
        }

        String stamp    = LocalDate.now().format(DateTimeFormatter.ofPattern("MMM_dd", Locale.ENGLISH));
        String fileName = "Screener_Sector_Stocks_" + stamp + ".xlsx";
        File   dir      = new File("C:\\StockMarket_Research");
        if (!dir.exists()) dir.mkdirs();
        File file = new File(dir, fileName);

        Workbook workbook = file.exists()
                ? new XSSFWorkbook(new FileInputStream(file))
                : new XSSFWorkbook();

        Sheet sheet = workbook.getSheet("Sector Stocks");
        if (sheet == null) sheet = workbook.createSheet("Sector Stocks");

        boolean headerDone    = !isRowEmpty(sheet.getRow(0));
        List<String> sectorDataKeys = null;  // ds.getData() keys, captured once
        List<String> stockKeys      = null;  // scraped stock row keys, captured once

        int totalWritten = 0;
        int groupIndex   = 0;

        try {
            for (Map.Entry<String, Vector<DetailedSector>> groupEntry : detailedSectors.entrySet()) {
                String groupName            = groupEntry.getKey();
                Vector<DetailedSector> dsList = groupEntry.getValue();
                groupIndex++;
                int sectorIndex = 0;

                for (DetailedSector ds : dsList) {
                    sectorIndex++;
                    String link = ds.getLink();
                    if (link == null || link.isBlank()) {
                        log.warn("No link for entry {}/{} in group [{}] — skipping",
                                sectorIndex, dsList.size(), groupName);
                        continue;
                    }

                    // Best-effort sector name: prefer "Name" key, else first non-numeric value
                    String sectorName = ds.getData().entrySet().stream()
                            .filter(e -> e.getKey().equalsIgnoreCase("name")
                                    || !e.getValue().matches("\\d+\\.?\\d*"))
                            .map(Map.Entry::getValue)
                            .filter(v -> !v.isBlank())
                            .findFirst()
                            .orElse("Sector_" + sectorIndex);

                    System.out.printf("%n┌── [Group %d] %-20s  [%d/%d] %s%n",
                            groupIndex, groupName, sectorIndex, dsList.size(), sectorName);
                    System.out.println("│   URL: " + link);

                    Vector<Map<String, String>> stockRows = scrapeStocksFromUrl(link);
                    System.out.printf("└── %d stock row(s) scraped%n", stockRows.size());
                    if (stockRows.isEmpty()) continue;

                    // Build and write header row once — sector columns first, then stock columns
                    if (!headerDone) {
                        sectorDataKeys = new ArrayList<>(ds.getData().keySet());
                        stockKeys      = new ArrayList<>(stockRows.getFirst().keySet());

                        List<String> hdr = new ArrayList<>();
                        hdr.add("Group");
                        hdr.add("Sector");
                        for (String k : sectorDataKeys) hdr.add("Sector " + k);
                        hdr.addAll(stockKeys);

                        Row hRow = sheet.createRow(0);
                        for (int i = 0; i < hdr.size(); i++) hRow.createCell(i).setCellValue(hdr.get(i));
                        headerDone = true;
                        log.info("'Sector Stocks' headers ({}): {}", hdr.size(), hdr);
                    }
                    if (sectorDataKeys == null) sectorDataKeys = new ArrayList<>(ds.getData().keySet());
                    if (stockKeys      == null) stockKeys      = new ArrayList<>(stockRows.getFirst().keySet());

                    // Append one row per stock — sector metrics repeated for every stock in the sector
                    int nextRow = sheet.getLastRowNum() + 1;
                    if (nextRow == 0) nextRow = 1;
                    for (Map<String, String> sr : stockRows) {
                        Row row = sheet.createRow(nextRow++);
                        int c = 0;
                        row.createCell(c++).setCellValue(groupName);
                        row.createCell(c++).setCellValue(sectorName);
                        for (String sk : sectorDataKeys)
                            row.createCell(c++).setCellValue(ds.getData().getOrDefault(sk, ""));
                        for (String stk : stockKeys)
                            row.createCell(c++).setCellValue(sr.getOrDefault(stk, ""));
                        totalWritten++;
                    }

                    // Save after every sector — crash loses at most one sector's data
                    try (FileOutputStream fos = new FileOutputStream(file)) {
                        workbook.write(fos);
                    }
                    System.out.printf("  Saved → %s  (rows so far: %d)%n", file.getName(), totalWritten);
                    log.info("Sector [{}]: {} rows appended, cumulative: {}",
                            sectorName, stockRows.size(), totalWritten);
                }
            }

            log.info("loadAndWriteSectorStocks complete — {} total rows in 'Sector Stocks'", totalWritten);
            System.out.println("\nTotal rows in 'Sector Stocks': " + totalWritten);

        } finally {
            try { workbook.close(); } catch (IOException e) { log.warn("Failed to close workbook", e); }
        }
    }

    // Shared pagination scraper — navigates startUrl, follows Next links, collects all
    // table rows as Map<header, value> (with "Stock Link" inserted next to the anchor column).
    public static Vector<Map<String, String>> scrapeStocksFromUrl(String startUrl) {
        Vector<Map<String, String>> rows = new Vector<>();
        List<String> headers = new ArrayList<>();
        String nextUrl = startUrl;
        int pageNum = 0;
        int linkColIndex = -1;

        while (nextUrl != null) {
            pageNum++;
            log.info("  scraping page {}: {}", pageNum, nextUrl);
            page.navigate(nextUrl, new Page.NavigateOptions()
                    .setWaitUntil(WaitUntilState.DOMCONTENTLOADED).setTimeout(60_000));
            page.waitForLoadState(LoadState.DOMCONTENTLOADED);

            Locator tbody = page.locator("table tbody").first();
            try {
                tbody.waitFor(new Locator.WaitForOptions().setTimeout(15_000));
            } catch (Exception e) {
                log.warn("No tbody on page {} for {}", pageNum, nextUrl);
                break;
            }

            List<Locator> allRows = tbody.locator("tr").all();
            if (allRows.isEmpty()) break;

            if (headers.isEmpty()) {
                for (Locator cell : allRows.getFirst().locator("th, td").all()) {
                    String h = cell.textContent().trim();
                    headers.add(h.isBlank() ? "col_" + headers.size() : h);
                }
                if (allRows.size() > 1) {
                    List<Locator> firstData = allRows.get(1).locator("td").all();
                    for (int col = 0; col < Math.min(headers.size(), firstData.size()); col++) {
                        if (firstData.get(col).locator("a").count() > 0) {
                            String sample = firstData.get(col).locator("a").first().getAttribute("href");
                            if (sample != null && !sample.isBlank()) { linkColIndex = col; break; }
                        }
                    }
                }
            }

            for (int i = 1; i < allRows.size(); i++) {
                List<Locator> cells = allRows.get(i).locator("td").all();
                if (cells.isEmpty()) continue;
                Map<String, String> row = new LinkedHashMap<>();
                for (int col = 0; col < headers.size(); col++) {
                    row.put(headers.get(col), col < cells.size() ? cells.get(col).textContent().trim() : "");
                    if (col == linkColIndex && col < cells.size()) {
                        List<Locator> anchors = cells.get(col).locator("a").all();
                        String href = anchors.isEmpty() ? "" : anchors.getFirst().getAttribute("href");
                        if (href != null && !href.isBlank())
                            href = href.startsWith("http") ? href : "https://www.screener.in" + href;
                        row.put("Stock Link", href == null ? "" : href);
                    }
                }
                rows.add(row);
            }

            nextUrl = null;
            String curUrl = page.url();
            for (Locator a : page.locator("a").all()) {
                String text = a.textContent().trim();
                if (!text.equalsIgnoreCase("Next") && !text.equals("›") && !text.equals("»")) continue;
                String href = a.getAttribute("href");
                if (href == null || href.isBlank() || href.equals("#")) continue;
                try { nextUrl = new java.net.URI(curUrl).resolve(href).toString(); }
                catch (java.net.URISyntaxException e) {
                    nextUrl = href.startsWith("http") ? href : "https://www.screener.in" + href;
                }
                break;
            }
        }
        log.info("  scrapeStocksFromUrl: {} rows, {} page(s)", rows.size(), pageNum);
        return rows;
    }

    private static boolean isRowEmpty(Row row) {
        if (row == null) return true;
        for (int c = row.getFirstCellNum(); c < row.getLastCellNum(); c++) {
            Cell cell = row.getCell(c);
            if (cell != null && !cell.toString().isBlank()) return false;
        }
        return true;
    }

    private static void setup() {
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
                                "--start-maximized")));
        // setViewportSize(null) disables Playwright's fixed viewport so the page
        // fills whatever window size the OS gives the maximized browser window.
        page = browser.newPage(new Browser.NewPageOptions().setViewportSize(null));
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
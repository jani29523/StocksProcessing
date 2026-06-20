package org.example;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitUntilState;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static org.example.CScreenarProcessor.loadAndWriteSectorStocks;

public class CScreenAllFieldsExtractor {

    private static final Logger log = LoggerFactory.getLogger(CScreenAllFieldsExtractor.class);

    private static final String LOGIN_URL   = "https://www.screener.in/login/?";
    private static final String COLUMNS_URL = "https://www.screener.in/user/columns/";
    private static final String EMAIL       = "janardhana.kadiri@gmail.com";
    private static final String PASSWORD    = "Jani295238*";
    private static final String MARKET_URL  = "https://www.screener.in/market/";
    private static Playwright playwright;
    private static Browser     browser;
    private static Page        page;

    // Global collection of all captured column labels — populated by captureAllElements().
    // Shared across all button-tab iterations so labels from every category are merged here.
    private static final Vector<ScreenerColumnLabel> capturedLabels = new Vector<>();

    // Tracks checkbox values already added; enforces uniqueness across multiple captureAllElements() calls.
    private static final Set<String> capturedValues = new HashSet<>();

    // key = section heading on the market page (e.g. "Large Cap", "Mid Cap")
    // value = all rows from that section's table as DetailedSector POJOs
    private static final Map<String, Vector<DetailedSector>> detailedSectors = new LinkedHashMap<>();

    private static final Map<String, String> sidebarLinks = new LinkedHashMap<>();

    // key = sector name, value = all data rows scraped from that sector's table
    // each row is a Map of { column-header -> cell-value } built dynamically from the first <tr>
    private static final Map<String, Vector<Map<String, String>>> sectorStockData = new LinkedHashMap<>();

    // ─────────────────────────────────────────────────────────────────────────
    // Entry point
    // ─────────────────────────────────────────────────────────────────────────

    public static void main(String[] args) {
        try {
            setup();
            login();
            extractOptionButtons();
            removeAllManagedColumns();
            selectSequentialELements();
        } catch (Exception e) {
            log.error("CScreenAllFieldsExtractor failed", e);
        } finally {
            tearDown();
        }
    }


    // ─────────────────────────────────────────────────────────────────────────
    // Sequential batch-selector
    //
    // Splits capturedLabels into groups of 15.  For each group:
    //   1. Navigates to a fresh COLUMNS_URL.
    //   2. Iterates every option-button tab in div.options.flex-wrap.
    //   3. In each tab, checks the checkbox for every label in the current batch
    //      whose value is visible there.
    //   4. Clicks the "Save columns" primary button.
    //   5. Waits 2 minutes before the next batch (skipped after the last batch).
    // ─────────────────────────────────────────────────────────────────────────

    private static void selectSequentialELements() {
        if (capturedLabels.isEmpty()) {
            log.warn("selectSequentialElements: capturedLabels is empty — nothing to select");
            System.out.println("No labels in capturedLabels — skipping selectSequentialElements.");
            return;
        }

        int total      = capturedLabels.size();
        int batchSize  = 15;
        int batchCount = (int) Math.ceil((double) total / batchSize);

        log.info("selectSequentialElements: {} label(s) total, {} batch(es) of ≤{}", total, batchCount, batchSize);
        System.out.printf("%nStarting sequential selection: %d label(s) across %d batch(es) of ≤%d%n%n",
                total, batchCount, batchSize);

        for (int batchIdx = 0; batchIdx < batchCount; batchIdx++) {
            int from = batchIdx * batchSize;
            int to   = Math.min(from + batchSize, total);
            List<ScreenerColumnLabel> batch = new ArrayList<>(capturedLabels.subList(from, to));

            // Build quick-lookup set of checkbox values for this batch
            Set<String> batchValues = new HashSet<>();
            for (ScreenerColumnLabel lbl : batch) {
                if (!lbl.getCheckboxValue().isBlank()) batchValues.add(lbl.getCheckboxValue());
            }

            System.out.println("══════════════════════════════════════════════════════════");
            System.out.printf("  BATCH %d/%d  (labels %d–%d of %d)%n",
                    batchIdx + 1, batchCount, from + 1, to, total);
            System.out.println("══════════════════════════════════════════════════════════");
            for (ScreenerColumnLabel lbl : batch) {
                System.out.printf("  → %-40s  value=%s%n", lbl.getLabelText(), lbl.getCheckboxValue());
            }
            System.out.println();

            // Fresh page load before each batch ensures a clean checkbox state
            log.info("Navigating to {} for batch {}/{}", COLUMNS_URL, batchIdx + 1, batchCount);
            page.navigate(COLUMNS_URL,
                    new Page.NavigateOptions()
                            .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
                            .setTimeout(60_000));
            page.waitForLoadState(LoadState.DOMCONTENTLOADED);

            int checked = checkLabelsAcrossAllTabs(batchValues);

            log.info("Batch {}/{}: {}/{} checkbox(es) checked — clicking Save",
                    batchIdx + 1, batchCount, checked, batch.size());
            System.out.printf("%n  Checked %d / %d — clicking Save%n", checked, batch.size());

            clickSaveButton();

            if (batchIdx < batchCount - 1) {


                log.info("Batch {}/{} saved — waiting 2 minutes before next batch",
                        batchIdx + 1, batchCount);
                System.out.printf("  Batch %d/%d saved. Waiting 2 minutes before next batch...%n%n",
                        batchIdx + 1, batchCount);
                page.waitForTimeout(2 * 60_000);

                processMarketSectors();

            } else {
                log.info("selectSequentialElements complete — all {} batch(es) saved", batchCount);
                System.out.println("\nAll batches processed and saved successfully.");
            }
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
        File dir      = new File("C:\\StockMarket_Research");
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

    private static boolean isRowEmpty(Row row) {
        if (row == null) return true;
        for (int c = row.getFirstCellNum(); c < row.getLastCellNum(); c++) {
            Cell cell = row.getCell(c);
            if (cell != null && !cell.toString().isBlank()) return false;
        }
        return true;
    }
    // ─────────────────────────────────────────────────────────────────────────
    // Login  (identical pattern to CScreenarProcessor)
    // ─────────────────────────────────────────────────────────────────────────

    public static void login() throws InterruptedException {
        log.info("Navigating to login URL: {}", LOGIN_URL);
        page.navigate(LOGIN_URL,
                new Page.NavigateOptions()
                        .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
                        .setTimeout(60_000));
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        log.info("Login page loaded — current URL: {}", page.url());

        Locator usernameInput = page.locator("input[name='username']").first();
        usernameInput.waitFor(new Locator.WaitForOptions().setTimeout(15_000));
        log.info("Username field found — entering email: {}", EMAIL);
        usernameInput.fill(EMAIL);

        Locator passwordInput = page.locator("input[name='password']").first();
        passwordInput.waitFor(new Locator.WaitForOptions().setTimeout(10_000));
        log.info("Password field found — entering password");
        passwordInput.fill(PASSWORD);

        Locator submitButton = page.locator("button[type='submit']").first();
        submitButton.waitFor(new Locator.WaitForOptions().setTimeout(10_000));
        log.info("Submit button found — clicking to login");
        submitButton.click();

        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        log.info("Login submitted — current URL: {}", page.url());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Remove all columns listed in ul#manage-menu
    //
    // The page at COLUMNS_URL shows a <ul id="manage-menu"> whose <li> elements
    // each contain a <button> that removes that column when clicked.  Because
    // each click mutates the DOM (the <li> disappears), element references go
    // stale immediately.  The safe pattern is to always re-query the list fresh
    // at the start of every iteration and pick the first remaining button.
    // ─────────────────────────────────────────────────────────────────────────

    public static void removeAllManagedColumns() {
        log.info("Navigating to columns management page: {}", COLUMNS_URL);
        page.navigate(COLUMNS_URL,
                new Page.NavigateOptions()
                        .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
                        .setTimeout(60_000));
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        log.info("Columns page loaded — current URL: {}", page.url());

        // Wait for the managed-column list to be present in the DOM
        Locator menuList = page.locator("ul#manage-menu");
        menuList.waitFor(new Locator.WaitForOptions().setTimeout(15_000));

        int initialCount = menuList.locator("li").count();
        log.info("Found {} item(s) in #manage-menu before removal", initialCount);
        System.out.println("Columns in manage-menu: " + initialCount);

        if (initialCount == 0) {
            log.info("manage-menu is already empty — nothing to remove");
            System.out.println("No columns to remove.");
            return;
        }

        int removed    = 0;
        int maxCycles  = initialCount + 20; // safety cap to prevent infinite loops

        for (int cycle = 1; cycle <= maxCycles; cycle++) {

            // Re-query every iteration — clicking changes the DOM
            List<Locator> liItems = menuList.locator("li").all();

            if (liItems.isEmpty()) {
                log.info("All items removed — manage-menu is empty after {} removal(s)", removed);
                System.out.println("manage-menu is now empty. Total removed: " + removed);
                break;
            }

            log.info("[cycle {}] {} item(s) remaining in manage-menu", cycle, liItems.size());

            // Find the first <li> that still has a visible, enabled button and click it
            boolean clicked = false;
            for (Locator li : liItems) {
                Locator btn = li.locator("button").first();
                if (btn.count() == 0) continue;

                // Capture the column label before clicking (element may disappear afterward)
                String label = li.textContent().trim()
                        .replaceAll("\\s+", " ")
                        .replaceAll("×", "")  // strip the '×' remove symbol if present
                        .trim();

                if (!btn.isVisible()) {
                    log.debug("Button not visible for '{}' — skipping", label);
                    continue;
                }

                log.info("[{}/~{}] Clicking remove button for column: '{}'",
                        removed + 1, initialCount, label);
                System.out.printf("[%d/%d] Removing: %s%n", removed + 1, initialCount, label);

                btn.click();
                removed++;
                clicked = true;

                // Wait for the network request triggered by the click to settle,
                // and for the DOM to reflect the removal, before the next iteration.
                try {
                    page.waitForLoadState(LoadState.DOMCONTENTLOADED,
                            new Page.WaitForLoadStateOptions().setTimeout(5_000));
                } catch (Exception ignored) {
                    // Some removals are pure AJAX with no full page load — fall through
                }
                page.waitForTimeout(400);
                break; // Re-enter the outer loop to get a fresh li list
            }

            if (!clicked) {
                log.info("No clickable button found in remaining {} item(s) — stopping", liItems.size());
                System.out.println("No more removable buttons found. Stopped at cycle " + cycle
                        + ". Total removed: " + removed);
                break;
            }
        }

        log.info("removeAllManagedColumns complete — {} column(s) removed from #manage-menu", removed);
        System.out.println("Done. Removed " + removed + " column(s).");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Extract all option buttons from div.options.flex-wrap
    //
    // The columns page contains a <div class="options flex-wrap"> that holds
    // category/group filter buttons (e.g. "All", "Fundamental", "Valuation"…).
    // This method:
    //   1. Navigates to COLUMNS_URL if not already there.
    //   2. Waits for the div to appear and collects every <button> inside it.
    //   3. Logs each button's label and whether it is currently active.
    //   4. Clicks the first button as the default selection.
    //   5. Returns the ordered list of all button labels found.
    // ─────────────────────────────────────────────────────────────────────────

    public static void extractOptionButtons() {
        // Navigate only when we are not already on the columns page
        if (!page.url().contains("/user/columns/")) {
            log.info("Navigating to columns management page: {}", COLUMNS_URL);
            page.navigate(COLUMNS_URL,
                    new Page.NavigateOptions()
                            .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
                            .setTimeout(60_000));
            page.waitForLoadState(LoadState.DOMCONTENTLOADED);
            log.info("Columns page loaded — current URL: {}", page.url());
        }

        // Wait for the options container to be present in the DOM
        Locator optionsDiv = page.locator("div.options.flex-wrap").first();
        optionsDiv.waitFor(new Locator.WaitForOptions().setTimeout(15_000));

        // Snapshot all buttons — this list is stable because we're only reading, not mutating
        List<Locator> buttonLocators = optionsDiv.locator("button").all();
        int count = buttonLocators.size();
        log.info("Found {} button(s) in div.options.flex-wrap", count);
        System.out.println("Option buttons in div.options.flex-wrap: " + count);

        // Collect labels and log each one with its active state

        for (int i = 0; i < buttonLocators.size(); i++) {
            Locator btn   = buttonLocators.get(i);
            btn.click();
            boolean active = isButtonActive(btn);
            if(active)
            {
                captureAllElements();
            }

        }

        // Print all unique captured labels collected across all option tabs
        log.info("Total unique labels captured: {}", capturedLabels.size());
        System.out.println("\n══════════════════════════════════════════════════════════");
        System.out.println("  ALL UNIQUE CAPTURED LABELS — Total: " + capturedLabels.size());
        System.out.println("══════════════════════════════════════════════════════════");
        for (int i = 0; i < capturedLabels.size(); i++) {
            ScreenerColumnLabel lbl = capturedLabels.get(i);
            System.out.printf("  [%3d] %-40s | data-name=%-28s | value=%-20s | checked=%b | class=%s%n",
                    i + 1, lbl.getLabelText(), lbl.getDataName(),
                    lbl.getCheckboxValue(), lbl.isChecked(), lbl.getLabelClassName());
        }
        System.out.println("══════════════════════════════════════════════════════════\n");

    }

    // ─────────────────────────────────────────────────────────────────────────
    // Capture all column labels from the active option panel
    //
    // DOM structure expected after an option button is clicked:
    //
    //   <div class="flex flex-column-mobile flex-gap-32">   ← container
    //     <div>                                              ← group div
    //       <label class="..." data-name="eps">             ← one column label
    //         <input type="checkbox" value="eps" />
    //         EPS
    //       </label>
    //       ...
    //     </div>
    //     <div> ... </div>
    //   </div>
    //
    // Each label is converted to a ScreenerColumnLabel POJO and added to the
    // global capturedLabels Vector.  capturedValues guards against duplicates:
    // if a checkbox value was already captured (e.g. from a previous tab), the
    // label is silently skipped so the Vector stays deduplicated.
    // ─────────────────────────────────────────────────────────────────────────

    private static void captureAllElements() {
        // Wait for the container to be present after the tab-button click
        Locator container = page.locator("div.flex.flex-column-mobile.flex-gap-32").first();
        try {
            container.waitFor(new Locator.WaitForOptions().setTimeout(10_000));
        } catch (Exception e) {
            log.warn("Container div.flex.flex-column-mobile.flex-gap-32 not visible — skipping capture");
            System.out.println("WARNING: column container not found — skipping this tab.");
            return;
        }

        // Each direct child <div> is one visual group of labels
        List<Locator> groupDivs = container.locator("> div").all();
        log.info("captureAllElements: {} group div(s) found in container", groupDivs.size());
        System.out.println("  Group divs in container: " + groupDivs.size());

        int added   = 0;
        int skipped = 0;

        for (int g = 0; g < groupDivs.size(); g++) {
            Locator groupDiv = groupDivs.get(g);
            List<Locator> labels = groupDiv.locator("label").all();
            log.info("  Group [{}]: {} label(s)", g + 1, labels.size());

            for (Locator labelLocator : labels) {
                // ── Read label attributes ─────────────────────────────────
                String labelClass = nvl(labelLocator.getAttribute("class"));
                String dataName   = nvl(labelLocator.getAttribute("data-name"));
                // Strip the checkbox's own text node to get just the visible label text
                String labelText  = labelLocator.textContent().trim().replaceAll("\\s+", " ");

                // ── Read the nested checkbox ──────────────────────────────
                Locator checkbox = labelLocator.locator("input[type='checkbox']").first();
                if (checkbox.count() == 0) {
                    log.debug("  No checkbox inside label '{}' — skipping", labelText);
                    continue;
                }
                String  checkboxValue = nvl(checkbox.getAttribute("value"));
                boolean isChecked     = checkbox.isChecked();

                // ── Uniqueness guard — key is the checkbox value ──────────
                if (!checkboxValue.isBlank() && capturedValues.contains(checkboxValue)) {
                    log.debug("  Duplicate value '{}' ('{}') — skipping", checkboxValue, labelText);
                    skipped++;
                    continue;
                }
                if (!checkboxValue.isBlank()) capturedValues.add(checkboxValue);

                // ── Build POJO and add to global Vector ───────────────────
                ScreenerColumnLabel label = new ScreenerColumnLabel(
                        labelClass, dataName, labelText, checkboxValue, isChecked);
                capturedLabels.add(label);
                added++;

                log.info("  [+{}] '{}' | data-name='{}' | value='{}' | checked={}",
                        capturedLabels.size(), labelText, dataName, checkboxValue, isChecked);
                System.out.printf("  [+%3d] %-40s | data-name=%-28s | value=%-20s | checked=%b%n",
                        capturedLabels.size(), labelText, dataName, checkboxValue, isChecked);
            }
        }

        log.info("captureAllElements done — +{} added, {} duplicate(s) skipped, {} total in Vector",
                added, skipped, capturedLabels.size());
        System.out.printf("  → +%d captured, %d duplicate(s) skipped. Vector total: %d%n",
                added, skipped, capturedLabels.size());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Tab scanner — iterates every option button, checks matching checkboxes
    //
    // Buttons are re-queried by index each iteration (nth(t)) so a prior click
    // never leaves a stale reference.  Once all targetValues are found the loop
    // short-circuits to avoid unnecessary tab switches.
    // ─────────────────────────────────────────────────────────────────────────

    private static int checkLabelsAcrossAllTabs(Set<String> targetValues) {
        Set<String> remaining    = new HashSet<>(targetValues);
        int         checkedCount = 0;

        Locator optionsDiv = page.locator("div.options.flex-wrap").first();
        try {
            optionsDiv.waitFor(new Locator.WaitForOptions().setTimeout(15_000));
        } catch (Exception e) {
            log.error("checkLabelsAcrossAllTabs: div.options.flex-wrap not found", e);
            System.out.println("  ERROR: options div not found — cannot check labels.");
            return 0;
        }

        int tabCount = page.locator("div.options.flex-wrap button").count();
        log.info("checkLabelsAcrossAllTabs: {} tab(s) to scan, {} target value(s)", tabCount, remaining.size());

        for (int t = 0; t < tabCount && !remaining.isEmpty(); t++) {
            // Always resolve by index — avoids stale element after previous click
            Locator tabBtn = page.locator("div.options.flex-wrap button").nth(t);

            String tabLabel = "tab#" + (t + 1);
            try { tabLabel = tabBtn.textContent().trim(); } catch (Exception ignored) {}

            log.info("  [Tab {}/{}] '{}'", t + 1, tabCount, tabLabel);
            System.out.printf("    [Tab %d/%d] %s%n", t + 1, tabCount, tabLabel);

            try {
                tabBtn.click();
            } catch (Exception e) {
                log.warn("  Could not click tab '{}' — skipping: {}", tabLabel, e.getMessage());
                continue;
            }
            page.waitForTimeout(800); // let tab content render

            Set<String> foundInTab = new HashSet<>();
            for (String val : remaining) {
                // Escape backslash then single-quote for CSS attribute selector safety
                String safeVal = val.replace("\\", "\\\\").replace("'", "\\'");
                Locator cb = page.locator("input[type='checkbox'][value='" + safeVal + "']").first();
                try {
                    if (cb.count() > 0 && cb.isVisible()) {
                        if (!cb.isChecked()) {
                            cb.check();
                            log.info("      ✓ Checked  value='{}'", val);
                            System.out.printf("      ✓ Checked:         %s%n", val);
                        } else {
                            log.info("      ✓ Already  value='{}'", val);
                            System.out.printf("      ✓ Already checked: %s%n", val);
                        }
                        foundInTab.add(val);
                        checkedCount++;
                    }
                } catch (Exception e) {
                    log.debug("      Error accessing checkbox value='{}': {}", val, e.getMessage());
                }
            }
            remaining.removeAll(foundInTab);
        }

        if (!remaining.isEmpty()) {
            log.warn("  {} value(s) not found across all tabs: {}", remaining.size(), remaining);
            System.out.printf("  WARNING: %d value(s) not found in any tab: %s%n",
                    remaining.size(), remaining);
        }

        return checkedCount;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Click the primary Save button (class="button-primary", text "Save columns")
    // ─────────────────────────────────────────────────────────────────────────

    private static void clickSaveButton() {
        Locator saveBtn = page.locator("button.button-primary")
                .filter(new Locator.FilterOptions().setHasText("Save columns"))
                .first();
        try {
            saveBtn.waitFor(new Locator.WaitForOptions().setTimeout(10_000));
            saveBtn.click();
            log.info("Save button clicked successfully");
            System.out.println("  Save button clicked.");
            try {
                page.waitForLoadState(LoadState.DOMCONTENTLOADED,
                        new Page.WaitForLoadStateOptions().setTimeout(10_000));
            } catch (Exception ignored) {}
            page.waitForTimeout(1_000);
        } catch (Exception e) {
            log.error("Failed to click Save button: {}", e.getMessage());
            System.out.println("  ERROR: Could not click Save button — " + e.getMessage());
        }
    }

    private static String nvl(String s) { return s != null ? s : ""; }

    // Returns true when the button's class attribute signals it is currently selected/active.
    private static boolean isButtonActive(Locator btn) {
        try {
            String cls = btn.getAttribute("class");
            return cls != null
                    && (cls.contains("active") || cls.contains("selected") || cls.contains("current"));
        } catch (Exception e) {
            return false;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Browser lifecycle  (identical setup to CScreenarProcessor)
    // ─────────────────────────────────────────────────────────────────────────

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
        page = browser.newPage(new Browser.NewPageOptions().setViewportSize(null));
    }

    private static void tearDown() {
        if (page != null)       page.close();
        if (browser != null)    browser.close();
        if (playwright != null) playwright.close();
    }
}
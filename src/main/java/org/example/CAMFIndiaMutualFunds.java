package org.example;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.LoadState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class CAMFIndiaMutualFunds {

    private static final Logger log = LoggerFactory.getLogger(CAMFIndiaMutualFunds.class);

    // Configurable: start date in DD-MMM-YYYY format (e.g. 09-Jun-2026)
    private static final String START_DATE = "02-Jun-2026";

    // Configurable: how many working days (Mon–Fri) to fetch going backwards from START_DATE (inclusive)
    private static final int WORKING_DAYS_COUNT = 80;

    private static final String BASE_URL =
            "https://portal.amfiindia.com/DownloadNAVHistoryReport_Po.aspx?frmdt=";
    private static final String OUTPUT_DIR = "C:\\StockMarket_Research\\AMFI_NAV";
    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH);

    private static Playwright playwright;
    private static Browser browser;
    private static Page page;

    public static void main(String[] args) {
        try {
            setup();
            fetchNAVForWorkingDays();
        } finally {
            tearDown();
        }
    }

    public static void fetchNAVForWorkingDays() {
        LocalDate startDate = LocalDate.parse(START_DATE, DATE_FMT);

        List<LocalDate> workingDays = new ArrayList<>();
        LocalDate cursor = startDate;
        while (workingDays.size() < WORKING_DAYS_COUNT) {
            DayOfWeek dow = cursor.getDayOfWeek();
            if (dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY) {
                workingDays.add(cursor);
            }
            cursor = cursor.minusDays(1);
        }

        log.info("Fetching NAV for {} working days starting from {}", workingDays.size(), START_DATE);
        System.out.println(">>> Fetching NAV for " + workingDays.size()
                + " working day(s) starting from " + START_DATE);

        for (LocalDate date : workingDays) {
            try {
                fetchAndSaveNAV(date);
            } catch (Exception e) {
                log.error("Failed to fetch NAV for {}: {}", DATE_FMT.format(date), e.getMessage());
                System.out.println(">>> ERROR for " + DATE_FMT.format(date) + ": " + e.getMessage());
            }
        }

        System.out.println(">>> Done.");
    }

    public static void fetchAndSaveNAV(LocalDate date) throws IOException {
        String dateStr = DATE_FMT.format(date);
        String urlStr = BASE_URL + dateStr;

        log.info("Loading in browser: {}", urlStr);
        System.out.println(">>> Loading: " + urlStr);

        page.navigate(urlStr, new Page.NavigateOptions().setTimeout(60_000));
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        page.waitForLoadState(LoadState.LOAD);
        page.waitForLoadState(LoadState.NETWORKIDLE);

        // AMFI serves plain text; the browser wraps it in <pre> inside <body>
        String content = page.locator("body").textContent();
        if (content == null) {
            content = "";
        }
        content = content.trim();

        log.info("Loaded {} chars for date {}", content.length(), dateStr);
        System.out.println(">>> Loaded " + content.length() + " chars for " + dateStr);

        File dir = new File(OUTPUT_DIR);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("Could not create output directory: " + OUTPUT_DIR);
        }

        File outFile = new File(dir, dateStr + ".txt");
        try (FileWriter fw = new FileWriter(outFile)) {
            fw.write(content);
        }

        log.info("Saved to {}", outFile.getAbsolutePath());
        System.out.println(">>> Saved: " + outFile.getAbsolutePath());
    }

    private static void setup() {
        playwright = Playwright.create();
        browser = playwright.chromium().launch(
                new BrowserType.LaunchOptions()
                        .setHeadless(false)
                        .setArgs(Arrays.asList(
                                "--disable-gpu",
                                "--no-sandbox",
                                "--disable-dev-shm-usage",
                                "--disable-http2")));
        page = browser.newPage();
    }

    private static void tearDown() {
        if (page != null) page.close();
        if (browser != null) browser.close();
        if (playwright != null) playwright.close();
    }
}
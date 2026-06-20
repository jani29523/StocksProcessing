package org.example;

import java.awt.Dimension;
import java.awt.Robot;
import java.awt.Toolkit;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class CMouseTracker {

    private static final int INTERVAL_MS  = 4 * 60 * 1_000; // 3 minutes between triggers
    private static final int ROUNDS       = 2;               // circles per trigger
    private static final int RADIUS       = 120;             // px radius of each circle
    private static final int STEPS        = 72;              // steps per circle (every 5 degrees)
    private static final int STEP_DELAY   = 20;              // ms between each step

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private static String now() {
        return LocalTime.now().format(TIME_FMT);
    }

    public static void main(String[] args) throws Exception {
        Robot robot = new Robot();
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        int cx = screen.width  / 2;
        int cy = screen.height / 2;

        System.out.println("[" + now() + "] CMouseTracker started — will move mouse every 3 minutes");

        while (true) {
            Thread.sleep(INTERVAL_MS);
            System.out.println("[" + now() + "] Triggering mouse movement (" + ROUNDS + " rounds)");

            for (int round = 1; round <= ROUNDS; round++) {
                System.out.println("[" + now() + "] Round " + round + "/" + ROUNDS + " started — mouse rotating");
                for (int step = 0; step <= STEPS; step++) {
                    double angle = 2 * Math.PI * step / STEPS;
                    int x = cx + (int) (RADIUS * Math.cos(angle));
                    int y = cy + (int) (RADIUS * Math.sin(angle));
                    robot.mouseMove(x, y);
                    Thread.sleep(STEP_DELAY);
                }
                System.out.println("[" + now() + "] Round " + round + "/" + ROUNDS + " done — mouse moved to (" + cx + "," + cy + ")");
            }

            System.out.println("[" + now() + "] Mouse movement complete — next trigger in 3 minutes");
        }
    }
}
/*
 * Copyright (c) 2025 Vitor Pamplona
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.imageio.ImageIO;

/**
 * Drives the desktop app on an X display (Xvfb in a headless container) with java.awt.Robot, so an
 * agent can look at the shared UI and click through it without xdotool or a window manager.
 * Pure JDK; run it as a single-file program against the same DISPLAY the app uses:
 *
 *   DISPLAY=:99 java tools/desktop-run/Drive.java shot /tmp/s.png
 *
 * Several steps can go in one call, separated by ";":  click 640 300 ; type hello ; key enter
 * See tools/desktop-run/README.md.
 */
public class Drive {
    static Robot robot;

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println("usage: Drive <cmd> [args] [; <cmd> [args] ...]  (see tools/desktop-run/README.md)");
            System.exit(2);
        }
        robot = new Robot();
        robot.setAutoDelay(20);

        List<List<String>> steps = new ArrayList<>();
        List<String> current = new ArrayList<>();
        for (String arg : args) {
            if (arg.equals(";")) {
                if (!current.isEmpty()) steps.add(current);
                current = new ArrayList<>();
            } else {
                current.add(arg);
            }
        }
        if (!current.isEmpty()) steps.add(current);

        for (List<String> step : steps) run(step);
        // Lets the app consume the last events (and read a pasted clipboard) before this JVM, which
        // owns the X selection, goes away.
        robot.delay(300);
        System.exit(0);
    }

    static void run(List<String> s) throws Exception {
        String cmd = s.get(0);
        switch (cmd) {
            case "size" -> {
                var d = Toolkit.getDefaultToolkit().getScreenSize();
                System.out.println(d.width + "x" + d.height);
            }
            case "shot" -> {
                // shot <file.png> [x y w h]
                Rectangle r = s.size() >= 6
                    ? new Rectangle(i(s, 2), i(s, 3), i(s, 4), i(s, 5))
                    : new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
                ImageIO.write(robot.createScreenCapture(r), "png", new File(s.get(1)));
                System.out.println("saved " + s.get(1));
            }
            case "move" -> robot.mouseMove(i(s, 1), i(s, 2));
            case "click" -> click(i(s, 1), i(s, 2), InputEvent.BUTTON1_DOWN_MASK, 1);
            case "dclick" -> click(i(s, 1), i(s, 2), InputEvent.BUTTON1_DOWN_MASK, 2);
            case "rclick" -> click(i(s, 1), i(s, 2), InputEvent.BUTTON3_DOWN_MASK, 1);
            case "down" -> {
                // down <x> <y>: press the left button there and hold it, to drag with move + up
                robot.mouseMove(i(s, 1), i(s, 2));
                robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            }
            case "up" -> {
                // up <x> <y>: release the held left button there
                robot.mouseMove(i(s, 1), i(s, 2));
                robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            }
            case "scroll" -> {
                // scroll <x> <y> <notches>: positive scrolls down
                robot.mouseMove(i(s, 1), i(s, 2));
                robot.mouseWheel(i(s, 3));
            }
            case "type" -> paste(String.join(" ", s.subList(1, s.size())));
            case "key" -> {
                for (int n = 1; n < s.size(); n++) chord(s.get(n));
            }
            case "sleep" -> robot.delay(i(s, 1));
            case "waitpaint" -> waitPaint(s.size() > 1 ? i(s, 1) : 60_000);
            default -> {
                System.err.println("unknown command: " + cmd);
                System.exit(2);
            }
        }
        robot.waitForIdle();
    }

    static int i(List<String> s, int idx) {
        return Integer.parseInt(s.get(idx));
    }

    static void click(int x, int y, int button, int count) {
        robot.mouseMove(x, y);
        for (int n = 0; n < count; n++) {
            robot.mousePress(button);
            robot.mouseRelease(button);
        }
    }

    /** Any text, any layout: through the clipboard and Ctrl+V rather than per-key keycodes. */
    static void paste(String text) {
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
        robot.delay(100);
        chord("ctrl+v");
    }

    /** ctrl+shift+k, enter, tab, escape, f5, a, ... */
    static void chord(String spec) {
        String[] parts = spec.toLowerCase().split("\\+");
        int[] codes = new int[parts.length];
        for (int n = 0; n < parts.length; n++) codes[n] = keyCode(parts[n]);
        for (int code : codes) robot.keyPress(code);
        for (int n = codes.length - 1; n >= 0; n--) robot.keyRelease(codes[n]);
    }

    static int keyCode(String name) {
        return switch (name) {
            case "ctrl", "control" -> KeyEvent.VK_CONTROL;
            case "shift" -> KeyEvent.VK_SHIFT;
            case "alt" -> KeyEvent.VK_ALT;
            case "meta", "cmd", "super" -> KeyEvent.VK_META;
            case "enter", "return" -> KeyEvent.VK_ENTER;
            case "tab" -> KeyEvent.VK_TAB;
            case "esc", "escape" -> KeyEvent.VK_ESCAPE;
            case "space" -> KeyEvent.VK_SPACE;
            case "backspace" -> KeyEvent.VK_BACK_SPACE;
            case "delete", "del" -> KeyEvent.VK_DELETE;
            case "up" -> KeyEvent.VK_UP;
            case "down" -> KeyEvent.VK_DOWN;
            case "left" -> KeyEvent.VK_LEFT;
            case "right" -> KeyEvent.VK_RIGHT;
            case "home" -> KeyEvent.VK_HOME;
            case "end" -> KeyEvent.VK_END;
            case "pageup" -> KeyEvent.VK_PAGE_UP;
            case "pagedown" -> KeyEvent.VK_PAGE_DOWN;
            default -> {
                if (name.matches("f([1-9]|1[0-2])")) yield KeyEvent.VK_F1 + Integer.parseInt(name.substring(1)) - 1;
                if (name.length() == 1) yield KeyEvent.getExtendedKeyCodeForChar(name.charAt(0));
                throw new IllegalArgumentException("unknown key: " + name);
            }
        };
    }

    /**
     * Waits until something is drawn: Xvfb starts as one flat colour, so the first screen with a few
     * dozen colours means the window has painted. Exits 1 on timeout.
     */
    static void waitPaint(int timeoutMs) {
        Rectangle screen = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            BufferedImage img = robot.createScreenCapture(screen);
            Set<Integer> colors = new HashSet<>();
            for (int y = 0; y < img.getHeight() && colors.size() < 64; y += 7) {
                for (int x = 0; x < img.getWidth() && colors.size() < 64; x += 7) colors.add(img.getRGB(x, y));
            }
            if (colors.size() >= 64) {
                System.out.println("painted");
                return;
            }
            robot.delay(500);
        }
        System.err.println("nothing painted within " + timeoutMs + " ms");
        System.exit(1);
    }
}

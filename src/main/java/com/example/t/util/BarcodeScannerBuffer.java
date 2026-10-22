package com.example.t.util;

import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.TextInputControl;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * High-performance, decoupled hardware barcode scanner wedge buffer.
 *
 * Distinguishes continuous rapid hardware scanner keystrokes (<= 40ms interval)
 * from manual human typing (120ms - 250ms interval).
 * Supports injectable clock for deterministic unit testing, Enter terminator detection,
 * and automatic cleanup of stray barcode digits from focused text input controls.
 */
public class BarcodeScannerBuffer {

    public static final long DEFAULT_THRESHOLD_MS = 40L;
    public static final int DEFAULT_MIN_LENGTH = 4;

    private final long maxInterKeystrokeIntervalMs;
    private final int minBarcodeLength;
    private final LongSupplier clock;

    private final StringBuilder buffer = new StringBuilder(64);
    private long lastKeystrokeTimeMs = 0L;
    private long firstKeystrokeTimeMs = 0L;
    private boolean isBurstCandidate = true;

    public BarcodeScannerBuffer() {
        this(DEFAULT_THRESHOLD_MS, DEFAULT_MIN_LENGTH, System::currentTimeMillis);
    }

    public BarcodeScannerBuffer(long maxInterKeystrokeIntervalMs, int minBarcodeLength) {
        this(maxInterKeystrokeIntervalMs, minBarcodeLength, System::currentTimeMillis);
    }

    public BarcodeScannerBuffer(long maxInterKeystrokeIntervalMs, int minBarcodeLength, LongSupplier clock) {
        this.maxInterKeystrokeIntervalMs = maxInterKeystrokeIntervalMs;
        this.minBarcodeLength = minBarcodeLength;
        this.clock = Objects.requireNonNull(clock, "clock supplier must not be null");
    }

    /**
     * Dispatches key events to buffer logic.
     * Handles KEY_TYPED (character accumulation) and KEY_PRESSED (Enter terminator / Escape).
     */
    public void handleKeyEvent(KeyEvent event, Consumer<String> onBarcodeDetected) {
        if (event == null) return;

        long now = clock.getAsLong();

        // 1. Check for Terminator (Enter key)
        boolean isEnterPressed = (event.getEventType() == KeyEvent.KEY_PRESSED && event.getCode() == KeyCode.ENTER);
        boolean isEnterTyped = (event.getEventType() == KeyEvent.KEY_TYPED &&
                ("\r".equals(event.getCharacter()) || "\n".equals(event.getCharacter())));

        if (isEnterPressed || isEnterTyped) {
            handleTerminator(event, onBarcodeDetected, now);
            return;
        }

        // 2. Clear on Escape
        if (event.getEventType() == KeyEvent.KEY_PRESSED && event.getCode() == KeyCode.ESCAPE) {
            reset();
            return;
        }

        // 3. Process Printable Characters from KEY_TYPED
        if (event.getEventType() == KeyEvent.KEY_TYPED) {
            String ch = event.getCharacter();
            if (ch == null || ch.isEmpty()) return;
            char c = ch.charAt(0);

            if (Character.isISOControl(c)) return;

            long interval = now - lastKeystrokeTimeMs;

            if (buffer.length() == 0) {
                buffer.append(c);
                firstKeystrokeTimeMs = now;
                lastKeystrokeTimeMs = now;
                isBurstCandidate = true;
            } else {
                if (interval > maxInterKeystrokeIntervalMs) {
                    // Interval exceeded threshold -> reset and start new candidate
                    buffer.setLength(0);
                    buffer.append(c);
                    firstKeystrokeTimeMs = now;
                    lastKeystrokeTimeMs = now;
                    isBurstCandidate = true;
                } else {
                    buffer.append(c);
                    lastKeystrokeTimeMs = now;
                }
            }
        }
    }

    private void handleTerminator(KeyEvent event, Consumer<String> onBarcodeDetected, long now) {
        long interval = now - lastKeystrokeTimeMs;
        String candidate = buffer.toString().trim();

        // A valid burst satisfies: candidate is flagged as burst candidate, has at least minBarcodeLength,
        // and the terminator arrived within threshold (or 2x threshold for multi-packet scanners).
        boolean validBurst = isBurstCandidate
                && candidate.length() >= minBarcodeLength
                && (buffer.length() == 0 || interval <= maxInterKeystrokeIntervalMs || (candidate.length() >= 8 && interval <= maxInterKeystrokeIntervalMs * 2));

        if (validBurst) {
            event.consume();
            cleanupFocusedTextInput(event, candidate);
            reset();

            if (onBarcodeDetected != null) {
                onBarcodeDetected.accept(candidate);
            }
        } else {
            reset();
        }
    }

    /**
     * Cleans up stray barcode digits that were typed into a focused TextInputControl.
     */
    private void cleanupFocusedTextInput(KeyEvent event, String barcode) {
        Object source = event.getTarget() != null ? event.getTarget() : event.getSource();
        if (source instanceof TextInputControl textInput) {
            String text = textInput.getText();
            if (text != null && text.endsWith(barcode)) {
                String restored = text.substring(0, text.length() - barcode.length());
                textInput.setText(restored);
                textInput.positionCaret(restored.length());
            }
        }
    }

    public void reset() {
        buffer.setLength(0);
        lastKeystrokeTimeMs = 0L;
        firstKeystrokeTimeMs = 0L;
        isBurstCandidate = true;
    }

    public void attachTo(Node node, Consumer<String> onBarcodeDetected) {
        if (node == null) return;
        EventHandler<KeyEvent> filter = event -> handleKeyEvent(event, onBarcodeDetected);
        node.addEventFilter(KeyEvent.KEY_TYPED, filter);
        node.addEventFilter(KeyEvent.KEY_PRESSED, filter);
    }

    public void attachTo(Scene scene, Consumer<String> onBarcodeDetected) {
        if (scene == null) return;
        EventHandler<KeyEvent> filter = event -> handleKeyEvent(event, onBarcodeDetected);
        scene.addEventFilter(KeyEvent.KEY_TYPED, filter);
        scene.addEventFilter(KeyEvent.KEY_PRESSED, filter);
    }

    public int getBufferLength() {
        return buffer.length();
    }

    public String getCurrentBuffer() {
        return buffer.toString();
    }

    public long getMaxInterKeystrokeIntervalMs() {
        return maxInterKeystrokeIntervalMs;
    }

    public int getMinBarcodeLength() {
        return minBarcodeLength;
    }
}

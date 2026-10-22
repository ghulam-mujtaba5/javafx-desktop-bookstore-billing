package com.example.t.util;

import javafx.application.Platform;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

public class BarcodeScannerBufferTest {

    @BeforeAll
    public static void initJavaFX() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {
            // Toolkit already started
        }
    }

    @Test
    @DisplayName("Rapid burst (<= 40ms) with Enter terminator fires barcode callback")
    public void testRapidBurstBarcodeDetection() {
        AtomicLong virtualClock = new AtomicLong(1000L);
        BarcodeScannerBuffer buffer = new BarcodeScannerBuffer(40L, 4, virtualClock::get);

        List<String> detected = new ArrayList<>();
        String testBarcode = "9780132350884";

        for (char c : testBarcode.toCharArray()) {
            KeyEvent typed = new KeyEvent(KeyEvent.KEY_TYPED, String.valueOf(c), "", KeyCode.UNDEFINED, false, false, false, false);
            buffer.handleKeyEvent(typed, detected::add);
            virtualClock.addAndGet(15L); // 15ms interval (hardware scanner burst)
        }

        KeyEvent enterEvent = new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ENTER, false, false, false, false);
        buffer.handleKeyEvent(enterEvent, detected::add);

        assertEquals(1, detected.size());
        assertEquals("9780132350884", detected.get(0));
        assertTrue(enterEvent.isConsumed(), "Terminator event must be consumed");
        assertEquals(0, buffer.getBufferLength(), "Buffer should be reset after dispatch");
    }

    @Test
    @DisplayName("Slow human typing (>= 120ms) does not trigger barcode callback")
    public void testSlowHumanTypingRejected() {
        AtomicLong virtualClock = new AtomicLong(1000L);
        BarcodeScannerBuffer buffer = new BarcodeScannerBuffer(40L, 4, virtualClock::get);

        List<String> detected = new ArrayList<>();
        String manualText = "BOOK";

        for (char c : manualText.toCharArray()) {
            KeyEvent typed = new KeyEvent(KeyEvent.KEY_TYPED, String.valueOf(c), "", KeyCode.UNDEFINED, false, false, false, false);
            buffer.handleKeyEvent(typed, detected::add);
            virtualClock.addAndGet(150L); // 150ms human typing cadence
        }

        KeyEvent enterEvent = new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ENTER, false, false, false, false);
        buffer.handleKeyEvent(enterEvent, detected::add);

        assertTrue(detected.isEmpty(), "Slow typing should not trigger barcode callback");
        assertFalse(enterEvent.isConsumed(), "Manual enter must not be consumed");
    }

    @Test
    @DisplayName("Short burst (< minBarcodeLength) is ignored")
    public void testShortBurstIgnored() {
        AtomicLong virtualClock = new AtomicLong(1000L);
        BarcodeScannerBuffer buffer = new BarcodeScannerBuffer(40L, 4, virtualClock::get);

        List<String> detected = new ArrayList<>();
        String shortCode = "123"; // only 3 chars

        for (char c : shortCode.toCharArray()) {
            KeyEvent typed = new KeyEvent(KeyEvent.KEY_TYPED, String.valueOf(c), "", KeyCode.UNDEFINED, false, false, false, false);
            buffer.handleKeyEvent(typed, detected::add);
            virtualClock.addAndGet(10L);
        }

        KeyEvent enterEvent = new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ENTER, false, false, false, false);
        buffer.handleKeyEvent(enterEvent, detected::add);

        assertTrue(detected.isEmpty(), "Candidates shorter than min length must not trigger");
    }

    @Test
    @DisplayName("Stray barcode digits stripped from focused TextInputControl")
    public void testCleanupStrayBarcodeFromTextInput() {
        AtomicLong virtualClock = new AtomicLong(1000L);
        BarcodeScannerBuffer buffer = new BarcodeScannerBuffer(40L, 4, virtualClock::get);

        TextField field = new TextField();
        field.setText("Customer John");

        List<String> detected = new ArrayList<>();
        String barcode = "789012";

        // Simulate typing into field by appending to text
        for (char c : barcode.toCharArray()) {
            field.setText(field.getText() + c);
            KeyEvent typed = new KeyEvent(field, field, KeyEvent.KEY_TYPED, String.valueOf(c), "", KeyCode.UNDEFINED, false, false, false, false);
            buffer.handleKeyEvent(typed, detected::add);
            virtualClock.addAndGet(12L);
        }

        assertEquals("Customer John789012", field.getText());

        KeyEvent enterEvent = new KeyEvent(field, field, KeyEvent.KEY_PRESSED, "", "", KeyCode.ENTER, false, false, false, false);
        buffer.handleKeyEvent(enterEvent, detected::add);

        assertEquals(1, detected.size());
        assertEquals("789012", detected.get(0));
        assertEquals("Customer John", field.getText(), "Stray barcode digits must be stripped from TextField");
    }

    @Test
    @DisplayName("Escape key resets the buffer")
    public void testEscapeKeyResetsBuffer() {
        AtomicLong virtualClock = new AtomicLong(1000L);
        BarcodeScannerBuffer buffer = new BarcodeScannerBuffer(40L, 4, virtualClock::get);

        KeyEvent typed = new KeyEvent(KeyEvent.KEY_TYPED, "9", "", KeyCode.UNDEFINED, false, false, false, false);
        buffer.handleKeyEvent(typed, null);
        assertEquals(1, buffer.getBufferLength());

        KeyEvent esc = new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ESCAPE, false, false, false, false);
        buffer.handleKeyEvent(esc, null);
        assertEquals(0, buffer.getBufferLength());
    }

    @Test
    @DisplayName("Carriage return / newline in KEY_TYPED terminates burst")
    public void testKeyTypedCarriageReturnTerminator() {
        AtomicLong virtualClock = new AtomicLong(1000L);
        BarcodeScannerBuffer buffer = new BarcodeScannerBuffer(40L, 4, virtualClock::get);

        List<String> detected = new ArrayList<>();
        String barcode = "4006381333931";

        for (char c : barcode.toCharArray()) {
            KeyEvent typed = new KeyEvent(KeyEvent.KEY_TYPED, String.valueOf(c), "", KeyCode.UNDEFINED, false, false, false, false);
            buffer.handleKeyEvent(typed, detected::add);
            virtualClock.addAndGet(10L);
        }

        KeyEvent crTyped = new KeyEvent(KeyEvent.KEY_TYPED, "\r", "", KeyCode.UNDEFINED, false, false, false, false);
        buffer.handleKeyEvent(crTyped, detected::add);

        assertEquals(1, detected.size());
        assertEquals("4006381333931", detected.get(0));
    }
}

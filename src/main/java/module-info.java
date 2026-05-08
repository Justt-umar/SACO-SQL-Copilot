module com.example.aisqlcopilot {
    // ── JavaFX ───────────────────────────────────────────────
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.web;

    // ── JDK ──────────────────────────────────────────────────
    requires java.net.http;
    requires java.desktop;

    // ── JSON (Jackson) ───────────────────────────────────────
    requires com.fasterxml.jackson.databind;
    requires com.fasterxml.jackson.core;

    // ── Logging (SLF4J) ──────────────────────────────────────
    requires org.slf4j;

    opens com.example.aisqlcopilot to javafx.fxml, com.fasterxml.jackson.databind;
    exports com.example.aisqlcopilot;
}
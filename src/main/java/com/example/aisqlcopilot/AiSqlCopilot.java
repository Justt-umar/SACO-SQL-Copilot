package com.example.aisqlcopilot;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.scene.image.Image;


// IMPORTS FOR ROBOT & CLIPBOARD
import java.awt.Robot;
import java.awt.event.KeyEvent;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * S.A.C.O. — SQL Agentic Co-Pilot Orchestrator
 *
 * Cross-platform (macOS + Windows) desktop AI assistant that bridges
 * MySQL Workbench with an n8n-hosted AI Agent for SQL code review,
 * debugging, and direct query execution.
 *
 * Core workflow:
 *   1. Scan IDE   — Pull code from MySQL Workbench into the local editor
 *   2. AI Review  — Send code to n8n AI Agent for analysis
 *   3. Grab Code  — Extract AI-generated SQL from chat history
 *   4. Apply      — Inject corrected code back into MySQL Workbench
 */
public class AiSqlCopilot extends Application {

    private static final String N8N_WEBHOOK_URL = "http://localhost:5678/webhook/saco-chat";

    private TextArea chatHistory;
    private TextField inputField;
    private Button sendButton;
    private TextArea codeEditor;
    private Button runCodeButton;
    private Button scanButton;
    private final HttpClient httpClient;

    /** The display name of the target IDE (OS-aware via OsAutomation). */
    private final String ideName = OsAutomation.targetIdeName();

    public AiSqlCopilot() {
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("S.A.C.O. — AI SQL Co-pilot Workspace");

        // ── App Icon ──────────────────────────────────────────────
        try {
            var iconStream = getClass().getResourceAsStream("/saco-icon.png");
            if (iconStream == null) {
                iconStream = getClass().getResourceAsStream("saco-icon.png");
            }
            if (iconStream != null) {
                primaryStage.getIcons().add(new Image(iconStream));
            } else {
                primaryStage.getIcons().add(new Image("file:saco-icon.png"));
            }
        } catch (Exception e) {
            System.out.println("Could not load icon: " + e.getMessage());
        }

        // ── Monospace font (OS-aware) ────────────────────────────
        String monoFont = OsAutomation.monoFont();

        // ── LEFT SIDE: CHAT PANEL ────────────────────────────────
        chatHistory = new TextArea();
        chatHistory.setEditable(false);
        chatHistory.setWrapText(true);
        chatHistory.setStyle("-fx-font-family: " + monoFont + "; -fx-font-size: 14px;");
        VBox.setVgrow(chatHistory, Priority.ALWAYS);

        inputField = new TextField();
        inputField.setPromptText("Ask S.A.C.O. to review or draft a query...");
        inputField.setStyle("-fx-font-size: 14px;");
        HBox.setHgrow(inputField, Priority.ALWAYS);

        sendButton = new Button("Send");
        sendButton.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");

        sendButton.setOnAction(e -> sendMessage(inputField.getText(), false));
        inputField.setOnAction(e -> sendMessage(inputField.getText(), false));

        HBox inputBox = new HBox(10, inputField, sendButton);
        inputBox.setPadding(new Insets(10, 0, 0, 0));
        VBox chatRoot = new VBox(10, chatHistory, inputBox);
        chatRoot.setPadding(new Insets(15));

        // ── RIGHT SIDE: CODE EDITOR PANEL ────────────────────────

        // 1. Scan IDE Button
        scanButton = new Button("🔍 Scan " + ideName);
        scanButton.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-background-color: #007acc; -fx-text-fill: white;");
        scanButton.setMaxWidth(Double.MAX_VALUE);
        scanButton.setOnAction(e -> scanIdeClipboard());

        // 2. Grab AI Code Button
        Button grabCodeButton = new Button("⬅️ Grab AI Code");
        grabCodeButton.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-background-color: #6f42c1; -fx-text-fill: white;");
        grabCodeButton.setMaxWidth(Double.MAX_VALUE);
        grabCodeButton.setOnAction(e -> extractCodeToEditor());

        // 3. Apply to IDE Button
        Button applyButton = new Button("⬇️ Apply to " + ideName);
        applyButton.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-background-color: #ffc107; -fx-text-fill: black;");
        applyButton.setMaxWidth(Double.MAX_VALUE);
        applyButton.setOnAction(e -> applyToIde());

        codeEditor = new TextArea();
        codeEditor.setPromptText("-- Click 'Scan " + ideName + "' to pull your code here,\n-- or type it manually...");
        codeEditor.setStyle("-fx-font-family: " + monoFont + "; -fx-font-size: 14px; -fx-control-inner-background: #1e1e1e; -fx-text-fill: #d4d4d4;");
        VBox.setVgrow(codeEditor, Priority.ALWAYS);

        runCodeButton = new Button("▶ Run Code");
        runCodeButton.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-background-color: #28a745; -fx-text-fill: white;");
        runCodeButton.setMaxWidth(Double.MAX_VALUE);
        runCodeButton.setOnAction(e -> sendMessage(codeEditor.getText(), true));

        VBox codeRoot = new VBox(10, scanButton, grabCodeButton, applyButton, codeEditor, runCodeButton);
        codeRoot.setPadding(new Insets(15));

        // ── COMBINE USING SPLITPANE ──────────────────────────────
        SplitPane splitPane = new SplitPane();
        splitPane.getItems().addAll(chatRoot, codeRoot);
        splitPane.setDividerPositions(0.4);

        Scene scene = new Scene(splitPane, 1000, 600);
        primaryStage.setScene(scene);

        // ALWAYS ON TOP & OPACITY
        primaryStage.setAlwaysOnTop(true);
        primaryStage.setOpacity(0.95);

        primaryStage.show();

        // ── Startup message (OS-aware) ───────────────────────────
        String osNote = OsAutomation.isMac()
                ? "Running on macOS — ensure Accessibility permission is granted\n" +
                  "(System Settings → Privacy & Security → Accessibility).\n"
                : "Running on Windows.\n";

        chatHistory.appendText("S.A.C.O. initialized. Target IDE: " + ideName + "\n"
                + osNote
                + "Make sure " + ideName + " is open before clicking Scan!\n\n");
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  SCAN IDE — Pull code from MySQL Workbench into the editor
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private void scanIdeClipboard() {
        chatHistory.appendText("System: Scanning " + ideName + "...\n");
        scanButton.setDisable(true);

        new Thread(() -> {
            try {
                Robot robot = new Robot();

                // 1. Switch to the target IDE
                OsAutomation.switchToIde(robot);
                Thread.sleep(500); // Wait for OS to complete the switch

                // 2. Select All (Cmd+A / Ctrl+A)
                OsAutomation.modifierCombo(robot, KeyEvent.VK_A);
                Thread.sleep(150);

                // 3. Copy (Cmd+C / Ctrl+C)
                OsAutomation.modifierCombo(robot, KeyEvent.VK_C);
                Thread.sleep(300); // Wait for clipboard to populate

                // 4. Read text from the system clipboard
                Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                String copiedText = (String) clipboard.getData(DataFlavor.stringFlavor);

                // 5. Update UI on the JavaFX thread
                Platform.runLater(() -> {
                    codeEditor.setText(copiedText);
                    chatHistory.appendText("System: Code successfully pulled from " + ideName + ".\n\n");
                    scanButton.setDisable(false);
                });

            } catch (Exception ex) {
                Platform.runLater(() -> {
                    chatHistory.appendText("Error scanning " + ideName + ": " + ex.getMessage() + "\n\n");
                    scanButton.setDisable(false);
                });
            }
        }).start();
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  APPLY TO IDE — Inject corrected code back into MySQL Workbench
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private void applyToIde() {
        String codeToInject = codeEditor.getText();
        if (codeToInject == null || codeToInject.trim().isEmpty()) return;

        chatHistory.appendText("System: Injecting code back into " + ideName + "...\n");

        new Thread(() -> {
            try {
                // 1. Put the new code onto the system clipboard
                StringSelection stringSelection = new StringSelection(codeToInject);
                Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                clipboard.setContents(stringSelection, null);

                Robot robot = new Robot();

                // 2. Switch to the target IDE
                OsAutomation.switchToIde(robot);
                Thread.sleep(1000); // Wait for window switch

                // 3. Select All (Cmd+A / Ctrl+A)
                OsAutomation.modifierCombo(robot, KeyEvent.VK_A);
                Thread.sleep(150);

                // 4. Paste (Cmd+V / Ctrl+V) — overwrites selected text
                OsAutomation.modifierCombo(robot, KeyEvent.VK_V);

                Platform.runLater(() -> {
                    chatHistory.appendText("System: Code successfully applied to " + ideName + ".\n\n");
                });

            } catch (Exception ex) {
                Platform.runLater(() -> chatHistory.appendText("Error injecting to " + ideName + ": " + ex.getMessage() + "\n\n"));
            }
        }).start();
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  GRAB AI CODE — Extract the latest SQL block from chat history
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private void extractCodeToEditor() {
        String fullChat = chatHistory.getText();

        // Find the absolute last occurrence of "```sql" in the chat
        int lastSqlStart = fullChat.lastIndexOf("```sql");

        if (lastSqlStart != -1) {
            // Find the closing "```" after the starting block
            int sqlEnd = fullChat.indexOf("```", lastSqlStart + 6);

            if (sqlEnd != -1) {
                String extractedCode = fullChat.substring(lastSqlStart + 6, sqlEnd).trim();
                codeEditor.setText(extractedCode);
                chatHistory.appendText("System: AI code successfully moved to editor.\n\n");
            } else {
                chatHistory.appendText("System: Found start of code, but no closing formatting.\n\n");
            }
        } else {
            chatHistory.appendText("System: Could not find any formatted SQL code blocks (```sql) in the chat history.\n\n");
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  SEND MESSAGE — POST to n8n AI Agent and display the response
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private void sendMessage(String textToSend, boolean isExecution) {
        String rawText = textToSend.trim();
        if (rawText.isEmpty()) return;

        if (isExecution) {
            chatHistory.appendText("System: Requesting execution of code in editor...\n");
        } else {
            chatHistory.appendText("You: " + rawText + "\n");
            inputField.clear();
        }

        inputField.setDisable(true);
        sendButton.setDisable(true);
        runCodeButton.setDisable(true);
        scanButton.setDisable(true);
        chatHistory.appendText("S.A.C.O. is processing...\n");

        String prompt = isExecution ? "SYSTEM_COMMAND_EXECUTE:\n" + rawText : rawText;
        String safePrompt = prompt.replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");

        String currentCode = codeEditor.getText()
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "");

        String jsonPayload = "{\"userPrompt\":\"" + safePrompt + "\", \"currentCode\":\"" + currentCode + "\"}";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(N8N_WEBHOOK_URL))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

        httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> {
                    Platform.runLater(() -> {
                        String currentText = chatHistory.getText();
                        chatHistory.setText(currentText.replace("S.A.C.O. is processing...\n", ""));

                        if (response.statusCode() == 200) {
                            chatHistory.appendText("S.A.C.O.:\n" + response.body() + "\n\n");
                        } else {
                            chatHistory.appendText("Error: Received " + response.statusCode() + " from server.\n\n");
                        }

                        inputField.setDisable(false);
                        sendButton.setDisable(false);
                        runCodeButton.setDisable(false);
                        scanButton.setDisable(false);
                        inputField.requestFocus();
                    });
                })
                .exceptionally(ex -> {
                    Platform.runLater(() -> {
                        String currentText = chatHistory.getText();
                        chatHistory.setText(currentText.replace("S.A.C.O. is processing...\n", ""));
                        chatHistory.appendText("Network Error: " + ex.getMessage() + "\n\n");
                        inputField.setDisable(false);
                        sendButton.setDisable(false);
                        runCodeButton.setDisable(false);
                        scanButton.setDisable(false);
                    });
                    return null;
                });
    }

    public static void main(String[] args) {
        launch(args);
    }
}
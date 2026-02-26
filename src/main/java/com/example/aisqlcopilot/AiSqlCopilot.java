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


// NEW IMPORTS FOR ROBOT & CLIPBOARD
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
import java.util.Objects;

public class AiSqlCopilot extends Application {

    private static final String N8N_WEBHOOK_URL = "http://localhost:5678/webhook/saco-chat";

    private TextArea chatHistory;
    private TextField inputField;
    private Button sendButton;
    private TextArea codeEditor;
    private Button runCodeButton;
    private Button scanButton; // NEW: The Scan Button
    private final HttpClient httpClient;

    public AiSqlCopilot() {
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("S.A.C.O. - AI SQL Co-pilot Workspace");

        try {
            // Path A: The standard resources root (Maven/Gradle)
            var iconStream = getClass().getResourceAsStream("/saco-icon.png");

            // Path B: If Path A fails, look relative to the class
            if (iconStream == null) {
                iconStream = getClass().getResourceAsStream("saco-icon.png");
            }

            // Path C: Try loading it as a direct file path if running locally
            if (iconStream != null) {
                primaryStage.getIcons().add(new Image(iconStream));
            } else {
                // If all Classpath attempts fail, try the local disk as a last resort
                primaryStage.getIcons().add(new Image("file:saco-icon.png"));
            }
        } catch (Exception e) {
            System.out.println("Could not load icon: " + e.getMessage());
        }
        // --- LEFT SIDE: CHAT PANEL ---
        chatHistory = new TextArea();
        chatHistory.setEditable(false);
        chatHistory.setWrapText(true);
        chatHistory.setStyle("-fx-font-family: 'Consolas'; -fx-font-size: 14px;");
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

        // --- RIGHT SIDE: CODE EDITOR PANEL ---

        // NEW: Scan SSMS Button at the top
        scanButton = new Button("🔍 Scan SSMS");
        scanButton.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-background-color: #007acc; -fx-text-fill: white;");
        scanButton.setMaxWidth(Double.MAX_VALUE);
        scanButton.setOnAction(e -> scanSsmsClipboard());

        // Create the new Apply button
        Button applyButton = new Button("⬇️ Apply to SSMS");
        applyButton.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-background-color: #ffc107; -fx-text-fill: black;");
        applyButton.setMaxWidth(Double.MAX_VALUE);
        applyButton.setOnAction(e -> applyToSsms());

        // Add applyButton to the VBox layout

        codeEditor = new TextArea();
        codeEditor.setPromptText("-- Click 'Scan SSMS' to pull your code here,\n-- or type it manually...");
        codeEditor.setStyle("-fx-font-family: 'Consolas'; -fx-font-size: 14px; -fx-control-inner-background: #1e1e1e; -fx-text-fill: #d4d4d4;");
        VBox.setVgrow(codeEditor, Priority.ALWAYS);

        runCodeButton = new Button("▶ Run Code");
        runCodeButton.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-background-color: #28a745; -fx-text-fill: white;");
        runCodeButton.setMaxWidth(Double.MAX_VALUE);
        runCodeButton.setOnAction(e -> sendMessage(codeEditor.getText(), true));

        // 1. Existing Scan Button
        scanButton = new Button("🔍 Scan SSMS");
        scanButton.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-background-color: #007acc; -fx-text-fill: white;");
        scanButton.setMaxWidth(Double.MAX_VALUE);
        scanButton.setOnAction(e -> scanSsmsClipboard());

        // 2. NEW: Grab AI Code Button
        Button grabCodeButton = new Button("⬅️ Grab AI Code");
        grabCodeButton.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-background-color: #6f42c1; -fx-text-fill: white;");
        grabCodeButton.setMaxWidth(Double.MAX_VALUE);
        grabCodeButton.setOnAction(e -> extractCodeToEditor());

        // 3. Existing Apply Button
        Button applyButton2 = new Button("⬇️ Apply to SSMS");
        applyButton2.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-background-color: #ffc107; -fx-text-fill: black;");
        applyButton2.setMaxWidth(Double.MAX_VALUE);
        applyButton2.setOnAction(e -> applyToSsms());

        // Add the scan button to the top of the right panel
        VBox codeRoot = new VBox(10, scanButton, grabCodeButton, applyButton2, codeEditor, runCodeButton);
        codeRoot.setPadding(new Insets(15));

        // --- COMBINE USING SPLITPANE ---
        SplitPane splitPane = new SplitPane();
        splitPane.getItems().addAll(chatRoot, codeRoot);
        splitPane.setDividerPositions(0.4);

        Scene scene = new Scene(splitPane, 1000, 600);
        primaryStage.setScene(scene);

        // ALWAYS ON TOP & OPACITY APPLIED HERE
        primaryStage.setAlwaysOnTop(true);
        primaryStage.setOpacity(0.95);

        primaryStage.show();

        chatHistory.appendText("S.A.C.O. initialized with SSMS Scanner.\nMake sure SSMS is your active window before clicking Scan!\n\n");
    }

    // NEW: The "Ghost" Keyboard Automation
    private void scanSsmsClipboard() {
        chatHistory.appendText("System: Scanning SSMS...\n");
        scanButton.setDisable(true);

        // Run in a background thread so we don't freeze the JavaFX UI
        new Thread(() -> {
            try {
                Robot robot = new Robot();

                // 1. Alt + Tab to switch to the last active window (SSMS)
                robot.keyPress(KeyEvent.VK_ALT);
                robot.keyPress(KeyEvent.VK_TAB);
                robot.keyRelease(KeyEvent.VK_TAB);
                robot.keyRelease(KeyEvent.VK_ALT);

                Thread.sleep(400); // Wait a fraction of a second for OS to switch windows

                // 2. Ctrl + A to Select All
                robot.keyPress(KeyEvent.VK_CONTROL);
                robot.keyPress(KeyEvent.VK_A);
                robot.keyRelease(KeyEvent.VK_A);
                robot.keyRelease(KeyEvent.VK_CONTROL);

                Thread.sleep(150); // Small pause

                // 3. Ctrl + C to Copy
                robot.keyPress(KeyEvent.VK_CONTROL);
                robot.keyPress(KeyEvent.VK_C);
                robot.keyRelease(KeyEvent.VK_C);
                robot.keyRelease(KeyEvent.VK_CONTROL);

                Thread.sleep(250); // Wait for the OS to lock the text into the clipboard

                // 4. Read the text from the System Clipboard
                Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                String copiedText = (String) clipboard.getData(DataFlavor.stringFlavor);

                // 5. Update the UI back on the main JavaFX thread
                Platform.runLater(() -> {
                    codeEditor.setText(copiedText);
                    chatHistory.appendText("System: Code successfully pulled from SSMS.\n\n");
                    scanButton.setDisable(false);
                });

            } catch (Exception ex) {
                Platform.runLater(() -> {
                    chatHistory.appendText("Error scanning SSMS: " + ex.getMessage() + "\n\n");
                    scanButton.setDisable(false);
                });
            }
        }).start();
    }

    private void applyToSsms() {
        String codeToInject = codeEditor.getText();
        if (codeToInject == null || codeToInject.trim().isEmpty()) return;

        chatHistory.appendText("System: Injecting code back into SSMS...\n");

        new Thread(() -> {
            try {
                // 1. Put the new code onto the OS Clipboard
                StringSelection stringSelection = new StringSelection(codeToInject);
                Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                clipboard.setContents(stringSelection, null);

                Robot robot = new Robot();

                // 2. Alt + Tab back to SSMS
                robot.keyPress(KeyEvent.VK_ALT);
                robot.keyPress(KeyEvent.VK_TAB);
                robot.keyRelease(KeyEvent.VK_TAB);
                robot.keyRelease(KeyEvent.VK_ALT);

                Thread.sleep(1000); // Wait for window switch

                // 3. Select All (Ctrl + A) to highlight the bad code
                robot.keyPress(KeyEvent.VK_CONTROL);
                robot.keyPress(KeyEvent.VK_A);
                robot.keyRelease(KeyEvent.VK_A);
                robot.keyRelease(KeyEvent.VK_CONTROL);

                Thread.sleep(150);

                // 4. Paste (Ctrl + V) to overwrite it with the good code
                robot.keyPress(KeyEvent.VK_CONTROL);
                robot.keyPress(KeyEvent.VK_V);
                robot.keyRelease(KeyEvent.VK_V);
                robot.keyRelease(KeyEvent.VK_CONTROL);

                Platform.runLater(() -> {
                    chatHistory.appendText("System: Code successfully applied to SSMS.\n\n");
                });

            } catch (Exception ex) {
                Platform.runLater(() -> chatHistory.appendText("Error injecting to SSMS: " + ex.getMessage() + "\n\n"));
            }
        }).start();
    }

    // NEW: Automatically extract code from the chat window to the editor
    private void extractCodeToEditor() {
        String fullChat = chatHistory.getText();

        // Find the absolute last occurrence of "```sql" in the chat
        int lastSqlStart = fullChat.lastIndexOf("```sql");

        if (lastSqlStart != -1) {
            // Find the closing "```" after the starting block
            int sqlEnd = fullChat.indexOf("```", lastSqlStart + 6);

            if (sqlEnd != -1) {
                // Extract the string between the markers and remove extra whitespace
                String extractedCode = fullChat.substring(lastSqlStart + 6, sqlEnd).trim();

                // Set the editor text to the extracted code
                codeEditor.setText(extractedCode);
                chatHistory.appendText("System: AI code successfully moved to editor.\n\n");
            } else {
                chatHistory.appendText("System: Found start of code, but no closing formatting.\n\n");
            }
        } else {
            chatHistory.appendText("System: Could not find any formatted SQL code blocks (```sql) in the chat history.\n\n");
        }
    }

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
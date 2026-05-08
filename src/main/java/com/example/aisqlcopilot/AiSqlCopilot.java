package com.example.aisqlcopilot;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.*;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.geometry.Rectangle2D;
import javafx.stage.Screen;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Robot;
import java.awt.event.KeyEvent;
import java.util.Optional;
import java.util.concurrent.*;

/**
 * S.A.C.O. v2 — SQL Agentic Co-Pilot Orchestrator
 *
 * Complete rewrite of the v1 monolith:
 * <ul>
 *   <li>Architecture: delegates to {@link N8nClient}, {@link CodeParser},
 *       {@link OsAutomation}, and {@link SacoConfig}</li>
 *   <li>UI: premium dark theme via external CSS, message-card chat,
 *       keyboard shortcuts, settings dialog, connection indicator</li>
 *   <li>Safety: Jackson JSON (no injection), auth tokens, session isolation</li>
 *   <li>Robustness: daemon executor, shared Robot, clipboard polling</li>
 * </ul>
 */
public class AiSqlCopilot extends Application {

    private static final Logger log = LoggerFactory.getLogger(AiSqlCopilot.class);

    // ── Dependencies ─────────────────────────────────────────
    private final SacoConfig config   = new SacoConfig();
    private final N8nClient  n8nClient = new N8nClient(config);
    private final String     ideName  = OsAutomation.targetIdeName();

    /** Shared daemon executor for background work (no orphan threads). */
    private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "saco-worker");
        t.setDaemon(true);
        return t;
    });

    /** Shared Robot instance (expensive to create, reuse it). */
    private Robot robot;

    // ── UI Components ────────────────────────────────────────
    private VBox       chatMessages;
    private ScrollPane chatScroll;
    private TextField  inputField;
    private Button     sendButton;
    private TextArea   codeEditor;
    private Button     runCodeButton;
    private Button     scanButton;
    private Button     grabButton;
    private Button     applyButton;
    private Label      statusLabel;
    private Stage      primaryStage;

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  APPLICATION LIFECYCLE
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;
        stage.setTitle("S.A.C.O. — AI SQL Co-pilot");

        // Initialize shared Robot
        try {
            robot = new Robot();
            robot.setAutoDelay(20);
        } catch (Exception e) {
            log.error("Failed to create Robot (Accessibility permission needed on macOS): {}", e.getMessage());
        }

        loadAppIcon(stage);

        // ── Build UI ──────────────────────────────────────────
        VBox chatPanel = buildChatPanel();
        VBox codePanel = buildCodePanel();

        SplitPane splitPane = new SplitPane(chatPanel, codePanel);
        splitPane.setDividerPositions(0.42);
        SplitPane.setResizableWithParent(chatPanel, false);

        // Title bar
        HBox titleBar = buildTitleBar();

        // Status bar
        HBox statusBar = buildStatusBar();

        BorderPane root = new BorderPane();
        root.setTop(titleBar);
        root.setCenter(splitPane);
        root.setBottom(statusBar);

        Scene scene = new Scene(root, config.getWindowWidth(), config.getWindowHeight());

        // Load CSS
        String css = getClass().getResource("/styles/saco-dark.css") != null
                ? getClass().getResource("/styles/saco-dark.css").toExternalForm()
                : null;
        if (css != null) {
            scene.getStylesheets().add(css);
        }

        registerKeyboardShortcuts(scene);

        stage.setScene(scene);
        stage.setMinWidth(800);
        stage.setMinHeight(500);
        stage.setAlwaysOnTop(true);
        stage.setOpacity(config.getOpacity());
        stage.show();

        // Save window size on close
        stage.setOnCloseRequest(e -> {
            config.setWindowWidth((int) stage.getWidth());
            config.setWindowHeight((int) stage.getHeight());
            config.save();
            executor.shutdownNow();
        });

        // ── Startup Orchestration (silent — no chat spam) ─────
        // Progress is logged + shown in status bar, NOT in the chat panel.
        StartupOrchestrator orchestrator = new StartupOrchestrator(config, msg ->
                Platform.runLater(() -> statusLabel.setText(msg)));
        executor.submit(() -> {
            boolean success = orchestrator.orchestrate();

            Platform.runLater(() -> {
                positionSacoBottomRight(stage);
                if (success) {
                    runHealthCheck();
                }
            });
        });
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  UI CONSTRUCTION
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private HBox buildTitleBar() {
        Label title = new Label("🤖  S.A.C.O. — AI SQL Co-pilot");
        title.getStyleClass().add("title-label");
        HBox.setHgrow(title, Priority.ALWAYS);

        Button settingsBtn = new Button("⚙");
        settingsBtn.getStyleClass().add("btn-settings");
        settingsBtn.setTooltip(new Tooltip("Settings"));
        settingsBtn.setOnAction(e -> openSettings());

        HBox bar = new HBox(title, settingsBtn);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().add("title-bar");
        return bar;
    }

    private HBox buildStatusBar() {
        statusLabel = new Label("● Checking n8n connection…");
        statusLabel.getStyleClass().addAll("status-label");

        Label sessionLabel = new Label("Session: " + config.getSessionId().substring(0, 8) + "…");
        sessionLabel.getStyleClass().add("status-label");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox bar = new HBox(statusLabel, spacer, sessionLabel);
        bar.getStyleClass().add("status-bar");
        return bar;
    }

    private VBox buildChatPanel() {
        // Label
        Label header = new Label("CHAT");
        header.getStyleClass().add("section-header");

        // Messages container
        chatMessages = new VBox();
        chatMessages.getStyleClass().add("chat-messages");

        chatScroll = new ScrollPane(chatMessages);
        chatScroll.setFitToWidth(true);
        chatScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        chatScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        VBox.setVgrow(chatScroll, Priority.ALWAYS);

        // Auto-scroll when new messages are added
        chatMessages.heightProperty().addListener((obs, old, val) ->
                chatScroll.setVvalue(1.0));

        // Input
        inputField = new TextField();
        inputField.setPromptText("Ask S.A.C.O. to review or draft a query… (Ctrl+Enter)");
        HBox.setHgrow(inputField, Priority.ALWAYS);
        inputField.setOnAction(e -> sendMessage(inputField.getText(), false));

        sendButton = new Button("Send");
        sendButton.getStyleClass().add("btn-send");
        sendButton.setOnAction(e -> sendMessage(inputField.getText(), false));

        HBox inputBox = new HBox(8, inputField, sendButton);
        inputBox.getStyleClass().add("input-container");
        inputBox.setAlignment(Pos.CENTER);

        VBox panel = new VBox(6, header, chatScroll, inputBox);
        panel.getStyleClass().add("chat-panel");
        return panel;
    }

    private VBox buildCodePanel() {
        Label header = new Label("CODE EDITOR");
        header.getStyleClass().add("section-header");

        // Action buttons
        scanButton = new Button("🔍  Scan " + ideName);
        scanButton.getStyleClass().add("btn-scan");
        scanButton.setMaxWidth(Double.MAX_VALUE);
        scanButton.setTooltip(new Tooltip("Pull code from " + ideName + " (⌘⇧S)"));
        scanButton.setOnAction(e -> scanIdeClipboard());

        grabButton = new Button("⬅️  Grab AI Code");
        grabButton.getStyleClass().add("btn-grab");
        grabButton.setMaxWidth(Double.MAX_VALUE);
        grabButton.setTooltip(new Tooltip("Extract last SQL block from chat (⌘⇧G)"));
        grabButton.setOnAction(e -> extractCodeToEditor());

        applyButton = new Button("⬇️  Apply to " + ideName);
        applyButton.getStyleClass().add("btn-apply");
        applyButton.setMaxWidth(Double.MAX_VALUE);
        applyButton.setTooltip(new Tooltip("Inject code into " + ideName + " (⌘⇧A)"));
        applyButton.setOnAction(e -> applyToIde());

        VBox buttonBar = new VBox(6, scanButton, grabButton, applyButton);
        buttonBar.getStyleClass().add("button-bar");

        // Code editor
        String monoFont = OsAutomation.monoFont();
        codeEditor = new TextArea();
        codeEditor.setPromptText("-- Click 'Scan " + ideName + "' to pull your code here,\n-- or type SQL manually…");
        codeEditor.setStyle("-fx-font-family: " + monoFont + ";");
        codeEditor.getStyleClass().add("code-editor");
        VBox.setVgrow(codeEditor, Priority.ALWAYS);

        // Run button
        runCodeButton = new Button("▶  Run Code Against Database");
        runCodeButton.getStyleClass().add("btn-run");
        runCodeButton.setMaxWidth(Double.MAX_VALUE);
        runCodeButton.setTooltip(new Tooltip("Execute query via n8n MySQL tool (⌘⇧R)"));
        runCodeButton.setOnAction(e -> sendMessage(codeEditor.getText(), true));

        VBox panel = new VBox(8, header, buttonBar, codeEditor, runCodeButton);
        panel.getStyleClass().add("code-panel");
        return panel;
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  KEYBOARD SHORTCUTS
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private void registerKeyboardShortcuts(Scene scene) {
        KeyCombination.Modifier mod = OsAutomation.isMac()
                ? KeyCombination.META_DOWN : KeyCombination.CONTROL_DOWN;

        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.S, mod, KeyCombination.SHIFT_DOWN),
                this::scanIdeClipboard);

        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.G, mod, KeyCombination.SHIFT_DOWN),
                this::extractCodeToEditor);

        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.A, mod, KeyCombination.SHIFT_DOWN),
                this::applyToIde);

        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.R, mod, KeyCombination.SHIFT_DOWN),
                () -> sendMessage(codeEditor.getText(), true));

        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.COMMA, mod),
                this::openSettings);

        log.info("Keyboard shortcuts registered");
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  CHAT MESSAGE DISPLAY
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /** Adds a styled user message card to the chat. */
    private void addUserMessage(String text) {
        Platform.runLater(() -> {
            VBox card = createMessageCard("You", text, "message-card-user", "message-role-user");
            chatMessages.getChildren().add(card);
        });
    }

    /** Adds a styled AI response card to the chat. */
    private void addAiMessage(String text) {
        Platform.runLater(() -> {
            VBox card = createMessageCard("S.A.C.O.", text, "message-card", "message-role-ai");
            chatMessages.getChildren().add(card);
        });
    }

    /** Adds a smaller system notification to the chat. */
    private void addSystemMessage(String text) {
        Platform.runLater(() -> {
            Label role = new Label("⚙ SYSTEM");
            role.getStyleClass().add("message-role-system");

            Label content = new Label(text);
            content.getStyleClass().add("message-content-system");
            content.setWrapText(true);

            VBox card = new VBox(2, role, content);
            card.getStyleClass().add("message-card-system");
            chatMessages.getChildren().add(card);
        });
    }

    /** Adds a temporary "processing" indicator; returns the node so it can be removed. */
    private CompletableFuture<Node> addProcessingIndicator() {
        CompletableFuture<Node> future = new CompletableFuture<>();
        Platform.runLater(() -> {
            Label indicator = new Label("⏳ S.A.C.O. is thinking…");
            indicator.getStyleClass().addAll("message-content-system");
            indicator.setStyle("-fx-text-fill: #4fc3f7; -fx-font-style: italic;");

            VBox card = new VBox(indicator);
            card.getStyleClass().add("message-card-system");
            chatMessages.getChildren().add(card);
            future.complete(card);
        });
        return future;
    }

    /** Creates a message card with role label and content. */
    private VBox createMessageCard(String role, String content, String cardStyle, String roleStyle) {
        Label roleLabel = new Label(role);
        roleLabel.getStyleClass().add(roleStyle);

        // Use TextFlow for word-wrapping multi-line content
        Text contentText = new Text(content);
        contentText.getStyleClass().add("message-content");
        contentText.setStyle("-fx-fill: #e0e0e0;");

        TextFlow flow = new TextFlow(contentText);
        flow.setMaxWidth(Double.MAX_VALUE);
        flow.setLineSpacing(2);

        VBox card = new VBox(4, roleLabel, flow);
        card.getStyleClass().add(cardStyle);
        card.setMaxWidth(Double.MAX_VALUE);
        return card;
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  SCAN IDE — Pull code from MySQL Workbench
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private void scanIdeClipboard() {
        if (robot == null) {
            addSystemMessage("❌ Robot not available. Check Accessibility permissions.");
            return;
        }

        addSystemMessage("Scanning " + ideName + "…");
        setAllButtonsDisabled(true);

        executor.submit(() -> {
            try {
                // Capture current clipboard to detect change
                String previousClipboard = OsAutomation.getClipboard();

                // 1. Switch to the target IDE
                OsAutomation.switchToIde(robot);
                Thread.sleep(400);

                // 2. Select All
                OsAutomation.modifierCombo(robot, KeyEvent.VK_A);
                Thread.sleep(100);

                // 3. Copy
                OsAutomation.modifierCombo(robot, KeyEvent.VK_C);

                // 4. Poll clipboard for new content (replaces magic sleep)
                String copiedText = OsAutomation.pollClipboard(previousClipboard, 2000);

                // 5. Update UI
                Platform.runLater(() -> {
                    codeEditor.setText(copiedText);
                    addSystemMessage("✅ Code pulled from " + ideName + " (" + copiedText.length() + " chars)");
                    setAllButtonsDisabled(false);
                });

            } catch (Exception ex) {
                log.error("Scan failed: {}", ex.getMessage(), ex);
                Platform.runLater(() -> {
                    addSystemMessage("❌ Scan failed: " + ex.getMessage());
                    setAllButtonsDisabled(false);
                });
            }
        });
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  APPLY TO IDE — Inject code back into MySQL Workbench
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private void applyToIde() {
        String code = codeEditor.getText();
        if (code == null || code.isBlank()) {
            addSystemMessage("⚠️ Nothing to apply — editor is empty.");
            return;
        }
        if (robot == null) {
            addSystemMessage("❌ Robot not available. Check Accessibility permissions.");
            return;
        }

        addSystemMessage("Injecting code into " + ideName + "…");

        executor.submit(() -> {
            try {
                // 1. Put code onto clipboard
                OsAutomation.setClipboard(code);

                // 2. Switch to IDE
                OsAutomation.switchToIde(robot);
                Thread.sleep(500);

                // 3. Select All
                OsAutomation.modifierCombo(robot, KeyEvent.VK_A);
                Thread.sleep(100);

                // 4. Paste (overwrites)
                OsAutomation.modifierCombo(robot, KeyEvent.VK_V);

                Platform.runLater(() ->
                        addSystemMessage("✅ Code applied to " + ideName + "."));

            } catch (Exception ex) {
                log.error("Apply failed: {}", ex.getMessage(), ex);
                Platform.runLater(() ->
                        addSystemMessage("❌ Apply failed: " + ex.getMessage()));
            }
        });
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  GRAB AI CODE — Extract SQL from chat history
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private void extractCodeToEditor() {
        // Collect all message text from chat cards
        StringBuilder allText = new StringBuilder();
        for (Node node : chatMessages.getChildren()) {
            if (node instanceof VBox card) {
                for (Node child : card.getChildren()) {
                    if (child instanceof TextFlow flow) {
                        for (Node textNode : flow.getChildren()) {
                            if (textNode instanceof Text t) {
                                allText.append(t.getText());
                            }
                        }
                    } else if (child instanceof Label lbl) {
                        allText.append(lbl.getText());
                    }
                    allText.append("\n");
                }
                allText.append("\n");
            }
        }

        Optional<String> extracted = CodeParser.extractLastSqlBlock(allText.toString());

        if (extracted.isPresent()) {
            codeEditor.setText(extracted.get());
            addSystemMessage("✅ AI code moved to editor (" + extracted.get().length() + " chars).");
        } else {
            addSystemMessage("⚠️ No SQL code blocks (```sql) found in chat.");
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  SEND MESSAGE — POST to n8n AI Agent
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private void sendMessage(String textToSend, boolean isExecution) {
        String rawText = (textToSend != null) ? textToSend.trim() : "";
        if (rawText.isEmpty()) return;

        if (isExecution) {
            addSystemMessage("⚡ Requesting execution of code…");
        } else {
            addUserMessage(rawText);
            inputField.clear();
        }

        setAllButtonsDisabled(true);

        // Add processing indicator (safe removal via reference)
        CompletableFuture<Node> indicatorFuture = addProcessingIndicator();

        String prompt = isExecution ? "SYSTEM_COMMAND_EXECUTE:\n" + rawText : rawText;
        String currentCode = codeEditor.getText();

        n8nClient.sendMessage(prompt, currentCode)
                .thenAccept(response -> {
                    // Remove processing indicator
                    indicatorFuture.thenAccept(indicator ->
                            Platform.runLater(() -> chatMessages.getChildren().remove(indicator)));

                    Platform.runLater(() -> {
                        if (response.isSuccess()) {
                            addAiMessage(response.body());
                        } else {
                            addSystemMessage("❌ Error: Received HTTP " + response.statusCode() + " from n8n.");
                        }
                        setAllButtonsDisabled(false);
                        inputField.requestFocus();
                    });
                })
                .exceptionally(ex -> {
                    indicatorFuture.thenAccept(indicator ->
                            Platform.runLater(() -> chatMessages.getChildren().remove(indicator)));

                    log.error("Network error: {}", ex.getMessage(), ex);
                    Platform.runLater(() -> {
                        addSystemMessage("❌ Network error: " + ex.getMessage());
                        setAllButtonsDisabled(false);
                    });
                    return null;
                });
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  SETTINGS
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private void openSettings() {
        boolean saved = SettingsDialog.show(primaryStage, config);
        if (saved) {
            primaryStage.setOpacity(config.getOpacity());
            addSystemMessage("✅ Settings saved.");
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  HEALTH CHECK
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private void runHealthCheck() {
        n8nClient.healthCheck().thenAccept(healthy ->
                Platform.runLater(() -> {
                    if (healthy) {
                        statusLabel.setText("● Connected to n8n");
                        statusLabel.getStyleClass().removeAll("status-disconnected");
                        statusLabel.getStyleClass().add("status-connected");
                    } else {
                        statusLabel.setText("● n8n unreachable");
                        statusLabel.getStyleClass().removeAll("status-connected");
                        statusLabel.getStyleClass().add("status-disconnected");
                    }
                })
        );
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  HELPERS
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /** Enables or disables all action buttons. */
    private void setAllButtonsDisabled(boolean disabled) {
        scanButton.setDisable(disabled);
        grabButton.setDisable(disabled);
        applyButton.setDisable(disabled);
        sendButton.setDisable(disabled);
        runCodeButton.setDisable(disabled);
        inputField.setDisable(disabled);
    }

    /** Loads the app icon from resources. */
    private void loadAppIcon(Stage stage) {
        try {
            var iconStream = getClass().getResourceAsStream("/saco-icon.png");
            if (iconStream != null) {
                stage.getIcons().add(new Image(iconStream));
            }
        } catch (Exception e) {
            log.warn("Could not load icon: {}", e.getMessage());
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  WINDOW POSITIONING
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * Positions the S.A.C.O. window to the bottom-right quadrant of the screen,
     * overlaying on top of MySQL Workbench.
     */
    private void positionSacoBottomRight(Stage stage) {
        try {
            Rectangle2D screen = Screen.getPrimary().getVisualBounds();
            double[] bounds = StartupOrchestrator.calculateBottomRightBounds(
                    screen.getWidth(), screen.getHeight());

            stage.setX(bounds[0]);
            stage.setY(bounds[1]);
            stage.setWidth(bounds[2]);
            stage.setHeight(bounds[3]);

            log.info("SACO positioned: x={}, y={}, w={}, h={}",
                    bounds[0], bounds[1], bounds[2], bounds[3]);
            addSystemMessage("📐 S.A.C.O. positioned to bottom-right quadrant.");
        } catch (Exception e) {
            log.warn("Failed to position window: {}", e.getMessage());
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
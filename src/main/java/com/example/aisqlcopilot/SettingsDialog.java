package com.example.aisqlcopilot;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.stage.Window;

/**
 * Settings dialog for configuring S.A.C.O. at runtime.
 *
 * Exposes: webhook URL, auth token, container name, font size, opacity.
 * All changes are persisted to {@code ~/.saco/config.properties}.
 */
public class SettingsDialog {

    private SettingsDialog() { /* utility */ }

    /**
     * Shows the settings dialog and applies any changes to the config.
     *
     * @param owner  the parent window
     * @param config the live config object
     * @return true if the user clicked Save
     */
    public static boolean show(Window owner, SacoConfig config) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("S.A.C.O. Settings");
        dialog.setHeaderText("Configure your AI Co-pilot");
        dialog.initOwner(owner);

        // ── Form Fields ──────────────────────────────────────
        TextField webhookField = new TextField(config.getWebhookUrl());
        webhookField.setPrefWidth(400);

        PasswordField tokenField = new PasswordField();
        tokenField.setText(config.getAuthToken());
        tokenField.setPromptText("Optional — leave blank for no auth");

        TextField containerField = new TextField(config.getN8nContainerName());
        containerField.setPromptText("Docker container name for n8n");

        Spinner<Integer> fontSpinner = new Spinner<>(10, 24, config.getFontSize());
        fontSpinner.setEditable(true);

        Slider opacitySlider = new Slider(0.5, 1.0, config.getOpacity());
        opacitySlider.setShowTickLabels(true);
        opacitySlider.setShowTickMarks(true);
        opacitySlider.setMajorTickUnit(0.1);

        // ── Layout ───────────────────────────────────────────
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(12);
        grid.setPadding(new Insets(20));

        grid.add(new Label("n8n Webhook URL:"), 0, 0);
        grid.add(webhookField, 1, 0);

        grid.add(new Label("Auth Token (X-SACO-Token):"), 0, 1);
        grid.add(tokenField, 1, 1);

        grid.add(new Label("n8n Container Name:"), 0, 2);
        grid.add(containerField, 1, 2);

        grid.add(new Label("Font Size:"), 0, 3);
        grid.add(fontSpinner, 1, 3);

        grid.add(new Label("Window Opacity:"), 0, 4);
        grid.add(opacitySlider, 1, 4);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        // Style the dialog
        dialog.getDialogPane().setStyle(
                "-fx-background-color: #1e1e2e;"
        );

        // ── Handle result ────────────────────────────────────
        var result = dialog.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            config.setWebhookUrl(webhookField.getText().trim());
            config.setAuthToken(tokenField.getText().trim());
            config.setN8nContainerName(containerField.getText().trim());
            config.setFontSize(fontSpinner.getValue());
            config.setOpacity(opacitySlider.getValue());
            config.save();
            return true;
        }
        return false;
    }
}

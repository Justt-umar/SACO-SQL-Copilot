module com.example.aisqlcopilot {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.net.http;
    requires java.desktop;


    opens com.example.aisqlcopilot to javafx.fxml;
    exports com.example.aisqlcopilot;
}
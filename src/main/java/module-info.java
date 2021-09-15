module com.example.logsearcher {
    requires javafx.controls;
    requires javafx.fxml;


    opens com.example.logsearcher to javafx.fxml;
    exports com.example.logsearcher;
}
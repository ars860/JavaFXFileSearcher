module com.example.logsearcher {
    requires javafx.controls;
    requires javafx.fxml;
    requires org.fxmisc.richtext;
//    requires richtextfx;


    opens com.example.logsearcher to javafx.fxml;
    exports com.example.logsearcher;
}
package com.codelog.schyfts;

import com.codelog.schyfts.api.Doctor;
import com.codelog.schyfts.api.User;
import com.codelog.schyfts.api.UserContext;
import com.codelog.schyfts.util.Request;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

public class Doctors implements Initializable {

    @FXML
    private TableView<Doctor> tblDoctors;
    @FXML
    private TextField txtShortcode;
    @FXML
    private TextField txtCellphone;
    @FXML
    private TextField txtName;
    @FXML
    private TextField txtSurname;
    @FXML
    private Button btnSubmit;

    private static List<Doctor> doctors;
    private boolean submitAction; // true: Add new doctor, false: Edit doctor

    @Override
    public void initialize(URL location, ResourceBundle resources) {

        doctors = new ArrayList<>();
        submitAction = false;
        refreshDoctors();

    }

    public void refreshDoctors() {
        txtShortcode.clear();
        txtCellphone.clear();
        txtName.clear();
        txtSurname.clear();

        TableColumn<Doctor, Integer> clmId = new TableColumn<>("ID");
        clmId.setCellValueFactory(new PropertyValueFactory<>("id"));

        TableColumn<Doctor, Integer> clmShortcode = new TableColumn<>("Shortcode");
        clmShortcode.setCellValueFactory(new PropertyValueFactory<>("shortcode"));

        TableColumn<Doctor, Integer> clmCellphone = new TableColumn<>("Cellphone");
        clmCellphone.setCellValueFactory(new PropertyValueFactory<>("cellphone"));

        TableColumn<Doctor, Integer> clmName = new TableColumn<>("Initials");
        clmName.setCellValueFactory(new PropertyValueFactory<>("name"));

        TableColumn<Doctor, Integer> clmSurname = new TableColumn<>("Surname");
        clmSurname.setCellValueFactory(new PropertyValueFactory<>("surname"));

        tblDoctors.getColumns().clear();
        tblDoctors.getColumns().add(clmId);
        tblDoctors.getColumns().add(clmShortcode);
        tblDoctors.getColumns().add(clmCellphone);
        tblDoctors.getColumns().add(clmName);
        tblDoctors.getColumns().add(clmSurname);

        doctors.clear();
        doctors = Doctor.getAllDoctors();
        tblDoctors.getItems().clear();
        tblDoctors.getItems().addAll(doctors);

        tblDoctors.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            if (newSelection != null) {
                txtShortcode.setText(newSelection.getShortcode());
                txtCellphone.setText(newSelection.getCellphone());
                txtName.setText(newSelection.getName());
                txtSurname.setText(newSelection.getSurname());
            }
        });
    }

    public void btnAddDoctorClick(ActionEvent actionEvent) {

        submitAction = true;
        tblDoctors.getSelectionModel().clearSelection();

        txtShortcode.clear();
        txtCellphone.clear();
        txtName.clear();
        txtSurname.clear();

        btnSubmit.setText("Add");

    }

    public void btnSubmitClick(ActionEvent actionEvent) {

        var body = new JSONObject();
        if (!submitAction) {
            var edit = new JSONObject();

            edit.put("shortcode", txtShortcode.getText());
            edit.put("cellphone", txtCellphone.getText());
            edit.put("name", txtName.getText());
            edit.put("surname", txtSurname.getText());
            body.put("token", UserContext.getInstance().getCurrentUser().getToken());
            body.put("edit", edit);
            body.put("id", tblDoctors.getSelectionModel().getSelectedItem().getId());

            Alert alert;

            try {
                Request req = new Request(Reference.API_URL + "editDoctor");
                req.setBody(body);
                req.sendRequest();
                if (!req.getResponse().get("status").equals("ok")) {
                    System.err.println("HTTP request error.");
                } else {
                    System.out.println("Success");
                }
                System.out.print(req.getResponse().toString(4));
                alert = new Alert(Alert.AlertType.INFORMATION, "Record updated");
            } catch (IOException e) {
                e.printStackTrace(System.err);
                alert = new Alert(Alert.AlertType.ERROR, String.format("Error: [%s]", e.getLocalizedMessage()));
            }

            alert.setTitle("Message");
            alert.show();

            txtShortcode.clear();
            txtCellphone.clear();
            txtName.clear();
            txtSurname.clear();
            refreshDoctors();
        } else {
            body.put("token", UserContext.getInstance().getCurrentUser().getToken());
            body.put("shortcode", txtShortcode.getText());
            body.put("cellphone", txtCellphone.getText());
            body.put("name", txtName.getText());
            body.put("surname", txtSurname.getText());

            try {
                Request req = new Request(Reference.API_URL + "addDoctor", body);
                req.sendRequest();
                if (!req.getResponse().get("status").equals("ok")) {
                    throw new IOException(req.getResponse().getString("message"));
                }
                Alert alert = new Alert(Alert.AlertType.INFORMATION, "Success");
                alert.setTitle("Done");
                alert.show();
                btnSubmit.setText("Submit");
                refreshDoctors();
            } catch (IOException e) {
                e.printStackTrace(System.err);
                Alert alert = new Alert(Alert.AlertType.ERROR, String.format("Error: %s", e.getLocalizedMessage()));
                alert.setTitle("Error");
                alert.show();
            } finally {
                submitAction = false;
                refreshDoctors();
            }
        } // endif
    } // btnSubmitClick
}

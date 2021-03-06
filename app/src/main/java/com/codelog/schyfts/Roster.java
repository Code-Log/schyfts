package com.codelog.schyfts;

import com.codelog.clogg.Logger;
import com.codelog.schyfts.api.APIException;
import com.codelog.schyfts.api.APIRequest;
import com.codelog.schyfts.api.Doctor;
import com.codelog.schyfts.api.LeaveData;
import com.codelog.schyfts.google.StorageContext;
import com.codelog.schyfts.util.AlertFactory;
import com.codelog.schyfts.util.LocalDateFormatter;
import com.codelog.schyfts.util.PrintUtils;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Bucket;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.MapValueFactory;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Pair;
import javafx.util.converter.DefaultStringConverter;
import org.json.JSONObject;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.*;

@SuppressWarnings({"rawtypes", "unchecked"})
public class Roster implements Initializable {

    private static final int MODULES = 16;
    private static final int LISTS = 14;

    private String[][] matrix;
    private static List<String> lists;
    public static Stage primaryStage;
    private List<Doctor> doctors;
    private Map<Integer, List<Integer>> moduleMap;
    private Map<Integer, String> doctorNames;
    private List<String> keys;
    private List<LeaveData> doctorLeave;
    private Bucket storageBucket;
    private Map<Integer, Integer> sharedModules;
    private int scheduleOffset;
    private static Optional<Pair<LocalDate, LocalDate>> dateRange;
    private Map<Integer, List> scheduleState;

    @FXML
    private ImageView imgLogo;
    @FXML
    private TableView<Map> tblRoster;
    @FXML
    private TableView<Map> tblSchedule;
    @FXML
    private Tab tabSchedule;
    @FXML
    private TextArea txtDateRange;
    @FXML
    private GridPane grdPrint;

    private void loadRoster() {
        var matrixBlob = storageBucket.get("matrix.csv");
        if (matrixBlob != null) {
            try {
                if (Files.exists(Path.of("matrix.csv")))
                    Files.delete(Path.of("matrix.csv"));
                matrixBlob.downloadTo(Path.of("matrix.csv"));
            } catch (IOException e) {
                Logger.getInstance().exception(e);
            }
        }

        if (!Files.exists(Path.of("matrix.csv"))) {
            return;
        }

        List<String> lines = new ArrayList<>();
        try {
            BufferedReader reader = new BufferedReader(new FileReader("matrix.csv"));
            String line;
            while ((line = reader.readLine()) != null)
                lines.add(line);
        } catch (IOException e) {
            Logger.getInstance().exception(e);
        }

        for (int i = 0; i < lines.size(); i++) {

            String[] split = lines.get(i).split(",");
            for (int j = 0; j < split.length; j++) {
                if (split[j].equals("\"\"") || split[j].equals("null"))
                    matrix[j][i] = "";
                else
                    matrix[j][i] = split[j];
            }

        }
    }

    private void refresh() {

        doctorNames = new HashMap<>();

        var doctors = Doctor.getAllDoctors();
        if (doctors == null) {
            Logger.getInstance().error("Couldn't retrieve doctors");
            return;
        }

        for (var d : doctors)
            doctorNames.put(d.getId(), String.format("%s %s", d.getSurname(), d.getName()));

        doctorLeave = LeaveData.getAllLeave();

        try {
            APIRequest getSettingRequest = new APIRequest("getSetting", true, "key");
            var response = getSettingRequest.send("scheduleOffset");
            Logger.getInstance().debug(String.format(
                    "Got setting scheduleOffset: %s",
                    response.getJSONObject("result").getString("value"))
            );
            Logger.getInstance().debug(response.toString(4));
            scheduleOffset = Integer.parseInt(response.getJSONObject("result").getString("value"));
        } catch (IOException | IllegalArgumentException | APIException e) {
            Logger.getInstance().exception(e);
        }

    }

    private void updateItems() {
        tblRoster.getItems().clear();
        ObservableList<Map<String, Object>> items = FXCollections.observableArrayList();
        for (int j = 0; j < LISTS; j++) {
            Map<String, Object> item = new HashMap<>();
            item.put("list", lists.get(j));
            for (int i = 0; i < MODULES; i++) {
                item.put(String.valueOf(i + 1), matrix[i][j]);
            }
            items.add(item);
        }
        tblRoster.getItems().addAll(items);
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        sharedModules = getSharedModules();
        var temp = Doctor.getAllDoctors();
        doctors = new ArrayList<>();

        APIRequest req = new APIRequest("getSetting", true, "key");
        assert temp != null;
        List<Integer> order = new ArrayList<>();
        try {
            var res = req.send("doctorOrder");
            var stringResult = res.getJSONObject("result").getString("value").split(",");
            for (var s : stringResult)
                order.add(Integer.parseInt(s));
        } catch (IOException | APIException e) {
            Logger.getInstance().exception(e);
        }

        for (var item : order) {
            for (Doctor d : temp) {
                if (d.getId() == item) {
                    doctors.add(d);
                }
            }
        }

        scheduleOffset = 0;

        var stream = getClass().getClassLoader().getResourceAsStream("google/service-account.json");
        if (stream != null) {

            var storage = StorageContext.getInstance().getStorage();
            storageBucket = storage.get("nelanest-roster");

        } else {
            Logger.getInstance().error("Couldn't load storage credentials");
        }

        tabSchedule.setDisable(true);

        tblRoster.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        tblRoster.setEditable(true);
        matrix = new String[MODULES][LISTS];
        loadRoster();

        lists = new ArrayList<>();

        lists.add("Mon AM");
        lists.add("Mon PM");
        lists.add("Tue AM");
        lists.add("Tue PM");
        lists.add("Wed AM");
        lists.add("Wed PM");
        lists.add("Thu AM");
        lists.add("Thu PM");
        lists.add("Fri AM");
        lists.add("Fri PM");
        lists.add("Sat AM");
        lists.add("Sat PM");
        lists.add("Sun AM");
        lists.add("Sun PM");

        TableColumn<Map, String> clmLabel = new TableColumn<>("List");
        clmLabel.setCellValueFactory(new MapValueFactory<>("list"));
        clmLabel.setSortable(false);
        tblRoster.getColumns().add(clmLabel);
        for (int i = 1; i < MODULES + 1; i++) {

            TableColumn<Map, String> clm = new TableColumn<>(String.valueOf(i));
            clm.setEditable(true);
            clm.setCellValueFactory(new MapValueFactory<>(String.valueOf(i)));
            clm.setCellFactory(TextFieldTableCell.forTableColumn());

            clm.setSortable(false);
            clm.setOnEditCommit(event -> {
                    matrix[tblRoster.getColumns().indexOf(clm) - 1][tblRoster.getSelectionModel().getSelectedIndex()] = event.getNewValue();
                    updateItems();
            });

            tblRoster.getColumns().add(clm);
        }

        updateItems();

        Thread thread = new Thread(this::refresh);
        thread.start();

        scheduleState = new HashMap<>();

    }

    public void mnuSave(ActionEvent actionEvent) {
        List<String> lines = new ArrayList<>();
        var flipMat = new String[LISTS][MODULES];

        for (int i = 0; i < MODULES; i++) {
            for (int j = 0; j < LISTS; j++) {
                flipMat[j][i] = matrix[i][j];
            }
        }

        for (int i = 0; i < LISTS; i++) {
            StringBuilder builder = new StringBuilder();
            for (int j = 0; j < MODULES - 1; j++) {
                builder.append(flipMat[i][j]).append(',');
            }
            builder.append(flipMat[i][MODULES - 1]);
            lines.add(builder.toString());
        }

        try {
            if (Files.exists(Path.of("matrix.csv").toAbsolutePath()))
                Files.delete(Path.of("matrix.csv").toAbsolutePath());

            StringBuilder builder = new StringBuilder();
            BufferedWriter writer = new BufferedWriter(new FileWriter("matrix.csv"));
            for (String line : lines) {
                writer.write(line);
                builder.append(line).append('\n');
                writer.flush();
                writer.newLine();
            }
            writer.close();

            storageBucket.create("matrix.csv", getClass().getResourceAsStream("matrix.csv"));
            Blob matrixBlob = storageBucket.get("matrix.csv");
            var writeChannel = matrixBlob.writer();

            String contents = builder.toString();
            byte[] arr = contents.getBytes();
            ByteBuffer buff = ByteBuffer.allocate(arr.length);
            buff.put(arr);
            buff.rewind();
            writeChannel.write(buff);
            writeChannel.close();

            Alert alert = new Alert(Alert.AlertType.INFORMATION, "Roster saved!");
            alert.setTitle("Saved");
            alert.show();

        } catch (IOException e) {
            Logger.getInstance().exception(e);
        }

        Logger.getInstance().info("Saved roster to 'matrix.csv'");

    }

    private static Map<Integer, Integer> getSharedModules() {
        Map<Integer, Integer> sharedModules = new HashMap<>();

        try {
            APIRequest req = new APIRequest("getSharedModules", true);
            var response = req.send();

            if (!response.getString("status").equals("ok"))
                throw new IOException(response.getString("message"));

            var results = response.getJSONArray("results");
            for (int i = 0; i < results.length(); i++) {
                for (String s : results.getJSONObject(i).getString("doctors").split(","))
                    sharedModules.put(Integer.parseInt(s), results.getJSONObject(i).getInt("moduleNum"));
            }
        } catch (IOException | APIException e) {
            Logger.getInstance().exception(e);
        }
        return sharedModules;
    }

    private void constructModuleMap() {
        moduleMap = new HashMap<>();
        assert dateRange.isPresent();
        var period = dateRange.get().getKey().toEpochDay() - Reference.GENESIS_TIME.toEpochDay();

        int currentModule = (scheduleOffset + (int)Math.floor((float)period / 7.0f) + 2) % MODULES;
        for (Doctor d : doctors) {
            if (!sharedModules.containsKey(d.getId())) {
                moduleMap.put(currentModule + 1, List.of(d.getId()));
                currentModule++;
                currentModule %= MODULES;
            }
        }

        var copy = new HashMap<>(sharedModules);
        for (var d : sharedModules.keySet()) {
            if (!moduleMap.containsKey(currentModule + 1)) {
                moduleMap.put(currentModule + 1, new ArrayList<>());
            }
            moduleMap.get(currentModule + 1).add(d);
            copy.remove(d);
            if (!copy.containsValue(sharedModules.get(d))) {
                currentModule++;
                currentModule %= MODULES;
            }
        }
    }

    public void mnuGenerateScheduleClick(ActionEvent actionEvent) {
        tblSchedule.getStylesheets().add("styles.css");
        Dialog<Pair<LocalDate, LocalDate>> dialog = new Dialog<>();
        dialog.setTitle("Schedule options");
        dialog.setHeaderText("Please select a start and end date");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        GridPane pane = new GridPane();
        pane.setHgap(10);
        pane.setVgap(10);
        pane.setPadding(new Insets(20, 150, 10, 10));

        DatePicker dpStartDate = new DatePicker();
        DatePicker dpEndDate = new DatePicker();

        pane.add(new Label("Start Date:"), 0, 0);
        pane.add(dpStartDate, 1, 0);
        pane.add(new Label("End Date:"), 0, 1);
        pane.add(dpEndDate, 1, 1);

        dialog.getDialogPane().setContent(pane);
        dialog.setResultConverter(dialogButton -> {
            if (!dialogButton.getButtonData().isCancelButton()) {
                return new Pair<>(dpStartDate.getValue(), dpEndDate.getValue());
            }
            return null;
        });

        dateRange = dialog.showAndWait();
        generateSchedule();
    }

    private int maxWeeks;
    private JSONObject surgeonLeaveJson;

    private void generateSchedule() {

        var prgBar = new ProgressBar();
        var prgText = new Label();
        var vbox = new VBox(prgBar, prgText);
        vbox.setMinWidth(100);
        vbox.setMinHeight(50);
        Stage prgStage = new Stage();
        Scene prgScene = new Scene(vbox);
        prgStage.setScene(prgScene);
        prgStage.setTitle("Progress");
        prgStage.initModality(Modality.APPLICATION_MODAL);
        prgStage.show();

        imgLogo.setImage(Reference.LOGO);
        if (dateRange.isEmpty())
            return;
        String dr = "Schedule from: %s to %s".formatted(dateRange.get().getKey().toString().split("T")[0],
                dateRange.get().getValue().toString().split("T")[0]);
        txtDateRange.setText(dr);

        maxWeeks = 0;

        tblSchedule.getColumns().clear();
        tblSchedule.getItems().clear();

        keys = new ArrayList<>();
        tabSchedule.setDisable(false);
        tabSchedule.getTabPane().getSelectionModel().select(tabSchedule);
        tblSchedule.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        tblSchedule.getItems().clear();
        tblSchedule.getColumns().clear();
        tblSchedule.setEditable(true);

        prgText.setText("Constructing module map...");
        constructModuleMap();
        prgBar.setProgress(0.15);

        TableColumn<Map, String> clmList = new TableColumn<>("List");
        clmList.setCellValueFactory(new MapValueFactory<>("List"));
        tblSchedule.getColumns().add(clmList);
        keys.add("List");

        prgText.setText("Constructing table columns...");
        for (var module : moduleMap.keySet()) {

            TableColumn<Map, String> clm = new TableColumn<>(String.valueOf(module));

            for (var doctor : moduleMap.get(module)) {
                TableColumn<Map, String> clmDoctor = new TableColumn<>(doctorNames.get(doctor));
                clmDoctor.setCellValueFactory(new MapValueFactory<>(doctorNames.get(doctor)));
                keys.add(doctorNames.get(doctor));
                clmDoctor.setEditable(true);
                clmDoctor.setCellFactory(param -> new TextFieldTableCell<>(new DefaultStringConverter()) {

                    @Override
                    public void updateItem(String item, boolean empty) {
                        super.updateItem(item, empty);
                        if (item == null || empty) {
                            setText("");
                            setStyle("");
                        } else {
                            Text text = new Text(item);
                            text.setStyle("-fx-text-alignment:left;");
                            text.wrappingWidthProperty().bind(getTableColumn().widthProperty());
                            setGraphic(text);

                            if (item.contains("#"))
                                setStyle("-fx-background-color:green;");
                        }
                    }
                });
                clm.getColumns().add(clmDoctor);
            }

            tblSchedule.getColumns().add(clm);
        }

        TableColumn<Map, String> clmStatic = new TableColumn<>("Static");
        TableColumn<Map, String> joubert = new TableColumn<>("Joubert L");
        keys.add("Joubert L");
        joubert.setCellFactory(TextFieldTableCell.forTableColumn());
        joubert.setCellValueFactory(new MapValueFactory<>("static"));
        joubert.setEditable(true);
        clmStatic.getColumns().add(joubert);
        tblSchedule.getColumns().add(clmStatic);

        for (int i = 0; i < 3; i++) {
            TableColumn<Map, String> clmCall = new TableColumn<>("Call " + (i + 1));
            var key = "call" + (i + 1);
            createColumn(clmCall, key);
        }

        for (int i = 0; i < 5; i++) {
            TableColumn<Map, String> clmLoc = new TableColumn<>("Loc " + (i + 1));
            var key = "loc" + (i + 1);
            createColumn(clmLoc, key);
        }

        ObservableList<Map<String, String>> items = FXCollections.observableArrayList();
        for (int i = 0; i < LISTS; i++) {
            var item = new HashMap<String, String>();
            item.put("List", lists.get(i));
            for (int j = 0; j < MODULES; j++) {
                for (var d : moduleMap.get(j + 1))
                    item.put(doctorNames.get(d), matrix[j][i]);
            }
            items.add(item);
        }

        tblSchedule.setEditable(true);
        tblSchedule.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        tblSchedule.getColumns().forEach(clm -> clm.getColumns().forEach(subClm -> {
            Logger.getInstance().debug("Modifying column %s".formatted(subClm.getText()));
            subClm.setEditable(true);
            subClm.setSortable(false);
            subClm.setOnEditCommit(event -> {
                var rowIdx = event.getTablePosition().getRow();
                tblSchedule.getItems().get(rowIdx).replace(subClm.getText(), event.getNewValue());
                tblSchedule.refresh();
                updateScheduleState();
            });
        }));

        tblSchedule.refresh();
        tblSchedule.getItems().addAll(items);

        prgBar.setProgress(0.45);
        prgText.setText("Refreshing surgeon leave...");
        var surgeonLeaveJson = SurgeonLeave.refresh();
        prgBar.setProgress(0.65);

        LocalDate currentStart = dateRange.get().getKey().plusWeeks(scheduleOffset);
        LocalDate currentEnd = currentStart.plusDays(5);
        var day = currentStart.getDayOfMonth();

        int idx = 0;
        for (var j = 0; j < lists.size(); j++) {
            if (lists.get(j).contains("\n"))
                lists.set(j, lists.get(j).substring(lists.get(j).indexOf('\n') + 1));
            lists.set(j, currentStart.plusDays(idx).getDayOfMonth() + "\n" + lists.get(j));
            idx += j % 2;
        }

        for (var i = 0; i < tblSchedule.getItems().size(); i++) {
            tblSchedule.getItems().get(i).put("List", lists.get(i));
        }

        for (var item : tblSchedule.getItems()) {
            for (int i = 0; i < 3; i++) {
                item.put("call" + (i + 1), "");
            }
            for (int i = 0; i < 5; i++) {
                item.put("loc" + (i + 1), "");
            }
        }

        prgText.setText("Removing surgeons on leave...");
        for (var leave : surgeonLeaveJson) {
            // name, surname, start, end
            String name = leave.getString("name");
            String surname = leave.getString("surname");
            LocalDate start = LocalDate.parse(leave.getString("start").split("T")[0]);
            LocalDate end = LocalDate.parse(leave.getString("end").split("T")[0]);

            List<Pair<Map, String>> itemsToRemove = new ArrayList<>();
            for (var item : tblSchedule.getItems()) {
                for (var key : item.keySet()) {
                    var value = (String)item.get(key);
                    if (value != null) {

                        var fullName = (name.equals(" ")) ? surname : "%s %s".formatted(name, surname);
                        var valueFullName =
                                (value.startsWith("LH") || value.startsWith("DH")) ? value.substring(3) :
                                value.startsWith("DCL") ? value.substring(4) : value;
                        if (value.contains(surname)) {
                            var itemDate = currentStart.plusDays((tblSchedule.getItems().indexOf(item) / 2));
                            if (!itemDate.isAfter(end) && !itemDate.isBefore(start)) {
                                Logger.getInstance().debug("To remove: %s(%s)".formatted(fullName, key));
                                itemsToRemove.add(new Pair<>(item, (String) key));
                            }
                        }

                    } // if
                } // for
            } // for

            for (var item : itemsToRemove)
                if (!((String)item.getKey().get(item.getValue())).contains("OFF"))
                    item.getKey().put(item.getValue(), item.getKey().get(item.getValue()) + " OFF");

            tblSchedule.refresh();
            var rosterPeriod = dateRange.get().getKey().until(dateRange.get().getValue());
            maxWeeks = rosterPeriod.getMonths() * 4 + Math.round(rosterPeriod.getDays() / 7.0f);
        } // for
        prgBar.setProgress(0.85);

        var i = currentStart;

        prgText.setText("Marking doctor leave...");
        while (i.isBefore(currentEnd) || i.isEqual(currentEnd)) {
            var intervalLength = currentStart.until(i).getDays();

            Map<String, String>[] dayitems = new Map[] {
                    tblSchedule.getItems().get(intervalLength * 2),
                    tblSchedule.getItems().get(intervalLength * 2 + 1)
            };

            for (var leave : doctorLeave) {
                if ((i.isAfter(leave.getStartDate()) || i.isEqual(leave.getStartDate()))
                        && (i.isBefore(leave.getEndDate()) || i.isEqual(leave.getEndDate()))) {

                    // OVERLAP!!!
                    // If this code executes, it means that we have a
                    // doctor with leave on the day of month 'i'.

                    var keys = dayitems[0].keySet();
                    for (var key : keys) {

                        if (dayitems[0].get(key) == null || dayitems[1].get(key) == null) {
                            continue;
                        }

                        var value = dayitems[0].get(key);
                        var nextDayValue = dayitems[1].get(key);

                        if ("%s %s".formatted(leave.getDoctorSurname(), leave.getDoctorName()).equals(key)) {

                            if (!value.contains("#"))
                                dayitems[0].put(key, "#" + value);

                            if (!nextDayValue.contains("#"))
                                dayitems[1].put(key, "#" + nextDayValue);

                        } // if
                    } // for
                } // if
            } // for
            i = i.plusDays(1);
        } // while

        // WARNING: This code must execute after all changes
        // to the schedule have been made.
        // This is MISSION CRITICAL
        prgBar.setProgress(0.1);
        prgText.setText("DONE");
        updateScheduleState();

        prgStage.close();

    } // function

    private void updateScheduleState() {
        var itemsCopy = new ArrayList(tblSchedule.getItems());
        if (scheduleState.containsKey(scheduleOffset))
            scheduleState.replace(scheduleOffset, itemsCopy);
        else
            scheduleState.put(scheduleOffset, itemsCopy);
    }

    private void createColumn(TableColumn<Map, String> clm, String key) {
        keys.add(key);
        clm.setCellValueFactory(new MapValueFactory<>(key));
        clm.setCellFactory(TextFieldTableCell.forTableColumn());
        clm.setOnEditCommit(event -> {
            var rowIdx = event.getTablePosition().getRow();
            tblSchedule.getItems().get(rowIdx).replace(key, event.getNewValue());
            tblSchedule.refresh();
            updateScheduleState();
        });
        tblSchedule.getColumns().add(clm);
    }

    @SuppressWarnings("unchecked")
    public void mnuSaveScheduleClick() {

        if (dateRange.isEmpty()) {
            Logger.getInstance().error("Expected dateRange to be populated, but got empty!");
            return;
        }

        FileChooser chooser = new FileChooser();
        FileChooser.ExtensionFilter filter = new FileChooser.ExtensionFilter("CSV files (*.csv)", "*.csv");
        chooser.getExtensionFilters().add(filter);

        VBox vbox = new VBox(new Text("Save"));
        vbox.setAlignment(Pos.CENTER);
        Scene scene = new Scene(vbox);
        Stage stage = new Stage();
        stage.initOwner(Schyfts.currentStage);
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setTitle("Save as");
        stage.setScene(scene);

        File file = chooser.showSaveDialog(stage);
        if (file == null)
            return;
        List<String> lines = new ArrayList<>();

        for (Map<String, String> item : tblSchedule.getItems()) {

            StringBuilder builder = new StringBuilder();
            for (var key : keys) {
                if (key.equals("List")) {
                    builder.append(item.get(key).replace('\n', ' ')).append(',');
                } else {
                    builder.append(item.get(key)).append(',');
                }
            }
            builder.deleteCharAt(builder.length() - 1);
            lines.add(builder.toString());

        }

        StringBuilder builder = new StringBuilder();
        StringBuilder builder2 = new StringBuilder();
        for (var clm : tblSchedule.getColumns()) {
            builder.append(clm.getText()).append(',');
            builder.append(",".repeat(Math.max(0, clm.getColumns().size() - 1)));
            for (int i = 0; i < clm.getColumns().size(); i++) {
                builder2.append(clm.getColumns().get(i).getText()).append(',');
            }
            if (clm.getColumns().size() == 0)
                builder2.append(',');
        }

        builder.deleteCharAt(builder.length() - 1);
        builder2.deleteCharAt(builder.length() - 1);

        // First line (Current date): $dd/MM/yyyy$
        var dateNow = dateRange.get().getKey().plusWeeks(scheduleOffset);
        lines.add(0, "$%s$".formatted(LocalDateFormatter.format(dateNow)));

        // Second line: header
        lines.add(1, builder.toString());

        // Content: Schedule
        lines.add(2, builder2.toString());

        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter(file));
            for (var line : lines) {
                line = line.replace("null", "");
                writer.write(line);
                writer.flush();
                writer.newLine();
            }
            writer.close();
        } catch (IOException e) {
            Logger.getInstance().exception(e);
        }
    }

    private void advanceSchedule() {
        advanceSchedule(false);
    }

    private void advanceSchedule(boolean reverse) {
        if (tabSchedule.isDisabled())
            return;

        if (reverse) {
            if (scheduleOffset <= 0)
                return;
            scheduleOffset--;
        } else {
            if (scheduleOffset >= maxWeeks)
                return;
            scheduleOffset++;
        }

        if (scheduleState.containsKey(scheduleOffset)) {
            tblSchedule.getItems().clear();
            tblSchedule.getItems().addAll(scheduleState.get(scheduleOffset));
        } else {
            generateSchedule();
        }
    }

    public void btnPrevClick() {
        advanceSchedule(true);
    }

    public void btnNextClick() {
        advanceSchedule();
    }

    public void mnuPrintClick() {

    }
}

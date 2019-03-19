package ai.h2o.automl;

import water.Iced;
import water.util.TwoDimTable;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Objects;

public class EventLogEntry<V extends Serializable> extends Iced {

  public enum Level {
    Debug, Info, Warn
  }

  public enum Stage {
    Workflow,
    DataImport,
    FeatureAnalysis,
    FeatureReduction,
    FeatureCreation,
    ModelTraining,
  }

  static TwoDimTable makeTwoDimTable(String tableHeader, int length) {
    String[] rowHeaders = new String[length];
    for (int i = 0; i < length; i++) rowHeaders[i] = "" + i;
    return new TwoDimTable(
            tableHeader,
            "Actions taken and discoveries made by AutoML",
            rowHeaders,
            EventLogEntry.colHeaders,
            EventLogEntry.colTypes,
            EventLogEntry.colFormats,
            "#"
    );
  }

  static String nowStr() {
    return dateTimeFormat.format(new Date());
  }

  static final SimpleDateFormat dateTimeFormat = new SimpleDateFormat("yyyy.MM.dd HH:mm:ss.S"); // uses local timezone
  static final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss.S");  // uses local timezone

  private static final String[] colHeaders = {
          "timestamp",
          "level",
          "stage",
          "name",
          "value",
          "message"
  };

  private static final String[] colTypes= {
          "string",
          "string",
          "string",
          "string",
          "string",
          "string"
  };

  private static final String[] colFormats= {
          "%s",
          "%s",
          "%s",
          "%s",
          "%s",
          "%s"
  };

  private static <E extends Enum<E>> int longest(Class<E> enu) {
    int longest = -1;
    for (E v : enu.getEnumConstants())
      longest = Math.max(longest, v.name().length());
    return longest;
  }

  private final int longestLevel = longest(Level.class); // for formatting
  private final int longestStage = longest(Stage.class); // for formatting

  transient private AutoML _autoML;

  private long _timestamp;
  private Level _level;
  private Stage _stage;
  private String _name;
  private V _value;
  private String _message;

  public long getTimestamp() {
    return _timestamp;
  }

  public AutoML getAutoML() { return _autoML; }

  public Level getLevel() {
    return _level;
  }

  public Stage getStage() {
    return _stage;
  }

  public String getName() {
    return _name;
  }

  public V getValue() {
    return _value;
  }

  public String getMessage() {
    return _message;
  }

  public EventLogEntry(AutoML autoML, Level level, Stage stage, String message) {
    this._timestamp = System.currentTimeMillis();
    this._autoML = autoML;
    this._level = level;
    this._stage = stage;
    this._message = message;
  }

  public void setNamedValue(String name, V value) {
    _name = name;
    _value = value;
  }

  public void addTwoDimTableRow(TwoDimTable table, int row) {
    int col = 0;
    table.set(row, col++, timeFormat.format(new Date(_timestamp)));
    table.set(row, col++, _level);
    table.set(row, col++, _stage);
    table.set(row, col++, _name);
    table.set(row, col++, _value);
    table.set(row, col++, _message);
  }

  @Override
  public String toString() {
    return String.format("%-12s %-"+longestLevel+"s %-"+longestStage+"s %s %s %s",
            timeFormat.format(new Date(_timestamp)),
            _level,
            _stage,
            Objects.toString(_name, ""),
            Objects.toString(_value, ""),
            Objects.toString(_message, "")
    );
  }
}

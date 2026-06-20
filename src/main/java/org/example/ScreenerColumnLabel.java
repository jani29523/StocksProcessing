package org.example;

/**
 * POJO representing one selectable column discovered on the Screener.in
 * column-management page (https://www.screener.in/user/columns/).
 *
 * Each instance maps to one <label> element inside
 * div.flex.flex-column-mobile.flex-gap-32 that wraps an <input type="checkbox">.
 *
 * Fields:
 *   labelClassName  — the class attribute of the <label> element
 *   dataName        — the data-name attribute of the <label> element (Screener's internal column key)
 *   labelText       — visible display text of the label (checkbox text trimmed)
 *   checkboxValue   — value attribute of the nested <input type="checkbox"> (used as unique key)
 *   checked         — live checked state of the checkbox at the time of capture
 */
public class ScreenerColumnLabel {

    private String  labelClassName;
    private String  dataName;
    private String  labelText;
    private String  checkboxValue;
    private boolean checked;

    public ScreenerColumnLabel() {}

    public ScreenerColumnLabel(String labelClassName, String dataName,
                               String labelText, String checkboxValue, boolean checked) {
        this.labelClassName = labelClassName;
        this.dataName       = dataName;
        this.labelText      = labelText;
        this.checkboxValue  = checkboxValue;
        this.checked        = checked;
    }

    public String  getLabelClassName()              { return labelClassName; }
    public void    setLabelClassName(String v)      { this.labelClassName = v; }

    public String  getDataName()                    { return dataName; }
    public void    setDataName(String v)            { this.dataName = v; }

    public String  getLabelText()                   { return labelText; }
    public void    setLabelText(String v)           { this.labelText = v; }

    public String  getCheckboxValue()               { return checkboxValue; }
    public void    setCheckboxValue(String v)       { this.checkboxValue = v; }

    public boolean isChecked()                      { return checked; }
    public void    setChecked(boolean v)            { this.checked = v; }

    @Override
    public String toString() {
        return "ScreenerColumnLabel{" +
                "labelText='" + labelText + '\'' +
                ", dataName='" + dataName + '\'' +
                ", checkboxValue='" + checkboxValue + '\'' +
                ", checked=" + checked +
                ", labelClass='" + labelClassName + '\'' +
                '}';
    }
}
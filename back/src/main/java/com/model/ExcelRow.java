package com.model;

import java.util.Map;

/** Parsed Excel row with source position and original values. */
public final class ExcelRow {

    private final String sheet;

    private final int sourceRow;

    private final Map<String, String> values;

    public ExcelRow(String sheet, int sourceRow, Map<String, String> values) {
        this.sheet = sheet;
        this.sourceRow = sourceRow;
        this.values = values;
    }

    public String at(String key) {
        return values.getOrDefault(key, "");
    }

    public String getSheet() {
        return sheet;
    }

    public int getSourceRow() {
        return sourceRow;
    }

    public Map<String, String> getValues() {
        return values;
    }

    public String position() {
        return sheet + " 第" + sourceRow + "行";
    }
}

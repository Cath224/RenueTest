package org.example.model;

import java.math.BigDecimal;

public class FilterNode {
    private String stringValue;
    private BigDecimal numValue;
    private int columnIndex;
    private String operator;


    public FilterNode(String stringValue, BigDecimal numValue, int columnIndex, String operator) {
        this.stringValue = stringValue;
        this.operator = operator;
        this.numValue = numValue;
        this.columnIndex = columnIndex;
    }


    public String getStringValue() {
        return stringValue;
    }

    public BigDecimal getNumValue() {
        return numValue;
    }

    public int getColumnIndex() {
        return columnIndex;
    }

    public String getOperator() {
        return operator;
    }
}

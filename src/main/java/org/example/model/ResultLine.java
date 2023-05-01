package org.example.model;

public class ResultLine {
    private String name;
    private String line;

    public ResultLine(String name, String line) {
        this.name = name;
        this.line = line;
    }

    public String getName() {
        return name;
    }

    public String getLine() {
        return line;
    }

    @Override
    public String toString() {
        return name + " [" + line + "]";
    }
}

package org.example;

import org.example.model.FilterNode;
import org.example.model.ResultLine;
import org.mvel2.MVEL;

import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;

public class Airport {

    private static final String FILENAME = "airports.csv";
    private static final String EXIT_COMMAND = "!quit";
    private static long start, end;

    private static TreeMap<Integer, String> nameHolder;

    public static void main(String[] args) throws IOException {

        initNameHolder();

        while (true) {
            Scanner scanner = new Scanner(System.in);
            System.out.println("Input filter: example(column[7]=-6.081689834590001&(column[5]='GKA'||column[6]='AYGO'))");
            String filter = scanner.nextLine();
            if (EXIT_COMMAND.equals(filter)) {
                break;
            }

            System.out.println("Input search: ");
            String target = scanner.nextLine();

            if (EXIT_COMMAND.equals(target)) {
                break;
            }
            start = System.currentTimeMillis();
            List<FilterNode> filterNodes = parseFilter(filter);
            List<Integer> indexes = find(target.toLowerCase());
            List<ResultLine> resultLines = getLinesFromFile(indexes, filterNodes, filter);
            end = System.currentTimeMillis();
            printResult(resultLines);
        }
    }

    private static List<FilterNode> parseFilter(String filter) {

        ArrayList<String> symbolsList = new ArrayList<String>();
        for (int i = 0; i < filter.length(); i++) {
            char symbol = filter.charAt(i);
            if (symbol == '<' || symbol == '>' || symbol == '=' || (i < filter.length() - 1 && symbol == '<' && filter.charAt(i + 1) == '>')) {
                symbolsList.add(String.valueOf(symbol));
                if (symbol == '<' && filter.charAt(i + 1) == '>') {
                    int size = symbolsList.size();
                    symbolsList.add("<>");
                    symbolsList.remove(size - 1);
                    i++;
                }
            }
        }

        List<FilterNode> filterNodes = new ArrayList<>();
        String[] expression = filter.split("&|\\|\\|");
        for (int i = 0; i < expression.length; i++) {
            expression[i] = expression[i].replace("column", "");
            expression[i] = expression[i].replace("[", "");
            expression[i] = expression[i].replace("]", "");
            expression[i] = expression[i].replace("(", "");
            expression[i] = expression[i].replace(")", "");
            expression[i] = expression[i].replace("<>", "!=");
            String valueString = "";
            BigDecimal valueNum = null;
            int column = 0;


            if (expression[i].contains(">") || expression[i].contains("<") || expression[i].contains("=") || expression[i].contains("!=")) {
                String[] parts = expression[i].split(">|<|=|!=");

                if (parts.length == 2) {
                    column = Integer.parseInt(parts[0].trim().substring(0, parts[0].length()));
                    String comparisonValue = parts[1].trim();
                    if (comparisonValue.contains("'")) {
                        valueString = comparisonValue.replace("'", "");
                    } else {
                        valueNum = new BigDecimal(comparisonValue);
                    }
                    filterNodes.add(new FilterNode(valueString, valueNum, column - 1, symbolsList.get(i)));
                }
            }

        }
        return filterNodes;
    }

    private static void printResult(List<ResultLine> resultLines) {
        if (resultLines == null)
            return;
        resultLines.sort(Comparator.comparing(ResultLine::getName, Comparator.naturalOrder()));
        for (ResultLine line : resultLines) {
            System.out.println(line.toString());
        }
        System.out.println("Total result: " + resultLines.size());
        System.out.println("Time: " + (end - start));
    }
    private static List<ResultLine> getLinesFromFile(List<Integer> indexes,
                                                 List<FilterNode> filterNodes,
                                                 String filter) throws IOException {
        List<ResultLine> resultToPrint = new ArrayList<>();
        try (BufferedReader reader
                     = new BufferedReader(new InputStreamReader(Airport.class.getClassLoader().getResourceAsStream(FILENAME)))) {
            indexes.sort(Comparator.naturalOrder());
            int index = 0;
            int indexAutocomplete = 0;
            Integer currIndex = indexes.get(indexAutocomplete);
            int lastIndex = indexes.get(indexes.size() - 1);

            String currLine;

            while ((currLine = reader.readLine()) != null) {
                String[] comparison = currLine.split(",");
                List<Boolean> expressionResultList = calculateFilterExpressionForCurrentLine(comparison, filterNodes);
                String logicString = buildFilterLogicString(filter, expressionResultList);
                boolean filterResult;
                try {
                    filterResult = logicString.isEmpty() || (boolean) MVEL.eval(logicString);
                } catch (Exception e) {
                    System.out.println("Filter is not correct");
                    return null;
                }

                if (index == currIndex && filterResult) {
                    resultToPrint.add(new ResultLine(comparison[1], currLine));
                }
                if (index >= currIndex && currIndex < lastIndex) {
                    indexAutocomplete++;
                    currIndex = indexes.get(indexAutocomplete);
                }
                index++;
            }
        }
        return resultToPrint;
    }

    private static List<Boolean> calculateFilterExpressionForCurrentLine(String[] comparison, List<FilterNode> filterNodes) {
        List<Boolean> expressionResultList = new ArrayList<>();
        boolean correct;
        for (int i = 0; i < filterNodes.size(); i++) {
            FilterNode currFilterNode = filterNodes.get(i);
            int comparisonColumn = currFilterNode.getColumnIndex();
            boolean stringInt = isNumber(comparison[comparisonColumn]);

            if (stringInt) {
                BigDecimal comparisonValueNum = currFilterNode.getNumValue();
                String operator = currFilterNode.getOperator();

                correct = false;
                switch (operator) {
                    case "<":
                        correct = new BigDecimal(comparison[comparisonColumn]).compareTo(comparisonValueNum) < 0;

                        break;
                    case ">":
                        correct = new BigDecimal(comparison[comparisonColumn]).compareTo(comparisonValueNum) > 0;

                        break;
                    case "<>":
                        correct = new BigDecimal(comparison[comparisonColumn]).compareTo(comparisonValueNum) != 0;

                        break;
                    case "=":
                        correct = new BigDecimal(comparison[comparisonColumn]).compareTo(comparisonValueNum) == 0;

                        break;
                }


            } else {
                String comparisonValueList = currFilterNode.getStringValue();
                String operator = currFilterNode.getOperator();
                String without = comparison[comparisonColumn].replace("\"", " ").trim();

                correct = false;
                switch (operator) {
                    case "=":
                        correct = without.contains(comparisonValueList);
                        break;
                    case ">":
                        break;
                    case "!=":
                        break;
                    case "<":
                        break;
                }


            }
            expressionResultList.add(correct);
        }
        return expressionResultList;
    }

    private static String buildFilterLogicString(String filter, List<Boolean> expressionResult) {
        StringBuilder logicString = new StringBuilder();
        int i = 0;
        int indexBoolean = 0;

        char[] charFilterString = filter.toCharArray();
        for (; i < charFilterString.length; i++) {

            if ((charFilterString[i] == '(') || (charFilterString[i] == ')')) {
                logicString.append(charFilterString[i]);
            } else if ((charFilterString[i] == '&')) {
                logicString.append(charFilterString[i]);
                logicString.append(charFilterString[i]);
            } else if ((charFilterString[i] == '|') && ((charFilterString[i + 1] == '|'))) {
                logicString.append(charFilterString[i]);
                logicString.append(charFilterString[i]);
                i++;
            } else if ((charFilterString[i] == '[') && ((charFilterString[i + 1] == ']'))) {
                continue;
            } else if ((charFilterString[i] == 'c')) {
                logicString.append(expressionResult.get(indexBoolean++));
            }
        }
        return logicString.toString();
    }

    private static List<Integer> find(String target) {
        List<Integer> indexes = new ArrayList<>();

        int targetLength = target.length();
        for (Map.Entry<Integer, String> entry : nameHolder.entrySet()) {
            String name = entry.getValue();
            if (targetLength > name.length()) {
                continue;
            }

            boolean isSimilar = true;
            for (int i = 0; i < targetLength; i++) {
                if (isSimilar) {
                    isSimilar = target.charAt(i) == name.charAt(i);
                }
            }

            if (isSimilar) {
                indexes.add(entry.getKey());
            }
        }
        return indexes;
    }


    private static void initNameHolder() throws IOException {
        nameHolder = new TreeMap<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(Airport.class.getClassLoader().getResourceAsStream(FILENAME)))) {
            String currLine;
            int index = 0;
            while ((currLine = reader.readLine()) != null) {
                String nameLine = currLine.split(",")[1].replace("\"", "").toLowerCase();
                nameHolder.put(index, nameLine);
                index++;
            }
        }
    }

    static public boolean isNumber(String s) {
        try {
            new BigDecimal(s);
            return true;
        } catch (NumberFormatException ex) {
            return false;
        }
    }
}




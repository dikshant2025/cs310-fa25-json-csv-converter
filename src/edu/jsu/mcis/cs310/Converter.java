package edu.jsu.mcis.cs310;

import com.github.cliftonlabs.json_simple.*;
import com.opencsv.*;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.Arrays;

public class Converter {

    @SuppressWarnings("unchecked")
    public static String csvToJson(String csvString) {

        String result = "{}";

try (CSVReader reader = new CSVReader(new StringReader(csvString))) {

    // Read rows one-by-one (avoids readAll() incompatibilities)
    java.util.List<String[]> rows = new java.util.ArrayList<>();
    String[] line;
    while ((line = reader.readNext()) != null) {
        // skip completely empty lines if any
        if (line.length == 1 && (line[0] == null || line[0].trim().isEmpty())) continue;
        rows.add(line);
    }

    // Empty/invalid CSV -> empty JSON shape
    if (rows.isEmpty()) {
        JsonObject empty = new JsonObject();
        empty.put("ProdNums", new JsonArray());
        empty.put("ColHeadings", new JsonArray());
        empty.put("Data", new JsonArray());
        return Jsoner.serialize(empty);
    }

    // Header row
    String[] headers = rows.get(0);
    for (int i = 0; i < headers.length; i++) {
    if (headers[i] != null) {
        headers[i] = headers[i].replace("\uFEFF", "").trim();
    } else {
        headers[i] = "";
    }
}
    // Find ProdNum safely
    int prodNumIdx = -1;
    for (int i = 0; i < headers.length; i++) {
        if ("ProdNum".equals(headers[i])) { prodNumIdx = i; break; }
    }
    if (prodNumIdx == -1) throw new IllegalArgumentException("CSV missing 'ProdNum' column.");

    // Build ColHeadings
    JsonArray colHeadings = new JsonArray();
    colHeadings.addAll(Arrays.asList(headers));

    // Build ProdNums and Data
    JsonArray prodNums = new JsonArray();
    JsonArray data = new JsonArray();

    for (int r = 1; r < rows.size(); r++) {
        String[] row = rows.get(r);
        if (row == null || row.length == 0) continue;

        prodNums.add(prodNumIdx < row.length ? row[prodNumIdx] : "");

        JsonArray one = new JsonArray();
        for (int c = 0; c < headers.length; c++) {
            if (c == prodNumIdx) continue;

            String col = headers[c];
            String val = (c < row.length) ? row[c] : "";

            if ("Season".equals(col) || "Episode".equals(col)) {
                one.add(parseIntSafe(val)); // JSON number
            } else {
                one.add(val);               // JSON string
            }
        }
        data.add(one);
    }

    JsonObject root = new JsonObject();
    root.put("ProdNums", prodNums);
    root.put("ColHeadings", colHeadings);
    root.put("Data", data);

    return Jsoner.serialize(root);
}

        catch (Exception e) {
            e.printStackTrace();
        }

        return result.trim();
    }

    @SuppressWarnings("unchecked")
    public static String jsonToCsv(String jsonString) {

        String result = "";

        try {
            // Parse JSON
            JsonObject root = (JsonObject) Jsoner.deserialize(jsonString);
            JsonArray prodNums = (JsonArray) root.get("ProdNums");
            JsonArray colHeadings = (JsonArray) root.get("ColHeadings");
            JsonArray data = (JsonArray) root.get("Data");

            if (prodNums == null || colHeadings == null || data == null) {
                throw new IllegalArgumentException("JSON missing required keys.");
            }

            // Write CSV via OpenCSV
            StringWriter sw = new StringWriter();
            try (CSVWriter writer = new CSVWriter(sw)) {

                // Header row
                String[] headerRow = new String[colHeadings.size()];
                for (int i = 0; i < colHeadings.size(); i++) {
                    headerRow[i] = String.valueOf(colHeadings.get(i));
                }
                writer.writeNext(headerRow, true);

                // Each record assembled in header order
                for (int i = 0; i < prodNums.size(); i++) {
                    String[] row = new String[colHeadings.size()];
                    String prod = String.valueOf(prodNums.get(i));
                    JsonArray one = (JsonArray) data.get(i); // values excluding ProdNum

                    int dataIdx = 0;
                    for (int c = 0; c < colHeadings.size(); c++) {
                        String col = String.valueOf(colHeadings.get(c));
                        if ("ProdNum".equals(col)) {
                            row[c] = prod;
                        } else {
                            Object v = one.get(dataIdx++);
                            if ("Season".equals(col)) {
                                row[c] = (v == null) ? "" : String.valueOf(v);
                            } else if ("Episode".equals(col)) {
                                // back to 2-digit, zero-padded string
                                int ep = (v == null) ? 0 : parseIntSafe(String.valueOf(v));
                                row[c] = zeroPad2(ep);
                            } else {
                                row[c] = (v == null) ? "" : String.valueOf(v);
                            }
                        }
                    }
                    writer.writeNext(row, true);
                }
            }

            result = sw.toString();
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        return result.trim();
    }

    // ---- helpers ------------------------------------------------------------

    private static int parseIntSafe(String s) {
        if (s == null) return 0;
        s = s.trim();
        if (s.isEmpty()) return 0;
        return Integer.parseInt(s);
    }

    private static String zeroPad2(int n) {
        return String.format("%02d", n);
    }
}

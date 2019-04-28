package stochastic.utility;

// Source: https://github.com/agilepro/mendocino/blob/master/src/com/purplehillsbooks/streams/CSVHelper.java

/*
 * Copyright 2013 Keith D Swenson
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

/**
 * Helps to read and write a CSV file, all methods are static writeLine:
 * Converts list of String values into a line of a CSV file parseLine: read a
 * line from a LineNumberReader and return the list of Strings
 * <p>
 * That should be all you need. Create or open the file and streams yourself from
 * whatever source you need to read from.. Everything in this class works on
 * characters, and not bytes.
 */
public class CSVHelper {

    /**
     * Just a convenience method that iterates the rows of a table and outputs
     * to a writer which is presumably a CSV file.
     *
     * @param w     writer (usually a BufferedWriter object to write to a file).
     * @param table rows to write as comma-separated values.
     * @throws IOException if there is an issue writing to the csv file via the writer.
     */
    public static void writeTable(Writer w, List<List<String>> table) throws IOException {
        for (List<String> row : table)
            writeLine(w, row);
    }

    /**
     * Write a single row of a CSV table, all values are quoted.
     *
     * @param w      writer (usually a BufferedWriter object to write to a file).
     * @param values row to write to csv file.
     * @throws IOException if there is an issue writing to the csv file via the writer.
     */
    public static void writeLine(Writer w, List<String> values) throws IOException {
        boolean firstVal = true;
        for (String val : values) {
            if (!firstVal) {
                w.write(",");
            }
            w.write("\"");
            for (int i = 0; i < val.length(); i++) {
                char ch = val.charAt(i);
                if (ch == '\"') {
                    w.write("\""); // extra quote
                }
                w.write(ch);
            }
            w.write("\"");
            firstVal = false;
        }
        w.write("\n");
    }

    /**
     * returns a row of values as a list.
     * <p>
     * returns null if you are past the end of the line.
     *
     * @param r handle that holds a csv file.
     * @return list of strings read from 1 row of the csv file.
     * @throws IOException if there is any issue reading from the file.
     */
    public static List<String> parseLine(Reader r) throws IOException {
        int ch = r.read();
        while (ch == '\r') {
            //ignore linefeed characters wherever they are, particularly just before end of file
            ch = r.read();
        }
        if (ch < 0) {
            return null;
        }
        ArrayList<String> store = new ArrayList<>();
        StringBuilder curVal = new StringBuilder();
        boolean inquotes = false;
        boolean started = false;
        while (ch >= 0) {
            if (inquotes) {
                started = true;
                if (ch == '\"') {
                    inquotes = false;
                } else {
                    curVal.append((char) ch);
                }
            } else {
                if (ch == '\"') {
                    inquotes = true;
                    if (started) {
                        // if this is the second quote in a value, add a quote
                        // this is for the double quote in the middle of a value
                        curVal.append('\"');
                    }
                } else if (ch == ',') {
                    store.add(curVal.toString());
                    curVal = new StringBuilder();
                    started = false;
                } else if (ch == '\r') {
                    //ignore LF characters
                } else if (ch == '\n') {
                    //end of a line, break out
                    break;
                } else {
                    curVal.append((char) ch);
                }
            }
            ch = r.read();
        }
        store.add(curVal.toString());
        return store;
    }
}

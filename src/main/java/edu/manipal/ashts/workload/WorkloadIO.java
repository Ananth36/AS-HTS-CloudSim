package edu.manipal.ashts.workload;

import edu.manipal.ashts.core.PriorityClass;
import edu.manipal.ashts.core.TaskSpec;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Reads/writes workloads as CSV:
 * <pre>id,arrival_time,length_mi,priority,deadline,phase</pre>
 * Also reads a real GoCJ dataset file (one job size in MI per line).
 */
public final class WorkloadIO {

    private WorkloadIO() {}

    public static void write(List<TaskSpec> tasks, File f) throws IOException {
        if (f.getParentFile() != null) f.getParentFile().mkdirs();
        PrintWriter w = new PrintWriter(f, "UTF-8");
        try {
            w.println("id,arrival_time,length_mi,priority,deadline,phase");
            for (TaskSpec t : tasks) {
                w.println(String.format(Locale.US, "%d,%.3f,%d,%s,%.3f,%s",
                        t.id, t.arrivalTime, t.lengthMI, t.priority.name(), t.deadline, t.phase));
            }
        } finally {
            w.close();
        }
    }

    public static List<TaskSpec> read(File f) throws IOException {
        List<TaskSpec> out = new ArrayList<TaskSpec>();
        BufferedReader r = new BufferedReader(new FileReader(f));
        try {
            String line;
            boolean header = true;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                if (header) { header = false; if (line.toLowerCase().startsWith("id")) continue; }
                String[] c = line.split(",");
                String phase = c.length > 5 ? c[5].trim() : "CUSTOM";
                out.add(new TaskSpec(out.size(), Double.parseDouble(c[1].trim()), Long.parseLong(c[2].trim()),
                        PriorityClass.parse(c[3]), Double.parseDouble(c[4].trim()), phase));
            }
        } finally {
            r.close();
        }
        return out;
    }

    /** GoCJ dataset files contain one job size (MI) per line. */
    public static long[] readGoCJ(File f) throws IOException {
        List<Long> v = new ArrayList<Long>();
        BufferedReader r = new BufferedReader(new FileReader(f));
        try {
            String line;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                try {
                    v.add((long) Double.parseDouble(line.split("[,;\\s]+")[0]));
                } catch (NumberFormatException ignored) {
                    // header or junk line
                }
            }
        } finally {
            r.close();
        }
        long[] a = new long[v.size()];
        for (int i = 0; i < a.length; i++) a[i] = v.get(i);
        return a;
    }
}

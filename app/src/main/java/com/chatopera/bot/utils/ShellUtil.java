package com.chatopera.bot.utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class ShellUtil {
    public static final String PWD = System.getProperty("user.dir");

    public ShellUtil() {
    }

    public static int command(String cmdline) {
        return command(cmdline, PWD);
    }

    public static int command(String cmdline, String directory) {
        List<String> result = new ArrayList<>();
        return command(cmdline, directory, result);
    }

    public static int command(String cmdline, String directory, List<String> result) {
        try {
            ProcessBuilder bash = new ProcessBuilder("bash", "-c", cmdline);
            Process process = bash.redirectErrorStream(true).directory(new File(directory)).start();
            BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()));
            if (result != null) {
                String line;
                while ((line = br.readLine()) != null) {
                    result.add(line);
                }
            }
            return process.waitFor();
        } catch (Exception e) {
            return 10;
        }
    }
}

package com.chatopera.bot.utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Iterator;

public class ShellUtil {
    public static final String PWD = System.getProperty("user.dir");

    public ShellUtil() {
    }

    public static String pwd() {
        return PWD;
    }

    public static int command(String cmdline) {
        return command(cmdline, PWD);
    }

    public static int command(String cmdline, String directory) {
        ArrayList<String> result = new ArrayList();
        int code = command(cmdline, directory, result);
        StringBuffer sb = new StringBuffer();
        Iterator var5 = result.iterator();

        while (var5.hasNext()) {
            String line = (String) var5.next();
            sb.append(line);
            sb.append("\n");
        }

        return code;
    }

    public static int command(String cmdline, String directory, ArrayList<String> result) {
        try {
            Process process = (new ProcessBuilder(new String[]{"bash", "-c", cmdline})).redirectErrorStream(true).directory(new File(directory)).start();
            BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()));
            if (result != null) {
                String line = null;

                while ((line = br.readLine()) != null) {
                    result.add(line);
                }
            }

            int exitCode = process.waitFor();
            return exitCode;
        } catch (Exception var6) {
            return 10;
        }
    }
}

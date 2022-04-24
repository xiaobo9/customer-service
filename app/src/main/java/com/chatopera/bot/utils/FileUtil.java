package com.chatopera.bot.utils;

import com.chatopera.bot.exception.FileNotExistException;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class FileUtil {
    public FileUtil() {
    }

    public static boolean exists(String path) {
        return new File(path).exists();
    }

    public static boolean isDirectory(String path) throws FileNotExistException {
        File f = new File(path);
        if (f.exists()) {
            return f.isDirectory();
        }
        throw new FileNotExistException("File not exist.");
    }

    public static boolean isFile(String path) throws FileNotExistException {
        File f = new File(path);
        if (f.exists()) {
            return f.isFile();
        }
        throw new FileNotExistException("File not exist.");
    }

    public static List<String> readLines(String filePath) throws FileNotExistException, IOException {
        return readLines(filePath, false);
    }

    public static List<String> readLines(String filePath, boolean trim) throws FileNotExistException, IOException {
        if (StringUtils.isBlank(filePath)) {
            throw new FileNotExistException("Blank file path param is invalid.");
        }
        if (isFile(filePath)) {
            throw new FileNotExistException("Path is not file but directory.");
        }
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            List<String> result = new ArrayList<>();
            // TODO
            for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                result.add(trim ? line.trim() : line);
            }
            reader.close();
            return result;
        }
    }

    public static void writeLines(String filePath, List<String> lines) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(new File(filePath));
             BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(fos));) {
            for (String line : lines) {
                bw.write(line);
                bw.newLine();
            }
        }
    }

    public static void remove(String path) throws IOException {
        if (exists(path)) {
            ShellUtil.command("rm -rf " + path);
        }
    }

    public static int move(String pre, String post) throws FileNotExistException {
        if (exists(pre) && StringUtils.isNotBlank(post)) {
            return ShellUtil.command("mv " + pre + " " + post);
        }
        throw new FileNotExistException("File or directory not exist OR target path is empty string.");
    }

    public static int copy(String source, String copied) throws FileNotExistException {
        if (exists(source) && StringUtils.isNotBlank(copied)) {
            return ShellUtil.command("cp -rf " + source + " " + copied);
        }
        throw new FileNotExistException("File or directory not exist OR copied path is empty string.");
    }

    public static int ln(String source, String link) throws FileNotExistException, IOException {
        if (!exists(source) || StringUtils.isBlank(link)) {
            throw new FileNotExistException("File or directory not exist OR copied path is empty string. Source " + source + ", target " + link);
        }
        if (FileUtils.isSymlink(new File(link))) {
            return ShellUtil.command("ln -nsf " + source + " " + link);
        }
        if (exists(link)) {
            remove(link);
        }

        return ShellUtil.command("ln -s " + source + " " + link);
    }
}

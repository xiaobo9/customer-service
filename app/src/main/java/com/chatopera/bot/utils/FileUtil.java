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
        File f = new File(path);
        return f.exists();
    }

    public static boolean isDirectory(String path) throws FileNotExistException {
        File f = new File(path);
        if (!f.exists()) {
            throw new FileNotExistException("File not exist.");
        } else {
            return f.isDirectory();
        }
    }

    public static boolean isFile(String path) throws FileNotExistException {
        File f = new File(path);
        if (!f.exists()) {
            throw new FileNotExistException("File not exist.");
        } else {
            return f.isFile();
        }
    }

    public static List<String> readlines(String filePath, boolean istrim) throws FileNotExistException {
        if (StringUtils.isBlank(filePath)) {
            throw new FileNotExistException("Blank file path param is invalid.");
        } else if (isFile(filePath)) {
            ArrayList ret = new ArrayList();

            try {
                BufferedReader reader = new BufferedReader(new FileReader(filePath));
                Throwable var4 = null;

                try {
                    for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                        ret.add(istrim ? line.trim() : line);
                    }

                    reader.close();
                    ArrayList var6 = ret;
                    return var6;
                } catch (Throwable var16) {
                    var4 = var16;
                    throw var16;
                } finally {
                    if (reader != null) {
                        if (var4 != null) {
                            try {
                                reader.close();
                            } catch (Throwable var15) {
                                var4.addSuppressed(var15);
                            }
                        } else {
                            reader.close();
                        }
                    }

                }
            } catch (IOException var18) {
                throw new FileNotExistException("IOException", var18);
            }
        } else {
            throw new FileNotExistException("Path is not file but directory.");
        }
    }

    public static List<String> readlines(String filePath) throws FileNotExistException {
        return readlines(filePath, false);
    }

    public static void writelines(String filePath, List<String> lines) throws IOException {
        File fout = new File(filePath);
        FileOutputStream fos = new FileOutputStream(fout);
        BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(fos));

        for (int i = 0; i < lines.size(); ++i) {
            bw.write((String) lines.get(i));
            bw.newLine();
        }

        bw.close();
    }

    public static void remove(String path) throws IOException {
        if (exists(path)) {
            ShellUtil.command("rm -rf " + path);
        }

    }

    public static int move(String pre, String post) throws FileNotExistException {
        if (exists(pre) && StringUtils.isNotBlank(post)) {
            return ShellUtil.command("mv " + pre + " " + post);
        } else {
            throw new FileNotExistException("File or directory not exist OR target path is empty string.");
        }
    }

    public static int copy(String source, String copied) throws FileNotExistException {
        if (exists(source) && StringUtils.isNotBlank(copied)) {
            return ShellUtil.command("cp -rf " + source + " " + copied);
        } else {
            throw new FileNotExistException("File or directory not exist OR copied path is empty string.");
        }
    }

    public static int ln(String source, String link) throws FileNotExistException, IOException {
        if (exists(source) && StringUtils.isNotBlank(link)) {
            if (FileUtils.isSymlink(new File(link))) {
                return ShellUtil.command("ln -nsf " + source + " " + link);
            } else {
                if (exists(link)) {
                    remove(link);
                }

                return ShellUtil.command("ln -s " + source + " " + link);
            }
        } else {
            throw new FileNotExistException("File or directory not exist OR copied path is empty string. Source " + source + ", target " + link);
        }
    }
}

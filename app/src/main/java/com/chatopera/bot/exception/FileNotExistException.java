package com.chatopera.bot.exception;

public class FileNotExistException extends Exception {
    public FileNotExistException(String msg) {
        super(msg);
    }

    public FileNotExistException(String msg, Throwable cause) {
        super(msg, cause);
    }
}

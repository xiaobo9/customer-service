package com.github.xiaobo9.mail;

import java.util.List;

public class Mail implements java.io.Serializable {
    public Mail() {
    }

    public Mail(String email, String subject, String content) {
        this.email = email;
        this.subject = subject;
        this.content = content;
    }

    public Mail(String email, String cc, List<String> filenames, String subject, String content) {
        this.email = email;
        this.cc = cc;
        this.filenames = filenames;
        this.subject = subject;
        this.content = content;
    }

    private static final long serialVersionUID = 1L;
    private String email;
    private String cc;
    private List<String> filenames;
    private String subject;
    private String content;

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getCc() {
        return cc;
    }

    public void setCc(String cc) {
        this.cc = cc;
    }

    public List<String> getFilenames() {
        return filenames;
    }

    public void setFilenames(List<String> filenames) {
        this.filenames = filenames;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }


}

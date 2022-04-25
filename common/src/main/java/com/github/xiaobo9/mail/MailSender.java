package com.github.xiaobo9.mail;

import org.apache.commons.lang.StringUtils;

import javax.activation.DataHandler;
import javax.activation.FileDataSource;
import javax.mail.Message.RecipientType;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.*;
import java.io.UnsupportedEncodingException;
import java.security.Security;
import java.util.List;
import java.util.Properties;

public class MailSender {
    /**
     * 发送邮件的props文件
     */
    private final transient Properties props = System.getProperties();
    /**
     * 邮件服务器登录验证
     */
    private transient MailAuthenticator authenticator;

    /**
     * 发送邮箱
     */
    private String fromEmail;
    /**
     * 邮箱session
     */
    private transient Session session;

    /**
     * 初始化邮件发送器
     *
     * @param smtpHostName SMTP邮件服务器地址
     * @param username     发送邮件的用户名(地址)
     * @param password     发送邮件的密码
     */
    public MailSender(final String smtpHostName, final String username,
                      final String password, final String seclev, String sslport) {
        init(username, password, smtpHostName, seclev, sslport);
    }

    /**
     * 初始化邮件发送器
     *
     * @param smtpHostName SMTP邮件服务器地址
     * @param fromEmail    邮件发送地址
     * @param username     发送邮件的用户名(地址)
     * @param password     发送邮件的密码
     * @param seclev       是否开启ssl
     */
    public MailSender(final String smtpHostName, final String fromEmail, final String username,
                      final String password, final String seclev, String sslport) {
        this.fromEmail = fromEmail;
        init(username, password, smtpHostName, seclev, sslport);
    }

    /**
     * 初始化邮件发送器
     *
     * @param username 发送邮件的用户名(地址)，并以此解析SMTP服务器地址
     * @param password 发送邮件的密码
     */
    public MailSender(final String username, final String password, final String seclev, String sslport) {
        // 通过邮箱地址解析出smtp服务器，对大多数邮箱都管用
        final String smtpHostName = "smtp." + username.split("@")[1];
        init(username, password, smtpHostName, seclev, sslport);
    }

    /**
     * 发送邮件
     *
     * @param recipient 收件人邮箱地址
     * @param mail      邮件对象
     * @throws AddressException
     * @throws MessagingException
     * @throws UnsupportedEncodingException
     */
    public void send(String recipient, MailInfo mail) throws AddressException,
            MessagingException, UnsupportedEncodingException {
        send(recipient, mail.getSubject(), mail.getContent());
    }

    /**
     * 群发邮件
     *
     * @param recipients 收件人们
     * @param mail       邮件对象
     * @throws AddressException
     * @throws MessagingException
     * @throws UnsupportedEncodingException
     */
    public void send(List<String> recipients, MailInfo mail)
            throws AddressException, MessagingException, UnsupportedEncodingException {
        send(recipients, mail.getSubject(), mail.getContent());
    }

    /**
     * 发送邮件
     *
     * @param recipient 收件人邮箱地址
     * @param subject   邮件主题
     * @param content   邮件内容
     * @throws AddressException
     * @throws MessagingException
     * @throws UnsupportedEncodingException
     */
    public void send(String recipient, String subject, Object content)
            throws AddressException, MessagingException, UnsupportedEncodingException {
        // 创建mime类型邮件
        final MimeMessage message = new MimeMessage(session);

        from(message);

        String[] toUsers = recipient.split("[\n,;]");
        InternetAddress[] internetAddress = new InternetAddress[toUsers.length];
        for (int i = 0; i < toUsers.length; i++) {
            internetAddress[i] = new InternetAddress(toUsers[i]);
        }
        message.setRecipients(RecipientType.TO, internetAddress);
        message.setSubject(MimeUtility.encodeText(subject, "UTF-8", "B"));
        message.setContent(content.toString(), "text/html;charset=utf-8");
        Transport.send(message);
    }


    /**
     * 附件发送
     *
     * @param recipient        接收人
     * @param recipient_append 抄送人
     * @param subject          邮件主题
     * @param content          邮件正文
     * @param filenames        附件列表
     * @throws AddressException
     * @throws MessagingException
     */
    public void send(String recipient, String recipient_append, String subject, Object content, List<String> filenames)
            throws Exception {
        // 创建mime类型邮件
        final MimeMessage message = new MimeMessage(session);

        from(message);

        // 设置收件人
        message.setRecipients(RecipientType.TO, recipient(recipient));

        // 设置抄送
        if (StringUtils.isNotBlank(recipient_append)) {
            message.setRecipients(RecipientType.CC, recipient(recipient_append));
        }

        message.setSubject(MimeUtility.encodeText(subject, "UTF-8", "B"));
        // 设置邮件内容
        Multipart multipart = new MimeMultipart();
        MimeBodyPart mbp = new MimeBodyPart();
        mbp.setContent(content.toString(), "text/html;charset=utf-8");
        multipart.addBodyPart(mbp);
        if (filenames != null && filenames.size() > 0) {//有附件
            for (String filename : filenames) {
                mbp = new MimeBodyPart();
                FileDataSource fds = new FileDataSource(filename);
                mbp.setDataHandler(new DataHandler(fds));
                mbp.setFileName(MimeUtility.encodeText(fds.getName(), "UTF-8", "B"));
                multipart.addBodyPart(mbp);
            }
        }

        message.setContent(multipart);
        Transport.send(message);
    }

    /**
     * 群发邮件
     *
     * @param recipients 收件人们
     * @param subject    主题
     * @param content    内容
     * @throws AddressException
     * @throws MessagingException
     * @throws UnsupportedEncodingException
     */
    public void send(List<String> recipients, String subject, Object content)
            throws AddressException, MessagingException, UnsupportedEncodingException {
        // 创建mime类型邮件
        final MimeMessage message = new MimeMessage(session);

        from(message);

        // 设置收件人们
        final int num = recipients.size();
        InternetAddress[] addresses = new InternetAddress[num];
        for (int i = 0; i < num; i++) {
            addresses[i] = new InternetAddress(recipients.get(i));
        }
        message.setRecipients(RecipientType.TO, addresses);
        message.setSubject(MimeUtility.encodeText(subject, "UTF-8", "B"));
        message.setContent(content.toString(), "text/html;charset=utf-8");
        Transport.send(message);
    }

    /**
     * 初始化
     *
     * @param username     发送邮件的用户名(地址)
     * @param password     密码
     * @param smtpHostName SMTP主机地址
     */
    @SuppressWarnings("restriction")
    private void init(String username, String password, String smtpHostName, String seclev, String sslport) {
        // 初始化props
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.host", smtpHostName);
        //ssl
        if (!StringUtils.isBlank(seclev) && seclev.equals("true")) {
            Security.addProvider(new com.sun.net.ssl.internal.ssl.Provider());
            final String SSL_FACTORY = "javax.net.ssl.SSLSocketFactory";
            props.put("mail.smtp.socketFactory.class", SSL_FACTORY);
            props.put("mail.smtp.socketFactory.fallback", "false");
            props.put("mail.smtp.port", sslport);
            props.put("mail.smtp.socketFactory.port", sslport);
        }
        // 验证
        authenticator = new MailAuthenticator(username, password);
        // 创建session
        session = Session.getInstance(props, authenticator);
    }

    private void from(MimeMessage message) throws MessagingException {
        if (authenticator != null && authenticator.getUsername().indexOf("@") > 0) {
            message.setFrom(new InternetAddress(authenticator.getUsername()));
        }
        if (this.fromEmail != null) {
            // message.setFrom(new InternetAddress(this.fromEmail) + " <" + authenticator.getUsername() + ">");
            message.setFrom(new InternetAddress(this.fromEmail));
        }
    }

    private InternetAddress[] recipient(String recipient) throws UnsupportedEncodingException, AddressException {
        String[] toUsers = recipient.split("[\n,]");
        InternetAddress[] internetAddress = new InternetAddress[toUsers.length];
        for (int i = 0; i < toUsers.length; i++) {
            if (toUsers[i].indexOf("<") > 0 && toUsers[i].indexOf(">") > 0) {
                String name = toUsers[i].substring(0, toUsers[i].indexOf("<"));
                String address = toUsers[i].substring(toUsers[i].indexOf("<") + 1, toUsers[i].indexOf(">"));
                internetAddress[i] = new InternetAddress(address, name, "utf-8");
            } else {
                internetAddress[i] = new InternetAddress(toUsers[i]);
            }

        }
        return internetAddress;
    }

}

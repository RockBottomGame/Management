package de.canitzp.rockbottommanagement;

import org.apache.commons.io.FileUtils;
import org.simplejavamail.api.email.Email;
import org.simplejavamail.api.mailer.Mailer;
import org.simplejavamail.email.EmailBuilder;
import org.simplejavamail.mailer.MailerBuilder;

import java.io.File;
import java.io.IOException;
import java.util.Properties;
import java.util.UUID;

public class MailHelper {

    private static final Properties MAIL_PROPERTIES;
    private static final Mailer MAILER;

    static {
        MAIL_PROPERTIES = new Properties();
        try {
            MAIL_PROPERTIES.load(FileUtils.openInputStream(new File(".", "mail.properties")));
        } catch (IOException e) {
            e.printStackTrace();
        }
        MAILER = MailerBuilder
                .withSMTPServer(
                        MAIL_PROPERTIES.getProperty("smtp.server"),
                        Integer.parseInt(MAIL_PROPERTIES.getProperty("smtp.port")),
                        MAIL_PROPERTIES.getProperty("mail.address"),
                        MAIL_PROPERTIES.getProperty("mail.password"))
                .buildMailer();

    }

    public static void sendVerifyMail(String email, String username, UUID accountId, String verificationCode) {
        StringBuilder b = new StringBuilder();
        b.append("Hello fellow friend,<br>");
        b.append("You just created a account for the Open-Source game \"RockBottom\".<br>");
        b.append("This letter is for verifying your e-mail-address. Please paste the verification code below, the next time you log into your account.<br>");
        b.append("<br>");
        b.append("\tYour Username: '").append(username).append("'<br>");
        b.append("\tYour E-Mail address: '").append(email).append("'<br>");
        b.append("\tYour Account-Identifier: '").append(accountId.toString()).append("'<br>");
        b.append("<br>");
        b.append("\tVerification Code: <b>").append(verificationCode).append("<\\b><br>");
        b.append("<br>");
        b.append("Thank you for registering and have fun playing RockBottom!");
        b.append("<br>");
        b.append("<br>");
        b.append("Sincerely<br>");
        b.append("<br>");
        b.append("<b>RockBottom Team<\\b><br>");

        Email mail = EmailBuilder.startingBlank()
                .from(MAIL_PROPERTIES.getProperty("mail.address"))
                .to(email)
                .withSubject("RockBottom Account creation")
                .appendTextHTML(b.toString())
                .buildEmail();
        MAILER.sendMail(mail);
    }

    public static void sendForgotPasswordMail(String email, String username, String verificationCode) {
        StringBuilder b = new StringBuilder();
        b.append("Hello ").append(username).append(",<br>");
        b.append("You just requested a new verification code for \"RockBottom\".<br>");
        b.append("Please paste the given verification code into the given text field in the game.<br>");
        b.append("<br>");
        b.append("\tVerification Code: <b>").append(verificationCode).append("<\\b><br>");
        b.append("<br>");
        b.append("<br>");
        b.append("Sincerely<br>");
        b.append("<br>");
        b.append("<b>RockBottom Team<\\b><br>");

        Email mail = EmailBuilder.startingBlank()
                .from(MAIL_PROPERTIES.getProperty("mail.address"))
                .to(email)
                .withSubject("RockBottom password reset")
                .appendTextHTML(b.toString())
                .buildEmail();
        MAILER.sendMail(mail);
    }

}

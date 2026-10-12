package com.myhosh.booking;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private final JavaMailSender mailSender;

    // The email address you register with Brevo
    private final String fromEmail = "vis628385@gmail.com";

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendConfirmationEmail(String to, String customerName, String date, String time, int partySize) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(to);
            message.setSubject("Reservation Confirmed - MyHosh");
            message.setText("Dear " + customerName + ",\n\n" +
                    "Your reservation at MyHosh is confirmed!\n\n" +
                    "📅 Date: " + date + "\n" +
                    "⏰ Time: " + time + "\n" +
                    "👥 Party Size: " + partySize + " Guests\n\n" +
                    "We look forward to hosting you.\n\n" +
                    "Best regards,\nMyHosh Management Team");

            mailSender.send(message);
            System.out.println("Confirmation email sent to " + to);
        } catch (Exception e) {
            System.err.println("Failed to send confirmation email: " + e.getMessage());
        }
    }

    public void sendCancellationEmail(String to, String customerName, String date, String time, String reason) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(to);
            message.setSubject("Reservation Cancellation - MyHosh");
            message.setText("Dear " + customerName + ",\n\n" +
                    "This email is to inform you that your reservation for " + date +
                    " at " + time + " has been cancelled.\n\n" +
                    "Reason: " + reason + "\n\n" +
                    "If you have any questions or wish to rebook, please contact us.\n\n" +
                    "Best regards,\nMyHosh Management Team");

            mailSender.send(message);
            System.out.println("Cancellation email sent to " + to);
        } catch (Exception e) {
            System.err.println("Failed to send cancellation email: " + e.getMessage());
        }
    }
}

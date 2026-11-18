package com.myhosh.booking;

import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.util.concurrent.CompletableFuture;

@Service
public class EmailService {

    private final JavaMailSender mailSender;
    private final String fromEmail = "MyHosh Salisbury <vis628385@gmail.com>";

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    // Bounce Guard: Never send to null, empty, or internal .local dummy addresses
    private boolean isValidRecipient(String to) {
        if (to == null || to.isBlank()) return false;
        String clean = to.trim().toLowerCase();
        return clean.contains("@") && !clean.endsWith(".local") && !clean.equals("n/a");
    }

    // Backwards-compatible 5-arg method
    public void sendConfirmationEmail(String to, String customerName, String date, String time, int partySize) {
        sendConfirmationEmail(to, customerName, date, time, partySize, null);
    }

    // Upgraded 6-arg method with Booking Reference Code (MH-0000)
    public void sendConfirmationEmail(String to, String customerName, String date, String time, int partySize, Long bookingId) {
        if (!isValidRecipient(to)) {
            System.out.println("Skipping confirmation email: optional or internal address (" + to + ")");
            return;
        }

        // Security: HTML-escape all user-supplied values to block HTML/XSS injection
        String safeName = HtmlUtils.htmlEscape(customerName != null ? customerName : "Guest");
        String safeDate = HtmlUtils.htmlEscape(date != null ? date : "");
        String safeTime = HtmlUtils.htmlEscape(time != null && time.length() >= 5 ? time.substring(0, 5) : String.valueOf(time));
        String refCode = bookingId != null ? "MH-" + String.format("%04d", bookingId) : "Confirmed";

        String htmlBody = """
            <div style="font-family: Georgia, serif; max-width: 560px; margin: 0 auto; background-color: #FBF6EA; border: 1px solid #C89D54; border-radius: 10px; overflow: hidden; color: #1B1A17;">
              <div style="background-color: #0F4D3A; padding: 24px; text-align: center; border-bottom: 3px solid #C89D54;">
                <h1 style="margin: 0; color: #FBF6EA; letter-spacing: 3px; font-size: 26px;">MYHOSH</h1>
                <p style="margin: 4px 0 0; color: #E6C786; font-size: 12px; letter-spacing: 2px; text-transform: uppercase;">Salisbury</p>
              </div>
              <div style="padding: 28px 24px;">
                <h2 style="margin-top: 0; color: #0F4D3A; font-size: 20px;">Reservation Confirmed</h2>
                <p style="font-size: 15px; line-height: 1.6;">Dear <strong>%s</strong>,</p>
                <p style="font-size: 15px; line-height: 1.6;">We are delighted to confirm your table reservation at <strong>MyHosh Salisbury</strong>.</p>
                
                <div style="background-color: #FFFFFF; border: 1px dashed #0F4D3A; border-radius: 8px; padding: 16px; text-align: center; margin: 20px 0;">
                  <div style="font-size: 11px; color: #666; text-transform: uppercase; letter-spacing: 1px;">Booking Reference</div>
                  <div style="font-size: 24px; font-weight: bold; color: #0F4D3A; letter-spacing: 2px; margin-top: 4px;">%s</div>
                </div>

                <table style="width: 100%%; border-collapse: collapse; margin: 20px 0; font-size: 14px;">
                  <tr><td style="padding: 8px 0; border-bottom: 1px solid #E5DEC9;"><strong>📅 Date:</strong></td><td style="padding: 8px 0; border-bottom: 1px solid #E5DEC9; text-align: right;">%s</td></tr>
                  <tr><td style="padding: 8px 0; border-bottom: 1px solid #E5DEC9;"><strong>⏰ Time:</strong></td><td style="padding: 8px 0; border-bottom: 1px solid #E5DEC9; text-align: right;">%s</td></tr>
                  <tr><td style="padding: 8px 0; border-bottom: 1px solid #E5DEC9;"><strong>👥 Guests:</strong></td><td style="padding: 8px 0; border-bottom: 1px solid #E5DEC9; text-align: right;">%d Guests</td></tr>
                </table>

                <div style="background-color: rgba(200, 157, 84, 0.15); border-left: 4px solid #C89D54; padding: 12px; font-size: 13px; line-height: 1.5; margin-bottom: 20px;">
                  <strong>Sitting Policy:</strong> Reservations are allocated for a <strong>2-hour sitting</strong>. Tables are held for 15 minutes past the reservation time.
                </div>

                <p style="font-size: 14px; line-height: 1.6; margin-bottom: 6px;">
                  <strong>📍 Address:</strong> <a href="https://maps.google.com/?q=99+Fisherton+Street+Salisbury+SP2+7SP" style="color: #0F4D3A;">99 Fisherton Street, Salisbury, SP2 7SP</a><br/>
                  <strong>📞 Phone:</strong> <a href="tel:+447853404534" style="color: #0F4D3A;">+44 7853404534</a>
                </p>
              </div>
              <div style="background-color: #1B1A17; color: #999; text-align: center; padding: 14px; font-size: 11px;">
                &copy; MyHosh Salisbury &bull; <a href="https://myhosh-salisbury.co.uk" style="color: #E6C786; text-decoration: none;">myhosh-salisbury.co.uk</a>
              </div>
            </div>
            """.formatted(safeName, refCode, safeDate, safeTime, partySize);

        // Non-blocking background dispatch
        CompletableFuture.runAsync(() -> {
            try {
                MimeMessage mimeMessage = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
                helper.setFrom(fromEmail);
                helper.setTo(to.trim());
                helper.setSubject("Reservation Confirmed (" + refCode + ") - MyHosh Salisbury");
                helper.setText(htmlBody, true);
                mailSender.send(mimeMessage);
                System.out.println("HTML confirmation email sent to " + to);
            } catch (Exception e) {
                System.err.println("Failed to send confirmation email: " + e.getMessage());
            }
        });
    }

    public void sendCancellationEmail(String to, String customerName, String date, String time, String reason) {
        if (!isValidRecipient(to)) {
            System.out.println("Skipping cancellation email: optional or internal address (" + to + ")");
            return;
        }

        String safeName = HtmlUtils.htmlEscape(customerName != null ? customerName : "Guest");
        String safeDate = HtmlUtils.htmlEscape(date != null ? date : "");
        String safeTime = HtmlUtils.htmlEscape(time != null && time.length() >= 5 ? time.substring(0, 5) : String.valueOf(time));
        String safeReason = HtmlUtils.htmlEscape(reason != null ? reason : "Cancelled by restaurant");

        String htmlBody = """
            <div style="font-family: Georgia, serif; max-width: 560px; margin: 0 auto; background-color: #FBF6EA; border: 1px solid #B3402F; border-radius: 10px; overflow: hidden; color: #1B1A17;">
              <div style="background-color: #1B1A17; padding: 22px; text-align: center; border-bottom: 3px solid #B3402F;">
                <h1 style="margin: 0; color: #FBF6EA; letter-spacing: 3px; font-size: 24px;">MYHOSH SALISBURY</h1>
              </div>
              <div style="padding: 26px 24px;">
                <h2 style="margin-top: 0; color: #B3402F; font-size: 20px;">Reservation Cancelled</h2>
                <p style="font-size: 15px; line-height: 1.6;">Dear <strong>%s</strong>,</p>
                <p style="font-size: 15px; line-height: 1.6;">Your reservation on <strong>%s</strong> at <strong>%s</strong> has been cancelled.</p>
                <div style="background-color: rgba(179, 64, 47, 0.08); border-left: 4px solid #B3402F; padding: 12px; font-size: 14px; margin: 18px 0;">
                  <strong>Reason:</strong> %s
                </div>
                <p style="font-size: 14px; line-height: 1.6;">If you wish to rebook, please visit <a href="https://myhosh-salisbury.co.uk" style="color: #0F4D3A;">myhosh-salisbury.co.uk</a> or call us on <strong>+44 7853404534</strong>.</p>
              </div>
            </div>
            """.formatted(safeName, safeDate, safeTime, safeReason);

        CompletableFuture.runAsync(() -> {
            try {
                MimeMessage mimeMessage = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
                helper.setFrom(fromEmail);
                helper.setTo(to.trim());
                helper.setSubject("Reservation Cancelled - MyHosh Salisbury");
                helper.setText(htmlBody, true);
                mailSender.send(mimeMessage);
                System.out.println("HTML cancellation email sent to " + to);
            } catch (Exception e) {
                System.err.println("Failed to send cancellation email: " + e.getMessage());
            }
        });
    }
}
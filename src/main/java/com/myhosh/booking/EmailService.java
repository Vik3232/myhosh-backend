package com.myhosh.booking;

import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;
import org.springframework.beans.factory.annotation.Value;

import java.util.concurrent.CompletableFuture;

@Service
public class EmailService {

    private final JavaMailSender mailSender;
    private final String fromEmail;
    private final String replyToEmail;

    public EmailService(
            JavaMailSender mailSender,
            @Value("${app.mail.from}") String fromEmail,
            @Value("${app.mail.reply-to}") String replyToEmail) {
        this.mailSender = mailSender;
        this.fromEmail = fromEmail;
        this.replyToEmail = replyToEmail;
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
        String guestsLabel = partySize == 1 ? "1 guest" : partySize + " guests";

        String template = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
              <meta charset="UTF-8" />
              <meta name="viewport" content="width=device-width, initial-scale=1.0" />
              <title>Reservation confirmed | MyHosh Salisbury</title>
              <link href="https://fonts.googleapis.com/css2?family=Geist+Mono:wght@500;600&family=Manrope:wght@400;500;600;700&display=swap" rel="stylesheet" />
            </head>
            <body style="margin:0;padding:0;background-color:#F4EFE4;font-family:'Manrope',-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;color:#1B1A17;-webkit-font-smoothing:antialiased;">
              <div style="display:none;max-height:0;overflow:hidden;opacity:0;mso-hide:all;">
                Table for {{GUESTS}} on {{DATE}} at {{TIME}}. Reference {{REF}} at MyHosh, 99 Fisherton Street, Salisbury.
              </div>

              <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="background-color:#F4EFE4;padding:32px 16px;">
                <tr>
                  <td align="center">
                    <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="max-width:560px;background-color:#FFFFFF;border:1px solid #E2D9C5;border-radius:16px;overflow:hidden;">

                      <!-- Top Brand Band (Flat Emerald, No Gradients) -->
                      <tr>
                        <td style="background-color:#0A2F25;padding:32px 24px;text-align:center;">
                          <div style="font-size:20px;line-height:28px;font-weight:600;letter-spacing:0.18em;color:#FBF6EA;">MYHOSH</div>
                          <div style="margin-top:4px;font-size:12px;line-height:16px;font-weight:500;letter-spacing:0.12em;text-transform:uppercase;color:#E5CFA3;">99 Fisherton Street, Salisbury</div>
                        </td>
                      </tr>

                      <!-- Main Content -->
                      <tr>
                        <td style="padding:32px 24px;">
                          <div style="display:inline-block;padding:4px 12px;border-radius:9999px;background-color:#EDF5F1;border:1px solid #B7D6C8;font-size:12px;line-height:16px;font-weight:600;color:#0F4D3A;margin-bottom:16px;">
                            Table confirmed
                          </div>

                          <h1 style="margin:0 0 12px 0;font-size:24px;line-height:32px;font-weight:600;color:#1B1A17;letter-spacing:-0.015em;">
                            We look forward to welcoming you, {{NAME}}
                          </h1>
                          <p style="margin:0 0 24px 0;font-size:15px;line-height:24px;font-weight:400;color:#5C584F;">
                            Your table at MyHosh Salisbury is reserved. Please keep your booking reference handy when you arrive.
                          </p>

                          <!-- Booking Reference Box (Nested Radius: 16 - 4 = 12px, Full 4-Sided Border) -->
                          <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="background-color:#FBF6EA;border:1px solid #D9C8A2;border-radius:12px;margin-bottom:24px;">
                            <tr>
                              <td style="padding:20px 16px;text-align:center;">
                                <div style="font-size:12px;line-height:16px;font-weight:500;color:#5C584F;margin-bottom:4px;">Booking reference</div>
                                <div style="font-family:'Geist Mono',ui-monospace,SFMono-Regular,Menlo,monospace;font-size:24px;line-height:32px;font-weight:600;letter-spacing:0.08em;color:#0F4D3A;">{{REF}}</div>
                              </td>
                            </tr>
                          </table>

                          <!-- Structured Details Table -->
                          <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="border:1px solid #EAE3D5;border-radius:12px;border-collapse:separate;margin-bottom:24px;">
                            <tr>
                              <td style="padding:14px 16px;border-bottom:1px solid #EAE3D5;font-size:14px;line-height:20px;font-weight:500;color:#5C584F;">Date</td>
                              <td style="padding:14px 16px;border-bottom:1px solid #EAE3D5;font-family:'Geist Mono',ui-monospace,SFMono-Regular,Menlo,monospace;font-size:14px;line-height:20px;font-weight:600;color:#1B1A17;text-align:right;">{{DATE}}</td>
                            </tr>
                            <tr>
                              <td style="padding:14px 16px;border-bottom:1px solid #EAE3D5;font-size:14px;line-height:20px;font-weight:500;color:#5C584F;">Time</td>
                              <td style="padding:14px 16px;border-bottom:1px solid #EAE3D5;font-family:'Geist Mono',ui-monospace,SFMono-Regular,Menlo,monospace;font-size:14px;line-height:20px;font-weight:600;color:#1B1A17;text-align:right;">{{TIME}}</td>
                            </tr>
                            <tr>
                              <td style="padding:14px 16px;font-size:14px;line-height:20px;font-weight:500;color:#5C584F;">Party size</td>
                              <td style="padding:14px 16px;font-size:14px;line-height:20px;font-weight:600;color:#1B1A17;text-align:right;">{{GUESTS}}</td>
                            </tr>
                          </table>

                          <!-- Sitting Policy (Full 4-Sided Border, Zero Hyphens) -->
                          <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="background-color:#FBF6EA;border:1px solid #E5D7BA;border-radius:12px;margin-bottom:24px;">
                            <tr>
                              <td style="padding:16px;font-size:13px;line-height:20px;color:#5C584F;">
                                <strong style="color:#1B1A17;font-weight:600;">Sitting policy:</strong> Tables are allocated for a two hour sitting and held for 15 minutes past your booking time. If your plans change, please call us ahead of time.
                              </td>
                            </tr>
                          </table>

                          <!-- Single Primary CTA -->
                          <table role="presentation" cellpadding="0" cellspacing="0" border="0" style="margin:0 0 24px 0;">
                            <tr>
                              <td style="border-radius:8px;background-color:#C89D54;">
                                <a href="https://maps.google.com/?q=99+Fisherton+Street+Salisbury+SP2+7SP" style="display:inline-block;padding:12px 20px;font-size:14px;line-height:20px;font-weight:600;color:#1B1A17;text-decoration:none;">
                                  Get directions to MyHosh
                                </a>
                              </td>
                            </tr>
                          </table>

                          <!-- Contact Footer Block -->
                          <p style="margin:0;font-size:14px;line-height:22px;color:#5C584F;">
                            <strong style="color:#1B1A17;font-weight:600;">Address:</strong> 99 Fisherton Street, Salisbury, SP2 7SP<br />
                            <strong style="color:#1B1A17;font-weight:600;">Phone:</strong> <a href="tel:+447853404534" style="color:#0F4D3A;text-decoration:underline;font-family:'Geist Mono',ui-monospace,SFMono-Regular,Menlo,monospace;">+44 7853404534</a>
                          </p>
                        </td>
                      </tr>

                      <!-- Dark Footer -->
                      <tr>
                        <td style="background-color:#131209;padding:20px 24px;text-align:center;font-size:12px;line-height:18px;color:#A8A59E;">
                          MyHosh Salisbury &middot; <a href="https://myhosh-salisbury.co.uk" style="color:#E5CFA3;text-decoration:none;">myhosh-salisbury.co.uk</a>
                        </td>
                      </tr>

                    </table>
                  </td>
                </tr>
              </table>
            </body>
            </html>
            """;

        String htmlBody = template
                .replace("{{NAME}}", safeName)
                .replace("{{REF}}", refCode)
                .replace("{{DATE}}", safeDate)
                .replace("{{TIME}}", safeTime)

                .replace("{{GUESTS}}", guestsLabel);



        CompletableFuture.runAsync(() -> {
            try {
                MimeMessage mimeMessage = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
                helper.setFrom(fromEmail);
                helper.setReplyTo(replyToEmail);
                helper.setTo(to.trim());
                helper.setSubject("Reservation confirmed (" + refCode + ") | MyHosh Salisbury");
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

        String template = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
              <meta charset="UTF-8" />
              <meta name="viewport" content="width=device-width, initial-scale=1.0" />
              <title>Reservation cancelled | MyHosh Salisbury</title>
              <link href="https://fonts.googleapis.com/css2?family=Geist+Mono:wght@500;600&family=Manrope:wght@400;500;600;700&display=swap" rel="stylesheet" />
            </head>
            <body style="margin:0;padding:0;background-color:#F4EFE4;font-family:'Manrope',-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;color:#1B1A17;-webkit-font-smoothing:antialiased;">
              <div style="display:none;max-height:0;overflow:hidden;opacity:0;mso-hide:all;">
                Your reservation at MyHosh Salisbury on {{DATE}} at {{TIME}} has been cancelled.
              </div>

              <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="background-color:#F4EFE4;padding:32px 16px;">
                <tr>
                  <td align="center">
                    <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="max-width:560px;background-color:#FFFFFF;border:1px solid #E2D9C5;border-radius:16px;overflow:hidden;">

                      <tr>
                        <td style="background-color:#131209;padding:28px 24px;text-align:center;">
                          <div style="font-size:20px;line-height:28px;font-weight:600;letter-spacing:0.18em;color:#FBF6EA;">MYHOSH</div>
                          <div style="margin-top:4px;font-size:12px;line-height:16px;font-weight:500;letter-spacing:0.12em;text-transform:uppercase;color:#E5CFA3;">Salisbury</div>
                        </td>
                      </tr>

                      <tr>
                        <td style="padding:32px 24px;">
                          <div style="display:inline-block;padding:4px 12px;border-radius:9999px;background-color:#FBECE9;border:1px solid #E6B5AC;font-size:12px;line-height:16px;font-weight:600;color:#9A2B1D;margin-bottom:16px;">
                            Reservation cancelled
                          </div>

                          <h1 style="margin:0 0 12px 0;font-size:24px;line-height:32px;font-weight:600;color:#1B1A17;">
                            Your table booking has been cancelled
                          </h1>
                          <p style="margin:0 0 20px 0;font-size:15px;line-height:24px;color:#5C584F;">
                            Hello {{NAME}}, this confirms that your reservation on <strong style="color:#1B1A17;font-family:'Geist Mono',ui-monospace,monospace;">{{DATE}}</strong> at <strong style="color:#1B1A17;font-family:'Geist Mono',ui-monospace,monospace;">{{TIME}}</strong> has been cancelled.
                          </p>

                          <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="background-color:#FBF6EA;border:1px solid #E5D7BA;border-radius:12px;margin-bottom:24px;">
                            <tr>
                              <td style="padding:16px;font-size:14px;line-height:20px;color:#5C584F;">
                                <strong style="color:#1B1A17;font-weight:600;">Reason:</strong> {{REASON}}
                              </td>
                            </tr>
                          </table>

                          <table role="presentation" cellpadding="0" cellspacing="0" border="0" style="margin:0 0 24px 0;">
                            <tr>
                              <td style="border-radius:8px;background-color:#0F4D3A;">
                                <a href="https://myhosh-salisbury.co.uk/#reserve" style="display:inline-block;padding:12px 20px;font-size:14px;line-height:20px;font-weight:600;color:#FBF6EA;text-decoration:none;">
                                  Book another table
                                </a>
                              </td>
                            </tr>
                          </table>

                          <p style="margin:0;font-size:14px;line-height:22px;color:#5C584F;">
                            Need help with a new date? Call us on <a href="tel:+447853404534" style="color:#0F4D3A;text-decoration:underline;font-family:'Geist Mono',ui-monospace,monospace;">+44 7853404534</a>.
                          </p>
                        </td>
                      </tr>

                      <tr>
                        <td style="background-color:#131209;padding:20px 24px;text-align:center;font-size:12px;line-height:18px;color:#A8A59E;">
                          MyHosh Salisbury &middot; <a href="https://myhosh-salisbury.co.uk" style="color:#E5CFA3;text-decoration:none;">myhosh-salisbury.co.uk</a>
                        </td>
                      </tr>

                    </table>
                  </td>
                </tr>
              </table>
            </body>
            </html>
            """;

        String htmlBody = template
                .replace("{{NAME}}", safeName)
                .replace("{{DATE}}", safeDate)
                .replace("{{TIME}}", safeTime)
                .replace("{{REASON}}", safeReason);

        CompletableFuture.runAsync(() -> {
            try {
                MimeMessage mimeMessage = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
                helper.setFrom(fromEmail);
                helper.setReplyTo(replyToEmail);
                helper.setTo(to.trim());
                helper.setSubject("Reservation cancelled | MyHosh Salisbury");
                helper.setText(htmlBody, true);
                mailSender.send(mimeMessage);
                System.out.println("HTML cancellation email sent to " + to);
            } catch (Exception e) {
                System.err.println("Failed to send cancellation email: " + e.getMessage());
            }
        });
    }

    public void sendReviewRequestEmail(String to, String customerName) {
        if (!isValidRecipient(to)) {
            System.out.println("Skipping review email: optional or internal address (" + to + ")");
            return;
        }

        String safeName = HtmlUtils.htmlEscape(customerName != null && !customerName.isBlank() ? customerName : "Guest");
        String reviewUrl = "https://search.google.com/local/writereview?placeid=ChIJid9eqcjrc0gRejtjRW2CRaE";

        String template = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
              <meta charset="UTF-8" />
              <meta name="viewport" content="width=device-width, initial-scale=1.0" />
              <title>Thank you for dining with us | MyHosh Salisbury</title>
              <link href="https://fonts.googleapis.com/css2?family=Manrope:wght@400;500;600;700&display=swap" rel="stylesheet" />
            </head>
            <body style="margin:0;padding:0;background-color:#F4EFE4;font-family:'Manrope',-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;color:#1B1A17;-webkit-font-smoothing:antialiased;">
              <div style="display:none;max-height:0;overflow:hidden;opacity:0;mso-hide:all;">
                Thank you for visiting MyHosh Salisbury. We would love to hear about your meal.
              </div>

              <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="background-color:#F4EFE4;padding:32px 16px;">
                <tr>
                  <td align="center">
                    <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="max-width:560px;background-color:#FFFFFF;border:1px solid #E2D9C5;border-radius:16px;overflow:hidden;">

                      <tr>
                        <td style="background-color:#0A2F25;padding:32px 24px;text-align:center;">
                          <div style="font-size:20px;line-height:28px;font-weight:600;letter-spacing:0.18em;color:#FBF6EA;">MYHOSH</div>
                          <div style="margin-top:4px;font-size:12px;line-height:16px;font-weight:500;letter-spacing:0.12em;text-transform:uppercase;color:#E5CFA3;">99 Fisherton Street, Salisbury</div>
                        </td>
                      </tr>

                      <tr>
                        <td style="padding:32px 24px;">
                          <h1 style="margin:0 0 12px 0;font-size:24px;line-height:32px;font-weight:600;color:#1B1A17;letter-spacing:-0.015em;">
                            Thank you for dining with us, {{NAME}}
                          </h1>
                          <p style="margin:0 0 16px 0;font-size:15px;line-height:24px;color:#5C584F;">
                            We hope you enjoyed your meal at MyHosh Salisbury. As an independent local restaurant, honest guest feedback means a great deal to our kitchen and team.
                          </p>
                          <p style="margin:0 0 24px 0;font-size:15px;line-height:24px;color:#5C584F;">
                            If you have a spare minute, we would be grateful if you shared what you enjoyed most from our charcoal grill, meze, or service on Google.
                          </p>

                          <table role="presentation" cellpadding="0" cellspacing="0" border="0" style="margin:0 0 24px 0;">
                            <tr>
                              <td style="border-radius:8px;background-color:#C89D54;">
                                <a href="{{REVIEW_URL}}" style="display:inline-block;padding:12px 22px;font-size:14px;line-height:20px;font-weight:600;color:#1B1A17;text-decoration:none;">
                                  Leave a Google review
                                </a>
                              </td>
                            </tr>
                          </table>

                          <p style="margin:0;font-size:13px;line-height:20px;color:#7A756A;">
                            You received this one time message because you opted in when booking at MyHosh Salisbury (99 Fisherton Street, Salisbury, SP2 7SP). We will not add you to any mailing list. To withdraw consent or request data erasure under UK GDPR, reply to this email or call +44 7853 404534.
                          </p>
                        </td>
                      </tr>

                      <tr>
                        <td style="background-color:#131209;padding:20px 24px;text-align:center;font-size:12px;line-height:18px;color:#A8A59E;">
                          MyHosh Salisbury &middot; 99 Fisherton Street, Salisbury SP2 7SP &middot; <a href="https://myhosh-salisbury.co.uk" style="color:#E5CFA3;text-decoration:none;">myhosh-salisbury.co.uk</a>
                        </td>
                      </tr>

                    </table>
                  </td>
                </tr>
              </table>
            </body>
            </html>
            """;

        String htmlBody = template
                .replace("{{NAME}}", safeName)
                .replace("{{REVIEW_URL}}", reviewUrl);

        CompletableFuture.runAsync(() -> {
            try {
                MimeMessage mimeMessage = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
                helper.setFrom(fromEmail);
                helper.setReplyTo(replyToEmail);
                helper.setTo(to.trim());
                helper.setSubject("Thank you for dining at MyHosh Salisbury");
                helper.setText(htmlBody, true);
                mailSender.send(mimeMessage);
                System.out.println("Post-dining review email sent to " + to);
            } catch (Exception e) {
                System.err.println("Failed to send review email: " + e.getMessage());
            }
        });
    }
}
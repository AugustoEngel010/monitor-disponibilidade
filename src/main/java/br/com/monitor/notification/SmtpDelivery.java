package br.com.monitor.notification;

import java.net.ConnectException;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.Date;
import jakarta.mail.MessagingException;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import org.eclipse.angus.mail.smtp.SMTPAddressFailedException;
import org.eclipse.angus.mail.smtp.SMTPSendFailedException;
import org.eclipse.angus.mail.util.MailConnectException;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.transaction.support.TransactionSynchronizationManager;

public class SmtpDelivery {
    private final JavaMailSenderImpl sender;
    private final String from;

    public SmtpDelivery(JavaMailSender sender, String from) {
        this.sender = (JavaMailSenderImpl) sender;
        this.from = from;
    }

    public NotificationStore.Result send(NotificationDelivery delivery) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("SMTP cannot run inside a transaction");
        }
        Transport transport = null;
        boolean connected = false;
        try {
            var message = sender.createMimeMessage();
            message.setFrom(new InternetAddress(from));
            message.setRecipients(jakarta.mail.Message.RecipientType.TO, delivery.recipient());
            message.setSubject("[" + delivery.type() + "] " + delivery.monitorName(), "UTF-8");
            message.setSentDate(Date.from(delivery.recoveredAt() == null
                    ? delivery.downConfirmedAt() : delivery.recoveredAt()));
            message.setText(body(delivery), "UTF-8");
            message.saveChanges();
            message.setHeader("Message-ID", delivery.messageId());
            transport = sender.getSession().getTransport("smtp");
            transport.connect(sender.getHost(), sender.getPort(), sender.getUsername(), sender.getPassword());
            connected = true;
            transport.sendMessage(message, message.getAllRecipients());
            return NotificationStore.Result.ACCEPTED;
        } catch (MailException error) {
            return NotificationStore.Result.FAILED;
        } catch (MessagingException error) {
            return !connected || knownFailure(error) ? NotificationStore.Result.FAILED
                    : NotificationStore.Result.UNCERTAIN;
        } finally {
            if (transport != null) {
                try { transport.close(); }
                catch (MessagingException ignored) { /* QUIT cannot undo the DATA acceptance already observed. */ }
            }
        }
    }

    private static boolean knownFailure(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof MailAuthenticationException || cause instanceof ConnectException
                    || cause instanceof UnknownHostException || cause instanceof MailConnectException
                    || cause instanceof SMTPAddressFailedException) { return true; }
            if (cause instanceof SMTPSendFailedException smtp && smtp.getReturnCode() >= 400) { return true; }
            if (cause instanceof MessagingException messaging && messaging.getNextException() != null
                    && messaging.getNextException() != cause.getCause()
                    && knownFailure(messaging.getNextException())) { return true; }
        }
        return false;
    }

    private static String body(NotificationDelivery delivery) {
        String text = "Monitor: " + delivery.monitorName() + "\nURL: " + delivery.monitorUrl()
                + "\nTransição: " + delivery.type() + "\nIncidente: " + delivery.incidentId()
                + "\nPrimeira falha: " + delivery.firstFailureAt()
                + "\nQueda confirmada: " + delivery.downConfirmedAt() + "\n";
        if (delivery.type().equals("RECOVERY")) {
            text += "Início da recuperação: " + delivery.recoveryStartedAt()
                    + "\nRecuperação confirmada: " + delivery.recoveredAt()
                    + "\nDuração estimada: "
                    + Duration.between(delivery.firstFailureAt(), delivery.recoveryStartedAt()).getSeconds()
                    + " segundos (inclui pausas e lacunas; não prova indisponibilidade contínua).\n";
        }
        return text;
    }
}

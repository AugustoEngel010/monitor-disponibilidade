package br.com.monitor.notification;

import jakarta.mail.internet.InternetAddress;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

@Configuration
public class NotificationConfiguration {
    @Bean
    public JavaMailSender notificationMailSender(Environment environment) {
        try {
            var sender = new JavaMailSenderImpl();
            String host = environment.getProperty("monitor.smtp.host", "smtp");
            if (host.isBlank() || host.chars().anyMatch(Character::isISOControl)) { throw invalid(); }
            sender.setHost(host);
            sender.setPort(number(environment, "port", 1025, 65535));
            sender.setUsername(environment.getProperty("monitor.smtp.username", ""));
            sender.setPassword(environment.getProperty("monitor.smtp.password", ""));
            sender.setDefaultEncoding("UTF-8");
            boolean auth = bool(environment, "auth");
            boolean tls = bool(environment, "starttls");
            boolean ssl = bool(environment, "ssl");
            if (tls && ssl || auth && (sender.getUsername().isBlank() || sender.getPassword().isBlank())) {
                throw invalid();
            }
            var properties = sender.getJavaMailProperties();
            properties.setProperty("mail.smtp.auth", Boolean.toString(auth));
            properties.setProperty("mail.smtp.starttls.enable", Boolean.toString(tls));
            properties.setProperty("mail.smtp.starttls.required", Boolean.toString(tls));
            properties.setProperty("mail.smtp.ssl.enable", Boolean.toString(ssl));
            properties.setProperty("mail.smtp.ssl.checkserveridentity", "true");
            properties.setProperty("mail.smtp.connectiontimeout",
                    Integer.toString(number(environment, "connect-timeout-ms", 5000, 60000)));
            properties.setProperty("mail.smtp.timeout",
                    Integer.toString(number(environment, "read-timeout-ms", 3000, 60000)));
            properties.setProperty("mail.smtp.writetimeout",
                    Integer.toString(number(environment, "write-timeout-ms", 5000, 60000)));
            properties.setProperty("mail.smtp.quitwait", "false");
            return sender;
        } catch (RuntimeException error) { throw invalid(); }
    }

    @Bean
    public SmtpDelivery smtpDelivery(JavaMailSender sender, Environment environment) {
        String from = environment.getProperty("monitor.smtp.from", "monitor@example.org");
        try {
            var address = new InternetAddress(from, true);
            address.validate();
            if (address.getPersonal() != null || !address.getAddress().equals(from)
                    || from.chars().anyMatch(Character::isISOControl)) { throw invalid(); }
            return new SmtpDelivery(sender, from);
        } catch (Exception error) { throw invalid(); }
    }

    private static int number(Environment environment, String field, int fallback, int max) {
        int value = Integer.parseInt(environment.getProperty("monitor.smtp." + field, Integer.toString(fallback)));
        if (value < 1 || value > max) { throw invalid(); }
        return value;
    }

    private static boolean bool(Environment environment, String field) {
        String value = environment.getProperty("monitor.smtp." + field, "false");
        if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) { throw invalid(); }
        return Boolean.parseBoolean(value);
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Invalid SMTP configuration");
    }
}

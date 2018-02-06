package nirvana;

import com.dumbster.smtp.SimpleSmtpServer;
import com.dumbster.smtp.SmtpMessage;

import java.io.IOException;

public class NirvanaSmtp {

    public static void main(String[] args) {
        try (SimpleSmtpServer server = SimpleSmtpServer.start(1025)) {

            System.out.println(NirvanaSmtp.class.getSimpleName() + " is waiting for emails...");

            while (true) {

                SmtpMessage smtpMessage = server.RECEIVED_MAIL.poll();
                if (smtpMessage != null) {
                    String subject = smtpMessage.getHeaderValue("Subject");
                    String from = smtpMessage.getHeaderValue("From");
                    String to = smtpMessage.getHeaderValue("To");
                    if (subject != null) {
                        System.out.println("received '" + subject + "' from: " + from + " to:" + to);
                    }
                }

                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}

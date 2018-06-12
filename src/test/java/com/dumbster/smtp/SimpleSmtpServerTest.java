/*
 * Dumbster - a dummy SMTP server
 * Copyright 2016 Joachim Nicolay
 * Copyright 2004 Jason Paul Kitchen
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.dumbster.smtp;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.util.Date;
import java.util.Properties;
import java.util.Queue;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SimpleSmtpServerTest {

    private SimpleSmtpServer server;

    @Before
    public void setUp() throws Exception {
        server = SimpleSmtpServer.start(SimpleSmtpServer.DEFAULT_SMTP_PORT + ThreadLocalRandom.current().nextInt(1000, 2000));
    }

    @After
    public void tearDown() {
        server.stop();
    }

    @Test
    public void testSend() throws MessagingException {
        sendMessage(server.getPort(), "sender@here.com", "Test", "Test Body", "receiver@there.com");

        Queue<SmtpMessage> emails = server.getReceivedEmails();
        assertEquals(1, emails.size());
        SmtpMessage email = emails.poll();
        assertEquals("Test", email.getHeaderValue("Subject"));
        assertEquals("Test Body\n", email.getBody());
        assertTrue(email.getHeaderNames().contains("Date"));
        assertTrue(email.getHeaderNames().contains("From"));
        assertTrue(email.getHeaderNames().contains("To"));
        assertTrue(email.getHeaderNames().contains("Subject"));
        assertTrue(email.getHeaderValues("To").contains("receiver@there.com"));
        assertEquals("receiver@there.com", email.getHeaderValue("To"));
    }

    @Test
    public void testSendAndReset() throws MessagingException {
        sendMessage(server.getPort(), "sender@here.com", "Test", "Test Body", "receiver@there.com");
        assertEquals(1, server.getReceivedEmails().size());

        server.reset();
        assertEquals(0, server.getReceivedEmails().size());

        sendMessage(server.getPort(), "sender@here.com", "Test", "Test Body", "receiver@there.com");
        assertEquals(1, server.getReceivedEmails().size());
    }

    @Test
    public void testSendMessageWithCR() throws MessagingException {
        String bodyWithCR = "\n\nKeep these pesky\ncarriage returns\n\n";
        sendMessage(server.getPort(), "sender@hereagain.com", "CRTest", bodyWithCR, "receivingagain@there.com");

        Queue<SmtpMessage> emails = server.getReceivedEmails();
        assertEquals(1, emails.size());
        SmtpMessage email = emails.poll();
        assertEquals(bodyWithCR + "\n", email.getBody());
    }

    @Test
	public void testSendMsgWithHeaderContinuation() throws Exception {
        Properties mailProps = getMailProperties(server.getPort());
        Session session = Session.getInstance(mailProps, null);

        MimeMessage msg = createMessage(session, "sender@hereagain.com", "receivingagain@there.com",
                                        "headerWithContinuation", "body");
        msg.addHeaderLine("X-SomeHeader: first part ");
        msg.addHeaderLine("    second part");
        Transport.send(msg);

        Queue<SmtpMessage> emails = server.getReceivedEmails();
        assertEquals(emails.size(), 1);
        SmtpMessage email = emails.poll();
        assertEquals(email.getHeaderValue("X-SomeHeader"), "first part second part");
        assertEquals(email.getBody(), "body\n");
    }

    @Test
    public void testSendTwoMessagesSameConnection() throws MessagingException {
        String serverHost = "localhost";
        String from = "sender@whatever.com";
        String to = "receiver@home.com";

        Properties mailProps = getMailProperties(server.getPort());
        Session session = Session.getInstance(mailProps, null);

        MimeMessage[] mimeMessages = new MimeMessage[2];
        mimeMessages[0] = createMessage(session, from, to, "Doodle1", "Bug1");
        mimeMessages[1] = createMessage(session, from, to, "Doodle2", "Bug2");

        Transport transport = session.getTransport("smtp");
        transport.connect(serverHost, server.getPort(), null, null);

        try {
            for (MimeMessage mimeMessage : mimeMessages)
                transport.sendMessage(mimeMessage, mimeMessage.getAllRecipients());
        } finally {
            transport.close();
        }

        assertEquals(2, server.getReceivedEmails().size());
    }

    @Test
    public void testSendTwoMsgsWithLogin() throws Exception {
        String serverHost = "localhost";
        String from = "sender@here.com";
        String[] to = {"receiver@there.com", "dimiter.bakardjiev@musala.com"};
        String subject = "Test";
        String body = "Test Body";

        Properties props = new Properties();
        props.setProperty("mail.smtp.ehlo", "false");
        props.setProperty("mail.smtp.auth", "true");
        props.setProperty("mail.smtp.auth.mechanisms", "PLAIN");
        props.setProperty("mail.smtp.sendpartial", "true");
        props.setProperty("mail.smtp.submitter", "user");

        Session session = Session.getDefaultInstance(props, null);

        Message msg = new MimeMessage(session);
        msg.setFrom(InternetAddress.parse(from, false)[0]);
        msg.setSubject(subject);
        msg.setText(body);
        msg.setHeader("X-Mailer", "musala");
        msg.setSentDate(new Date());
        msg.saveChanges();

        Transport transport = session.getTransport("smtp");
        transport.connect(serverHost, server.getPort(), "user", "password");
        try {
            for (String t : to)
                transport.sendMessage(msg, InternetAddress.parse(t, false));
        } finally {
            transport.close();
        }

        Queue<SmtpMessage> emails = this.server.getReceivedEmails();
        assertEquals(2, emails.size());
        SmtpMessage email = emails.poll();
        assertEquals(subject, email.getHeaderValue("Subject"));
        assertEquals(body + "\n", email.getBody());
    }

    private Properties getMailProperties(int port) {
        Properties mailProps = new Properties();
        mailProps.setProperty("mail.smtp.host", "localhost");
        mailProps.setProperty("mail.smtp.port", String.valueOf(port));
        mailProps.setProperty("mail.smtp.sendpartial", "true");
        mailProps.setProperty("mail.smtp.auth", "false");
        mailProps.setProperty("mail.smtp.ehlo", "false");
        return mailProps;
    }


    private void sendMessage(int port, String from, String subject, String body, String to) throws MessagingException {
        Properties mailProps = getMailProperties(port);
        Session session = Session.getInstance(mailProps, null);
        session.setDebug(true);

        MimeMessage msg = createMessage(session, from, to, subject, body);
        Transport.send(msg);
    }

    private MimeMessage createMessage(Session session, String from, String to, String subject, String body)
        throws MessagingException {
        MimeMessage msg = new MimeMessage(session);
        msg.setFrom(new InternetAddress(from));
        msg.setSubject(subject);
        msg.setSentDate(new Date());
        msg.setText(body);
        msg.setRecipient(Message.RecipientType.TO, new InternetAddress(to));
        return msg;
    }

}

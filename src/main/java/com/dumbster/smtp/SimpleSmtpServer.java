/*
 * Dumbster - a dummy SMTP server
 * Copyright 2018 Christian Meyer
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

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;
import java.util.Scanner;
import java.util.concurrent.ConcurrentLinkedQueue;

import static java.nio.charset.StandardCharsets.ISO_8859_1;

/**
 * Dummy SMTP server for testing purposes.
 */
@Slf4j
public final class SimpleSmtpServer implements AutoCloseable {

    /**
     * Default SMTP port is 25.
     */
    public static final int DEFAULT_SMTP_PORT = 25;

    /**
     * pick any free port.
     */
    public static final int AUTO_SMTP_PORT = 0;

    /**
     * When stopping wait this long for any still ongoing transmission
     */
    private static final int STOP_TIMEOUT = 20000;

    private static final String CRLF = "\r\n";

    /**
     * Store and offer received emails in a {@link Queue} object.
     * Implementation uses a unbounded and thread-safe {@link ConcurrentLinkedQueue}
     */
    private final Queue<SmtpMessage> receivedEmails;

    /**
     * The server socket this server listens to.
     */
    private final ServerSocket serverSocket;

    /**
     * Thread that does the work.
     */
    private final Thread workerThread;

    /**
     * Indicates the server thread that it should stop
     */
    private volatile boolean stopped = false;

    /**
     * private constructor because factory method {@link #start(int)} better indicates that
     * the created server is already running
     *
     * @param serverSocket socket to listen on
     */
    private SimpleSmtpServer(ServerSocket serverSocket) {
        this.receivedEmails = new ConcurrentLinkedQueue<>();
        this.serverSocket = serverSocket;
        this.workerThread = new Thread(this::performWork);
        this.workerThread.start();
    }

    /**
     * Creates an instance of a started SimpleSmtpServer listening on port {@value SimpleSmtpServer#DEFAULT_SMTP_PORT}.
     *
     * @return a reference to the running SMTP server
     *
     * @throws IOException when listening on the socket causes one
     */
    public static SimpleSmtpServer start() throws IOException {
        return start(DEFAULT_SMTP_PORT);
    }

    /**
     * Creates an instance of a started SimpleSmtpServer.
     *
     * @param port port number the server should listen to
     *
     * @return a reference to the running SMTP server
     *
     * @throws IOException when listening on the socket causes one
     */
    public static SimpleSmtpServer start(int port) throws IOException {
        return new SimpleSmtpServer(new ServerSocket(Math.max(port, AUTO_SMTP_PORT)));
    }

    /**
     * Handle an SMTP transaction, i.e. all activity between initial connect and QUIT command.
     *
     * @param out output stream
     * @param input input stream
     *
     * @return List of SmtpMessage
     */
    private static List<SmtpMessage> handleTransaction(PrintWriter out, Iterator<String> input) {
        // Initialize the state machine
        SmtpState smtpState = SmtpState.CONNECT;
        SmtpRequest smtpRequest = new SmtpRequest(SmtpActionType.CONNECT, "", smtpState);

        // Execute the connection request
        SmtpResponse smtpResponse = smtpRequest.execute();

        // Send initial response
        sendResponse(out, smtpResponse);
        smtpState = smtpResponse.getNextState();

        List<SmtpMessage> msgList = new ArrayList<>();
        SmtpMessage msg = new SmtpMessage();

        while (smtpState != SmtpState.CONNECT) {
            String line = input.next();

            if (line == null)
                break;

            log.debug("client response: {}", line);

            // Create request from client input and current state
            SmtpRequest request = SmtpRequest.createRequest(line, smtpState);
            // Execute request and create response object
            SmtpResponse response = request.execute();
            // Move to next internal state
            smtpState = response.getNextState();
            // Send response to client
            sendResponse(out, response);
            // Store input in message
            msg.store(response, request.params);

            // If message reception is complete save it
            if (smtpState == SmtpState.QUIT) {
                msgList.add(msg);
                msg = new SmtpMessage();
            }
        }

        return Collections.unmodifiableList(msgList);
    }

    /**
     * Send response to client.
     *
     * @param out socket output stream
     * @param smtpResponse response object
     */
    private static void sendResponse(PrintWriter out, SmtpResponse smtpResponse) {
        int code = smtpResponse.getCode();
        if (code > 0) {
            String message = smtpResponse.getMessage();

            log.debug("Server response: {} {}{}", code, message, CRLF);

            out.print(code + " " + message + CRLF);
            out.flush();
        }
    }

    /**
     * @return the port the server is listening on
     */
    public int getPort() {
        return serverSocket.getLocalPort();
    }

    /**
     * All received email stored in a thread-safe queue.
     * The returned object is the backing object of the received data.
     * Deleting or modifying data in the returned object is a destructive operation.
     *
     * @return all received email stored in a thread-safe queue
     */
    public Queue<SmtpMessage> getReceivedEmails() {
        return receivedEmails;
    }

    /**
     * All received email copied in a {@link ArrayList} to support the original semantics.
     * Modifying this list does not change the backing queue {@link #getReceivedEmails()}.
     *
     * @return all received email copied in a Array List
     */
    public List<SmtpMessage> getReceivedEmailCopy() {
        return new ArrayList<>(receivedEmails);
    }

    /**
     * forgets all received emails
     */
    public void reset() {
        getReceivedEmails().clear();
    }

    /**
     * Stops the server. Server is shutdown after processing of the current request is complete.
     */
    public void stop() {
        if (stopped)
            return;

        closeSocket();
        // and block until worker is finished
        try {
            workerThread.join(STOP_TIMEOUT);
        } catch (InterruptedException e) {
            log.warn("interrupted when waiting for worker thread to finish", e);
        }
    }

    /**
     * synonym for {@link #stop()}
     */
    @Override
    public void close() {
        stop();
    }

    /**
     * Main loop of the SMTP server.
     */
    private void performWork() {
        log.info("listening on port " + serverSocket.getLocalPort());
        try {
            while (!stopped) { // Server: loop until stopped
                try (Socket socket = serverSocket.accept();
                     Scanner input = new Scanner(new InputStreamReader(socket.getInputStream(), ISO_8859_1))
                                         .useDelimiter(CRLF);
                     PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), ISO_8859_1))) {

                    synchronized (receivedEmails) {
                        /*
                         * We synchronize over the handle method and the queue update because the client call completes inside
                         * the handle method and we should prevent the client from reading the list until we've updated it.
                         */
                        receivedEmails.addAll(handleTransaction(out, input));
                    }
                }
            }
        } catch (Exception e) {
            closeSocket();
        }
    }

    private void closeSocket() {
        if (!stopped) {
            try {
                serverSocket.close();
                stopped = true;
            } catch (IOException ex) {
                log.error("problem when closing server socket", ex);
            }
        }
    }

}

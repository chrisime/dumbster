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

/**
 * SMTP server state.
 */
public enum SmtpState {

    /**
     * CONNECT state: waiting for a client connection.
     */
    CONNECT,

    /**
     * GREET state: waiting for a ELHO message.
     */
    GREET,

    /**
     * Intermediate state to tell the client about the supported authentications.
     */
    GREET_AUTH,

    /**
     * AUTH PLAIN state: waiting for authentication
     */
    AUTH_PLAIN,

    /**
     * Waiting for the client to enter credentials.
     */
    CREDENTIALS,

    /**
     * MAIL state: waiting for the MAIL FROM: command.
     */
    MAIL,

    /**
     * RCPT state: waiting for a RCPT &lt;email address&gt; command.
     */
    RCPT,

    /**
     * DATA_HDR state: waiting for headers.
     */
    DATA_HDR,

    /**
     * DATA_BODY state: processing body text.
     */
    DATA_BODY,

    /**
     * QUIT state: end of client transmission.
     */
    QUIT

}

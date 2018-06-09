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

import lombok.AllArgsConstructor;

/**
 * Contains an SMTP client request. Handles state transitions using the following state transition table.
 * <PRE>
 * -----------+-------------------------------------------------------------------------------------------------
 * |                                 State
 * | Action   +-------------+-----------+-----------+--------------+---------------+---------------+------------
 * |          | CONNECT     | GREET     | MAIL      | RCPT         | DATA_HDR      | DATA_BODY     | QUIT
 * -----------+-------------+-----------+-----------+--------------+---------------+---------------+------------
 * connect    | 220/GREET   | 503/GREET | 503/MAIL  | 503/RCPT     | 503/DATA_HDR  | 503/DATA_BODY | 503/QUIT
 * ehlo       | 503/CONNECT | 250/MAIL  | 503/MAIL  | 503/RCPT     | 503/DATA_HDR  | 503/DATA_BODY | 503/QUIT
 * mail       | 503/CONNECT | 503/GREET | 250/RCPT  | 503/RCPT     | 503/DATA_HDR  | 503/DATA_BODY | 250/RCPT
 * rcpt       | 503/CONNECT | 503/GREET | 503/MAIL  | 250/RCPT     | 503/DATA_HDR  | 503/DATA_BODY | 503/QUIT
 * data       | 503/CONNECT | 503/GREET | 503/MAIL  | 354/DATA_HDR | 503/DATA_HDR  | 503/DATA_BODY | 503/QUIT
 * data_end   | 503/CONNECT | 503/GREET | 503/MAIL  | 503/RCPT     | 250/QUIT      | 250/QUIT      | 503/QUIT
 * unrecog    | 500/CONNECT | 500/GREET | 500/MAIL  | 500/RCPT     | ---/DATA_HDR  | ---/DATA_BODY | 500/QUIT
 * quit       | 503/CONNECT | 503/GREET | 503/MAIL  | 503/RCPT     | 503/DATA_HDR  | 503/DATA_BODY | 250/CONNECT
 * blank_line | 503/CONNECT | 503/GREET | 503/MAIL  | 503/RCPT     | ---/DATA_BODY | ---/DATA_BODY | 503/QUIT
 * rset       | 250/GREET   | 250/GREET | 250/GREET | 250/GREET    | 250/GREET     | 250/GREET     | 250/GREET
 * vrfy       | 252/CONNECT | 252/GREET | 252/MAIL  | 252/RCPT     | 252/DATA_HDR  | 252/DATA_BODY | 252/QUIT
 * expn       | 252/CONNECT | 252/GREET | 252/MAIL  | 252/RCPT     | 252/DATA_HDR  | 252/DATA_BODY | 252/QUIT
 * help       | 211/CONNECT | 211/GREET | 211/MAIL  | 211/RCPT     | 211/DATA_HDR  | 211/DATA_BODY | 211/QUIT
 * noop       | 250/CONNECT | 250/GREET | 250/MAIL  | 250/RCPT     | 250|DATA_HDR  | 250/DATA_BODY | 250/QUIT
 * </PRE>
 */
@AllArgsConstructor
class SmtpRequest {

    /**
     * SMTP action/command received from client.
     */
    private final SmtpActionType action;

    /**
     * Additional information passed from the client with the SMTP action.
     */
    final String params;

    /**
     * Current state of the SMTP state table.
     */
    private final SmtpState state;

    /**
     * Create an SMTP request object given a line of the input stream from the client and the current internal state.
     *
     * @param s line of input
     * @param state current state
     *
     * @return a populated SmtpRequest object
     */
    static SmtpRequest createRequest(String s, SmtpState state) {
        SmtpActionType action;
        String params = null;

        if (state == SmtpState.DATA_HDR) {
            if (s.equals(".")) {
                action = SmtpActionType.DATA_END;
            } else if (s.length() < 1) {
                action = SmtpActionType.BLANK_LINE;
            } else {
                action = SmtpActionType.UNRECOG;
                params = s;
            }
        } else if (state == SmtpState.DATA_BODY) {
            if (s.equals(".")) {
                action = SmtpActionType.DATA_END;
            } else {
                action = SmtpActionType.UNRECOG;
                params = s.length() < 1 ? "\n" : s;
            }
        } else {
            String su = s.toUpperCase();
            if (su.startsWith("HELO ")) {
                action = SmtpActionType.HELO;
                params = s.substring(5);
            } else if (su.startsWith("EHLO ")) {
                action = SmtpActionType.EHLO;
                params = s.substring(5);
            } else if (su.startsWith("MAIL FROM:")) {
                action = SmtpActionType.MAIL;
                params = s.substring(10);
            } else if (su.startsWith("RCPT TO:")) {
                action = SmtpActionType.RCPT;
                params = s.substring(8);
            } else if (su.startsWith("DATA")) {
                action = SmtpActionType.DATA;
            } else if (su.startsWith("QUIT")) {
                action = SmtpActionType.QUIT;
            } else if (su.startsWith("RSET")) {
                action = SmtpActionType.RSET;
            } else if (su.startsWith("NOOP")) {
                action = SmtpActionType.NOOP;
            } else if (su.startsWith("EXPN")) {
                action = SmtpActionType.EXPN;
            } else if (su.startsWith("VRFY")) {
                action = SmtpActionType.VRFY;
            } else if (su.startsWith("HELP")) {
                action = SmtpActionType.HELP;
            } else {
                action = SmtpActionType.UNRECOG;
            }
        }

        return new SmtpRequest(action, params, state);
    }

    /**
     * Execute the SMTP request returning a response. This method models the state transition table for the SMTP server.
     *
     * @return reponse to the request
     */
    SmtpResponse execute() {
        SmtpResponse response;
        if (action.isStateless()) {
            switch (action) {
                case EXPN:
                case VRFY:
                    response = new SmtpResponse(252, "Not supported", this.state);
                    break;
                case HELP:
                    response = new SmtpResponse(211, "No help available", this.state);
                    break;
                case NOOP:
                    response = new SmtpResponse(250, "OK", this.state);
                    break;
                case RSET:
                    response = new SmtpResponse(250, "OK", SmtpState.GREET);
                    break;
                default:
                    response = new SmtpResponse(500, "Command not recognized", this.state);
                    break;
            }
        } else { // Stateful commands
            switch (action) {
                case CONNECT:
                    if (SmtpState.CONNECT == state) {
                        response = new SmtpResponse(220, "localhost Dumbster SMTP service ready", SmtpState.GREET);
                    } else {
                        response = new SmtpResponse(503, "Bad sequence of commands: " + action, this.state);
                    }
                    break;
                case HELO:
                    if (SmtpState.GREET == state) {
                        response = new SmtpResponse(250, "OK", SmtpState.MAIL);
                    } else {
                        response = new SmtpResponse(503, "Bad sequence of commands: " + action, this.state);
                    }
                    break;
                case MAIL:
                    if (SmtpState.MAIL == state || SmtpState.QUIT == state) {
                        response = new SmtpResponse(250, "OK", SmtpState.RCPT);
                    } else {
                        response = new SmtpResponse(503, "Bad sequence of commands: " + action, this.state);
                    }
                    break;
                case RCPT:
                    if (SmtpState.RCPT == state) {
                        response = new SmtpResponse(250, "OK", this.state);
                    } else {
                        response = new SmtpResponse(503, "Bad sequence of commands: " + action, this.state);
                    }
                    break;
                case DATA:
                    if (SmtpState.RCPT == state) {
                        response =
                            new SmtpResponse(354, "Start mail input; end with <CRLF>.<CRLF>", SmtpState.DATA_HDR);
                    } else {
                        response = new SmtpResponse(503, "Bad sequence of commands: " + action, this.state);
                    }
                    break;
                case UNRECOG:
                    if (SmtpState.DATA_HDR == state || SmtpState.DATA_BODY == state) {
                        response = new SmtpResponse(-1, "", this.state);
                    } else {
                        response = new SmtpResponse(500, "Command not recognized", this.state);
                    }
                    break;
                case DATA_END:
                    if (SmtpState.DATA_HDR == state || SmtpState.DATA_BODY == state) {
                        response = new SmtpResponse(250, "OK", SmtpState.QUIT);
                    } else {
                        response = new SmtpResponse(503, "Bad sequence of commands: " + action, this.state);
                    }
                    break;
                case BLANK_LINE:
                    if (SmtpState.DATA_HDR == state) {
                        response = new SmtpResponse(-1, "", SmtpState.DATA_BODY);
                    } else if (SmtpState.DATA_BODY == state) {
                        response = new SmtpResponse(-1, "", this.state);
                    } else {
                        response = new SmtpResponse(503, "Bad sequence of commands: " + action, this.state);
                    }
                    break;
                case QUIT:
                    if (SmtpState.QUIT == state) {
                        response = new SmtpResponse(221, "localhost Dumbster service closing transmission channel",
                                                    SmtpState.CONNECT);
                    } else {
                        response = new SmtpResponse(503, "Bad sequence of commands: " + action, this.state);
                    }
                    break;
                default:
                    response = new SmtpResponse(500, "Command not recognized", this.state);
                    break;
            }
        }
        return response;
    }

}

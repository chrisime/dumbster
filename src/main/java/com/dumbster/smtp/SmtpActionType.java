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
 * Represents an SMTP action or command.
 */
enum SmtpActionType {

    /**
     * CONNECT action.
     */
    CONNECT(false),

    /**
     * HELO action.
     */
    HELO(false),

    /**
     * EHLO action.
     */
    EHLO(false),

    /**
     * MAIL FROM action.
     */
    MAIL(false),

    /**
     * RCPT action.
     */
    RCPT(false),

    /**
     * DATA action.
     */
    DATA(false),

    /**
     * DATA END "." action.
     */
    DATA_END(false),

    /**
     * QUIT action.
     */
    QUIT(false),

    /**
     * unrecognized action: body text gets this action type.
     */
    UNRECOG(false),

    /**
     * Header/body separator action: separates headers and body text.
     */
    BLANK_LINE(false),

    /**
     * Stateless RSET action.
     */
    RSET(true),

    /**
     * Stateless VRFY action.
     */
    VRFY(true),

    /**
     * Stateless EXPN action.
     */
    EXPN(true),

    /**
     * Stateless HELP action.
     */
    HELP(true),

    /**
     * Stateless NOOP action.
     */
    NOOP(true);

    private boolean stateless;

    SmtpActionType(boolean stateless) {
        this.stateless = stateless;
    }

    /**
     * Indicates whether the action is stateless or not.
     *
     * @return true iff the action is stateless
     */
    boolean isStateless() {
        return stateless;
    }

}

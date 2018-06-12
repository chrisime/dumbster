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

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Container for a complete SMTP message - headers and message body.
 */
public class SmtpMessage {

    /** Headers: Map of List of String hashed on header name. */
    private List<Header> headers;

    /** Message body. */
    private StringBuilder body;

    /** Constructor. Initializes headers Map and body buffer. */
    public SmtpMessage() {
        headers = new ArrayList<>(10);
        body = new StringBuilder();
    }

    /**
     * Update the headers or body depending on the SmtpResponse object and line of input.
     *
     * @param response SmtpResponse object
     * @param params   remainder of input line after SMTP command has been removed
     */
    public void store(SmtpResponse response, String params) {
        if (params == null)
            return;

        if (SmtpState.DATA_HDR == response.getNextState()) {
            if (params.length() > 0 && Character.isWhitespace(params.charAt(0))) {
                appendToLastHeader(params);
            } else {
                int headerNameEnd = params.indexOf(':');
                if (headerNameEnd >= 0) {
                    String name = params.substring(0, headerNameEnd).trim();
                    String value = params.substring(headerNameEnd + 1).trim();
                    addHeader(name, value);
                }
            }
        } else if (SmtpState.DATA_BODY == response.getNextState()) {
            body.append(params);
            body.append('\n');
        }
    }

    /**
     * Get an Iterator over the header names.
     *
     * @return an Iterator over the set of header names (String)
     */
    public Set<String> getHeaderNames() {
        return headers.stream()
                      .map(header -> header.name)
                      .collect(Collectors.toCollection(() -> new LinkedHashSet<>(headers.size())));
    }

    /**
     * Get the value(s) associated with the given header name.
     *
     * @param name header name
     * @return value(s) associated with the header name
     */
    public List<String> getHeaderValues(String name) {
        return headers.stream()
                      .filter(header -> header.name.equals(name))
                      .findFirst()
                      .map(header -> Collections.unmodifiableList(header.values))
                      .orElse(Collections.emptyList());
    }

    /**
     * Get the first values associated with a given header name.
     *
     * @param name header name
     * @return first value associated with the header name
     */
    public String getHeaderValue(String name) {
        List<String> values = getHeaderValues(name);
        return values.isEmpty() ? null : values.get(0);
    }

    /**
     * Get the message body.
     *
     * @return message body
     */
    public String getBody() {
        return body.toString();
    }

    /**
     * Adds a header to the Map.
     *
     * @param name  header name
     * @param value header value
     */
    private void addHeader(String name, String value) {
        headers.stream()
               .filter(header -> header.name.equals(name))
               .findFirst()
               .map(header -> header.values.add(value))
               .orElseGet(() -> {
                   Header h = new Header();
                   h.name = name;
                   h.values = new ArrayList<>(1);
                   headers.add(h);
                   h.values.add(value);
                   return null;
               });
    }

    private void appendToLastHeader(String value) {
        if (headers.isEmpty()) {
            throw new IllegalStateException("found continuation line before first header");
        } else {
            Header header = headers.get(headers.size() - 1);
            String lastValue = header.values.get(header.values.size() - 1);
            String newValue = lastValue + " " + value.trim();
            header.values.set(header.values.size() - 1, newValue);
        }
    }

    @Override
    public String toString() {
        StringBuilder msg = new StringBuilder();
        for (Header header : headers) {
            for (String value : header.values) {
                msg.append(header.name);
                msg.append(": ");
                msg.append(value);
                msg.append('\n');
            }
        }
        return msg.append('\n').append(body).append('\n').toString();
    }

    private static final class Header {
        String name;
        List<String> values;
    }

}

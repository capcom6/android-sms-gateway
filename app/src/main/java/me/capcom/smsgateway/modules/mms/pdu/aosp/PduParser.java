/*
 * Copyright (C) 2007-2008 Esmertec AG.
 * Copyright (C) 2007-2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package me.capcom.smsgateway.modules.mms.pdu.aosp;

import android.util.Log;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public class PduParser {
    /**
     *  The next are WAP values defined in WSP specification.
     */
    private static final int QUOTE = 127;
    private static final int LENGTH_QUOTE = 31;
    private static final int TEXT_MIN = 32;
    private static final int TEXT_MAX = 127;
    private static final int SHORT_INTEGER_MAX = 127;
    private static final int SHORT_LENGTH_MAX = 30;
    private static final int LONG_INTEGER_LENGTH_MAX = 8;
    private static final int QUOTED_STRING_FLAG = 34;
    private static final int END_STRING_FLAG = 0x00;
    //The next two are used by the interface "parseWapString" to
    //distinguish Text-String and Quoted-String.
    private static final int TYPE_TEXT_STRING = 0;
    private static final int TYPE_QUOTED_STRING = 1;
    private static final int TYPE_TOKEN_STRING = 2;

    /**
     * Specify the part position.
     */
    private static final int THE_FIRST_PART = 0;
    private static final int THE_LAST_PART = 1;

    /**
     * The pdu data.
     */
    private ByteArrayInputStream mPduDataStream = null;

    /**
     * Header fields that were not recognized, fork addition.
     */
    private final List<Integer> mIgnoredHeaders = new ArrayList<Integer>();

    /**
     * The log tag.
     */
    private static final String LOG_TAG = "PduParser";
    private static final boolean DEBUG = false;
    private static final boolean LOCAL_LOGV = false;

    /**
     * Constructor.
     *
     * @param pduDataStream pdu data to be parsed
     */
    public PduParser(byte[] pduDataStream) {
        mPduDataStream = new ByteArrayInputStream(pduDataStream);
    }

    /**
     * Parse the pdu headers of the PDU passed to the constructor.
     *
     * <p>Fork addition: upstream only reaches this through parse(), which also enforces the
     * mandatory headers of every MMS message type. The gateway only needs the headers of a
     * m-notification-ind and tolerates carriers that omit optional fields.
     *
     * @return headers in PduHeaders structure, null when parse fail
     */
    public PduHeaders parseHeaders() {
        return parseHeaders(mPduDataStream);
    }

    /**
     * Header fields that were not recognized and skipped, fork addition.
     *
     * @return the list of the skipped header field ids
     */
    public List<Integer> getIgnoredHeaders() {
        return mIgnoredHeaders;
    }

    /**
     * Parse pdu headers.
     *
     * @param pduDataStream pdu data input stream
     * @return headers in PduHeaders structure, null when parse fail
     */
    protected PduHeaders parseHeaders(ByteArrayInputStream pduDataStream){
        if (pduDataStream == null) {
            return null;
        }
        boolean keepParsing = true;
        PduHeaders headers = new PduHeaders();

        while (keepParsing && (pduDataStream.available() > 0)) {
            pduDataStream.mark(1);
            int headerField = extractByteValue(pduDataStream);
            /* parse custom text header */
            if ((headerField >= TEXT_MIN) && (headerField <= TEXT_MAX)) {
                pduDataStream.reset();
                byte [] bVal = parseWapString(pduDataStream, TYPE_TEXT_STRING);
                if (LOCAL_LOGV) {
                    Log.v(LOG_TAG, "TextHeader: " + new String(bVal));
                }
                /* we should ignore it at the moment */
                continue;
            }
            switch (headerField) {
                case PduHeaders.MESSAGE_TYPE:
                {
                    int messageType = extractByteValue(pduDataStream);
                    if (LOCAL_LOGV) {
                        Log.v(LOG_TAG, "parseHeaders: messageType: " + messageType);
                    }
                    switch (messageType) {
                        // We don't support these kind of messages now.
                        case PduHeaders.MESSAGE_TYPE_FORWARD_REQ:
                        case PduHeaders.MESSAGE_TYPE_FORWARD_CONF:
                        case PduHeaders.MESSAGE_TYPE_MBOX_STORE_REQ:
                        case PduHeaders.MESSAGE_TYPE_MBOX_STORE_CONF:
                        case PduHeaders.MESSAGE_TYPE_MBOX_VIEW_REQ:
                        case PduHeaders.MESSAGE_TYPE_MBOX_VIEW_CONF:
                        case PduHeaders.MESSAGE_TYPE_MBOX_UPLOAD_REQ:
                        case PduHeaders.MESSAGE_TYPE_MBOX_UPLOAD_CONF:
                        case PduHeaders.MESSAGE_TYPE_MBOX_DELETE_REQ:
                        case PduHeaders.MESSAGE_TYPE_MBOX_DELETE_CONF:
                        case PduHeaders.MESSAGE_TYPE_MBOX_DESCR:
                        case PduHeaders.MESSAGE_TYPE_DELETE_REQ:
                        case PduHeaders.MESSAGE_TYPE_DELETE_CONF:
                        case PduHeaders.MESSAGE_TYPE_CANCEL_REQ:
                        case PduHeaders.MESSAGE_TYPE_CANCEL_CONF:
                            return null;
                    }
                    try {
                        headers.setOctet(messageType, headerField);
                    } catch(InvalidHeaderValueException e) {
                        log("Set invalid Octet value: " + messageType +
                                " into the header filed: " + headerField);
                        break;   /* fork: keep parsing an unexpected value */
                    } catch(RuntimeException e) {
                        log(headerField + "is not Octet header field!");
                        break;   /* fork: keep parsing an unexpected value */
                    }
                    break;
                }
                /* Octect value */
                case PduHeaders.REPORT_ALLOWED:
                case PduHeaders.ADAPTATION_ALLOWED:
                case PduHeaders.DELIVERY_REPORT:
                case PduHeaders.DRM_CONTENT:
                case PduHeaders.DISTRIBUTION_INDICATOR:
                case PduHeaders.QUOTAS:
                case PduHeaders.READ_REPORT:
                case PduHeaders.STORE:
                case PduHeaders.STORED:
                case PduHeaders.TOTALS:
                case PduHeaders.SENDER_VISIBILITY:
                case PduHeaders.READ_STATUS:
                case PduHeaders.CANCEL_STATUS:
                case PduHeaders.PRIORITY:
                case PduHeaders.STATUS:
                case PduHeaders.REPLY_CHARGING:
                case PduHeaders.MM_STATE:
                case PduHeaders.RECOMMENDED_RETRIEVAL_MODE:
                case PduHeaders.CONTENT_CLASS:
                case PduHeaders.RETRIEVE_STATUS:
                case PduHeaders.STORE_STATUS:
                    /**
                     * The following field has a different value when
                     * used in the M-Mbox-Delete.conf and M-Delete.conf PDU.
                     * For now we ignore this fact, since we do not support these PDUs
                     */
                case PduHeaders.RESPONSE_STATUS:
                {
                    int value = extractByteValue(pduDataStream);
                    if (LOCAL_LOGV) {
                        Log.v(LOG_TAG, "parseHeaders: byte: " + headerField + " value: " +
                                value);
                    }

                    try {
                        headers.setOctet(value, headerField);
                    } catch(InvalidHeaderValueException e) {
                        log("Set invalid Octet value: " + value +
                                " into the header filed: " + headerField);
                        break;   /* fork: keep parsing an unexpected value */
                    } catch(RuntimeException e) {
                        log(headerField + "is not Octet header field!");
                        break;   /* fork: keep parsing an unexpected value */
                    }
                    break;
                }

                /* Long-Integer */
                case PduHeaders.DATE:
                case PduHeaders.REPLY_CHARGING_SIZE:
                case PduHeaders.MESSAGE_SIZE:
                {
                    try {
                        long value = parseLongInteger(pduDataStream);
                        if (LOCAL_LOGV) {
                            Log.v(LOG_TAG, "parseHeaders: longint: " + headerField + " value: " +
                                    value);
                        }
                        headers.setLongInteger(value, headerField);
                    } catch(RuntimeException e) {
                        log(headerField + "is not Long-Integer header field!");
                        return null;
                    }
                    break;
                }

                /* Integer-Value */
                case PduHeaders.MESSAGE_COUNT:
                case PduHeaders.START:
                case PduHeaders.LIMIT:
                {
                    try {
                        long value = parseIntegerValue(pduDataStream);
                        if (LOCAL_LOGV) {
                            Log.v(LOG_TAG, "parseHeaders: int: " + headerField + " value: " +
                                    value);
                        }
                        headers.setLongInteger(value, headerField);
                    } catch(RuntimeException e) {
                        log(headerField + "is not Long-Integer header field!");
                        return null;
                    }
                    break;
                }

                /* Text-String */
                case PduHeaders.TRANSACTION_ID:
                case PduHeaders.REPLY_CHARGING_ID:
                case PduHeaders.AUX_APPLIC_ID:
                case PduHeaders.APPLIC_ID:
                case PduHeaders.REPLY_APPLIC_ID:
                    /**
                     * The next three header fields are email addresses
                     * as defined in RFC2822,
                     * not including the characters "<" and ">"
                     */
                case PduHeaders.MESSAGE_ID:
                case PduHeaders.REPLACE_ID:
                case PduHeaders.CANCEL_ID:
                    /**
                     * The following field has a different value when
                     * used in the M-Mbox-Delete.conf and M-Delete.conf PDU.
                     * For now we ignore this fact, since we do not support these PDUs
                     */
                case PduHeaders.CONTENT_LOCATION:
                {
                    byte[] value = parseWapString(pduDataStream, TYPE_TEXT_STRING);
                    if (null != value) {
                        try {
                            if (LOCAL_LOGV) {
                                Log.v(LOG_TAG, "parseHeaders: string: " + headerField + " value: " +
                                        new String(value));
                            }
                            headers.setTextString(value, headerField);
                        } catch(NullPointerException e) {
                            log("null pointer error!");
                        } catch(RuntimeException e) {
                            log(headerField + "is not Text-String header field!");
                            break;   /* fork: keep parsing an unexpected value */
                        }
                    }
                    break;
                }

                /* Encoded-string-value */
                case PduHeaders.SUBJECT:
                case PduHeaders.RECOMMENDED_RETRIEVAL_MODE_TEXT:
                case PduHeaders.RETRIEVE_TEXT:
                case PduHeaders.STATUS_TEXT:
                case PduHeaders.STORE_STATUS_TEXT:
                    /* the next one is not support
                     * M-Mbox-Delete.conf and M-Delete.conf now */
                case PduHeaders.RESPONSE_TEXT:
                {
                    EncodedStringValue value =
                        parseEncodedStringValue(pduDataStream);
                    if (null != value) {
                        try {
                            if (LOCAL_LOGV) {
                                Log.v(LOG_TAG, "parseHeaders: encoded string: " + headerField
                                        + " value: " + value.getString());
                            }
                            headers.setEncodedStringValue(value, headerField);
                        } catch(NullPointerException e) {
                            log("null pointer error!");
                        } catch (RuntimeException e) {
                            log(headerField + "is not Encoded-String-Value header field!");
                            break;   /* fork: keep parsing an unexpected value */
                        }
                    }
                    break;
                }

                /* Addressing model */
                case PduHeaders.BCC:
                case PduHeaders.CC:
                case PduHeaders.TO:
                {
                    EncodedStringValue value =
                        parseEncodedStringValue(pduDataStream);
                    if (null != value) {
                        byte[] address = value.getTextString();
                        if (null != address) {
                            String str = new String(address);
                            if (LOCAL_LOGV) {
                                Log.v(LOG_TAG, "parseHeaders: (to/cc/bcc) address: " + headerField
                                        + " value: " + str);
                            }
                            int endIndex = str.indexOf("/");
                            if (endIndex > 0) {
                                str = str.substring(0, endIndex);
                            }
                            try {
                                value.setTextString(str.getBytes());
                            } catch(NullPointerException e) {
                                log("null pointer error!");
                                break;   /* fork: keep parsing an unexpected value */
                            }
                        }

                        try {
                            headers.appendEncodedStringValue(value, headerField);
                        } catch(NullPointerException e) {
                            log("null pointer error!");
                        } catch(RuntimeException e) {
                            log(headerField + "is not Encoded-String-Value header field!");
                            break;   /* fork: keep parsing an unexpected value */
                        }
                    }
                    break;
                }

                /* Value-length
                 * (Absolute-token Date-value | Relative-token Delta-seconds-value) */
                case PduHeaders.DELIVERY_TIME:
                case PduHeaders.EXPIRY:
                case PduHeaders.REPLY_CHARGING_DEADLINE:
                {
                    /* parse Value-length */
                    parseValueLength(pduDataStream);

                    /* Absolute-token or Relative-token */
                    int token = extractByteValue(pduDataStream);

                    /* Date-value or Delta-seconds-value */
                    long timeValue;
                    try {
                        timeValue = parseLongInteger(pduDataStream);
                    } catch(RuntimeException e) {
                        log(headerField + "is not Long-Integer header field!");
                        return null;
                    }
                    if (PduHeaders.VALUE_RELATIVE_TOKEN == token) {
                        /* need to convert the Delta-seconds-value
                         * into Date-value */
                        timeValue = System.currentTimeMillis()/1000 + timeValue;
                    }

                    try {
                        if (LOCAL_LOGV) {
                            Log.v(LOG_TAG, "parseHeaders: time value: " + headerField
                                    + " value: " + timeValue);
                        }
                        headers.setLongInteger(timeValue, headerField);
                    } catch(RuntimeException e) {
                        log(headerField + "is not Long-Integer header field!");
                        break;   /* fork: keep parsing an unexpected value */
                    }
                    break;
                }

                case PduHeaders.FROM: {
                    /* From-value =
                     * Value-length
                     * (Address-present-token Encoded-string-value | Insert-address-token)
                     */
                    EncodedStringValue from = null;
                    parseValueLength(pduDataStream); /* parse value-length */

                    /* Address-present-token or Insert-address-token */
                    int fromToken = extractByteValue(pduDataStream);

                    /* Address-present-token or Insert-address-token */
                    if (PduHeaders.FROM_ADDRESS_PRESENT_TOKEN == fromToken) {
                        /* Encoded-string-value */
                        from = parseEncodedStringValue(pduDataStream);
                        if (null != from) {
                            byte[] address = from.getTextString();
                            if (null != address) {
                                String str = new String(address);
                                int endIndex = str.indexOf("/");
                                if (endIndex > 0) {
                                    str = str.substring(0, endIndex);
                                }
                                try {
                                    from.setTextString(str.getBytes());
                                } catch(NullPointerException e) {
                                    log("null pointer error!");
                                    break;   /* fork: keep parsing an unexpected value */
                                }
                            }
                        }
                    } else {
                        try {
                            from = new EncodedStringValue(
                                    PduHeaders.FROM_INSERT_ADDRESS_TOKEN_STR.getBytes());
                        } catch(NullPointerException e) {
                            log(headerField + "is not Encoded-String-Value header field!");
                            break;   /* fork: keep parsing an unexpected value */
                        }
                    }

                    try {
                        if (LOCAL_LOGV) {
                            Log.v(LOG_TAG, "parseHeaders: from address: " + headerField
                                    + " value: " + from.getString());
                        }
                        headers.setEncodedStringValue(from, PduHeaders.FROM);
                    } catch(NullPointerException e) {
                        log("null pointer error!");
                    } catch(RuntimeException e) {
                        log(headerField + "is not Encoded-String-Value header field!");
                        break;   /* fork: keep parsing an unexpected value */
                    }
                    break;
                }

                case PduHeaders.MESSAGE_CLASS: {
                    /* Message-class-value = Class-identifier | Token-text */
                    pduDataStream.mark(1);
                    int messageClass = extractByteValue(pduDataStream);
                    if (LOCAL_LOGV) {
                        Log.v(LOG_TAG, "parseHeaders: MESSAGE_CLASS: " + headerField
                                + " value: " + messageClass);
                    }

                    if (messageClass >= PduHeaders.MESSAGE_CLASS_PERSONAL) {
                        /* Class-identifier */
                        try {
                            if (PduHeaders.MESSAGE_CLASS_PERSONAL == messageClass) {
                                headers.setTextString(
                                        PduHeaders.MESSAGE_CLASS_PERSONAL_STR.getBytes(),
                                        PduHeaders.MESSAGE_CLASS);
                            } else if (PduHeaders.MESSAGE_CLASS_ADVERTISEMENT == messageClass) {
                                headers.setTextString(
                                        PduHeaders.MESSAGE_CLASS_ADVERTISEMENT_STR.getBytes(),
                                        PduHeaders.MESSAGE_CLASS);
                            } else if (PduHeaders.MESSAGE_CLASS_INFORMATIONAL == messageClass) {
                                headers.setTextString(
                                        PduHeaders.MESSAGE_CLASS_INFORMATIONAL_STR.getBytes(),
                                        PduHeaders.MESSAGE_CLASS);
                            } else if (PduHeaders.MESSAGE_CLASS_AUTO == messageClass) {
                                headers.setTextString(
                                        PduHeaders.MESSAGE_CLASS_AUTO_STR.getBytes(),
                                        PduHeaders.MESSAGE_CLASS);
                            }
                        } catch(NullPointerException e) {
                            log("null pointer error!");
                        } catch(RuntimeException e) {
                            log(headerField + "is not Text-String header field!");
                            break;   /* fork: keep parsing an unexpected value */
                        }
                    } else {
                        /* Token-text */
                        pduDataStream.reset();
                        byte[] messageClassString = parseWapString(pduDataStream, TYPE_TEXT_STRING);
                        if (null != messageClassString) {
                            try {
                                headers.setTextString(messageClassString, PduHeaders.MESSAGE_CLASS);
                            } catch(NullPointerException e) {
                                log("null pointer error!");
                            } catch(RuntimeException e) {
                                log(headerField + "is not Text-String header field!");
                                break;   /* fork: keep parsing an unexpected value */
                            }
                        }
                    }
                    break;
                }

                case PduHeaders.MMS_VERSION: {
                    int version = parseShortInteger(pduDataStream);

                    try {
                        if (LOCAL_LOGV) {
                            Log.v(LOG_TAG, "parseHeaders: MMS_VERSION: " + headerField
                                    + " value: " + version);
                        }
                        headers.setOctet(version, PduHeaders.MMS_VERSION);
                    } catch(InvalidHeaderValueException e) {
                        log("Set invalid Octet value: " + version +
                                " into the header filed: " + headerField);
                        break;   /* fork: keep parsing an unexpected value */
                    } catch(RuntimeException e) {
                        log(headerField + "is not Octet header field!");
                        break;   /* fork: keep parsing an unexpected value */
                    }
                    break;
                }

                case PduHeaders.PREVIOUSLY_SENT_BY: {
                    /* Previously-sent-by-value =
                     * Value-length Forwarded-count-value Encoded-string-value */
                    /* parse value-length */
                    parseValueLength(pduDataStream);

                    /* parse Forwarded-count-value */
                    try {
                        parseIntegerValue(pduDataStream);
                    } catch(RuntimeException e) {
                        log(headerField + " is not Integer-Value");
                        return null;
                    }

                    /* parse Encoded-string-value */
                    EncodedStringValue previouslySentBy =
                        parseEncodedStringValue(pduDataStream);
                    if (null != previouslySentBy) {
                        try {
                            if (LOCAL_LOGV) {
                                Log.v(LOG_TAG, "parseHeaders: PREVIOUSLY_SENT_BY: " + headerField
                                        + " value: " + previouslySentBy.getString());
                            }
                            headers.setEncodedStringValue(previouslySentBy,
                                    PduHeaders.PREVIOUSLY_SENT_BY);
                        } catch(NullPointerException e) {
                            log("null pointer error!");
                        } catch(RuntimeException e) {
                            log(headerField + "is not Encoded-String-Value header field!");
                            break;   /* fork: keep parsing an unexpected value */
                        }
                    }
                    break;
                }

                case PduHeaders.PREVIOUSLY_SENT_DATE: {
                    /* Previously-sent-date-value =
                     * Value-length Forwarded-count-value Date-value */
                    /* parse value-length */
                    parseValueLength(pduDataStream);

                    /* parse Forwarded-count-value */
                    try {
                        parseIntegerValue(pduDataStream);
                    } catch(RuntimeException e) {
                        log(headerField + " is not Integer-Value");
                        return null;
                    }

                    /* Date-value */
                    try {
                        long perviouslySentDate = parseLongInteger(pduDataStream);
                        if (LOCAL_LOGV) {
                            Log.v(LOG_TAG, "parseHeaders: PREVIOUSLY_SENT_DATE: " + headerField
                                    + " value: " + perviouslySentDate);
                        }
                        headers.setLongInteger(perviouslySentDate,
                                PduHeaders.PREVIOUSLY_SENT_DATE);
                    } catch(RuntimeException e) {
                        log(headerField + "is not Long-Integer header field!");
                        return null;
                    }
                    break;
                }

                case PduHeaders.MM_FLAGS: {
                    /* MM-flags-value =
                     * Value-length
                     * ( Add-token | Remove-token | Filter-token )
                     * Encoded-string-value
                     */
                    if (LOCAL_LOGV) {
                        Log.v(LOG_TAG, "parseHeaders: MM_FLAGS: " + headerField
                                + " NOT REALLY SUPPORTED");
                    }

                    /* parse Value-length */
                    parseValueLength(pduDataStream);

                    /* Add-token | Remove-token | Filter-token */
                    extractByteValue(pduDataStream);

                    /* Encoded-string-value */
                    parseEncodedStringValue(pduDataStream);

                    /* not store this header filed in "headers",
                     * because now PduHeaders doesn't support it */
                    break;
                }

                /* Value-length
                 * (Message-total-token | Size-total-token) Integer-Value */
                case PduHeaders.MBOX_TOTALS:
                case PduHeaders.MBOX_QUOTAS:
                {
                    if (LOCAL_LOGV) {
                        Log.v(LOG_TAG, "parseHeaders: MBOX_TOTALS: " + headerField);
                    }
                    /* Value-length */
                    parseValueLength(pduDataStream);

                    /* Message-total-token | Size-total-token */
                    extractByteValue(pduDataStream);

                    /*Integer-Value*/
                    try {
                        parseIntegerValue(pduDataStream);
                    } catch(RuntimeException e) {
                        log(headerField + " is not Integer-Value");
                        return null;
                    }

                    /* not store these headers filed in "headers",
                    because now PduHeaders doesn't support them */
                    break;
                }

                case PduHeaders.ELEMENT_DESCRIPTOR: {
                    if (LOCAL_LOGV) {
                        Log.v(LOG_TAG, "parseHeaders: ELEMENT_DESCRIPTOR: " + headerField);
                    }
                    parseContentType(pduDataStream, null);

                    /* not store this header filed in "headers",
                    because now PduHeaders doesn't support it */
                    break;
                }

                case PduHeaders.CONTENT_TYPE: {
                    HashMap<Integer, Object> map =
                        new HashMap<Integer, Object>();
                    byte[] contentType =
                        parseContentType(pduDataStream, map);

                    if (null != contentType) {
                        try {
                            if (LOCAL_LOGV) {
                                Log.v(LOG_TAG, "parseHeaders: CONTENT_TYPE: " + headerField +
                                        Arrays.toString(contentType));
                            }
                            headers.setTextString(contentType, PduHeaders.CONTENT_TYPE);
                        } catch(NullPointerException e) {
                            log("null pointer error!");
                        } catch(RuntimeException e) {
                            log(headerField + "is not Text-String header field!");
                            break;   /* fork: keep parsing an unexpected value */
                        }
                    }

                    keepParsing = false;
                    break;
                }

                case PduHeaders.CONTENT:
                case PduHeaders.ADDITIONAL_HEADERS:
                case PduHeaders.ATTRIBUTES:
                default: {
                    mIgnoredHeaders.add(headerField);  /* fork: report the skipped field */
                    if (LOCAL_LOGV) {
                        Log.v(LOG_TAG, "parseHeaders: Unknown header: " + headerField);
                    }
                    log("Unknown header");
                }
            }
        }

        return headers;
    }
    /**
     * Log status.
     *
     * @param text log information
     */
    private static void log(String text) {
        if (LOCAL_LOGV) {
            Log.v(LOG_TAG, text);
        }
    }

    /**
     * Parse unsigned integer.
     *
     * @param pduDataStream pdu data input stream
     * @return the integer, -1 when failed
     */
    protected static int parseUnsignedInt(ByteArrayInputStream pduDataStream) {
        /**
         * From wap-230-wsp-20010705-a.pdf
         * The maximum size of a uintvar is 32 bits.
         * So it will be encoded in no more than 5 octets.
         */
        assert(null != pduDataStream);
        int result = 0;
        int temp = pduDataStream.read();
        if (temp == -1) {
            return temp;
        }

        while((temp & 0x80) != 0) {
            result = result << 7;
            result |= temp & 0x7F;
            temp = pduDataStream.read();
            if (temp == -1) {
                return temp;
            }
        }

        result = result << 7;
        result |= temp & 0x7F;

        return result;
    }

    /**
     * Parse value length.
     *
     * @param pduDataStream pdu data input stream
     * @return the integer
     */
    protected static int parseValueLength(ByteArrayInputStream pduDataStream) {
        /**
         * From wap-230-wsp-20010705-a.pdf
         * Value-length = Short-length | (Length-quote Length)
         * Short-length = <Any octet 0-30>
         * Length-quote = <Octet 31>
         * Length = Uintvar-integer
         * Uintvar-integer = 1*5 OCTET
         */
        assert(null != pduDataStream);
        int temp = pduDataStream.read();
        assert(-1 != temp);
        int first = temp & 0xFF;

        if (first <= SHORT_LENGTH_MAX) {
            return first;
        } else if (first == LENGTH_QUOTE) {
            return parseUnsignedInt(pduDataStream);
        }

        throw new RuntimeException ("Value length > LENGTH_QUOTE!");
    }

    /**
     * Parse encoded string value.
     *
     * @param pduDataStream pdu data input stream
     * @return the EncodedStringValue
     */
    protected static EncodedStringValue parseEncodedStringValue(ByteArrayInputStream pduDataStream){
        /**
         * From OMA-TS-MMS-ENC-V1_3-20050927-C.pdf
         * Encoded-string-value = Text-string | Value-length Char-set Text-string
         */
        assert(null != pduDataStream);
        pduDataStream.mark(1);
        EncodedStringValue returnValue = null;
        int charset = 0;
        int temp = pduDataStream.read();
        assert(-1 != temp);
        int first = temp & 0xFF;
        if (first == 0) {
            return new EncodedStringValue("");
        }

        pduDataStream.reset();
        if (first < TEXT_MIN) {
            parseValueLength(pduDataStream);

            charset = parseShortInteger(pduDataStream); //get the "Charset"
        }

        byte[] textString = parseWapString(pduDataStream, TYPE_TEXT_STRING);

        try {
            if (0 != charset) {
                returnValue = new EncodedStringValue(charset, textString);
            } else {
                returnValue = new EncodedStringValue(textString);
            }
        } catch(Exception e) {
            return null;
        }

        return returnValue;
    }

    /**
     * Parse Text-String or Quoted-String.
     *
     * @param pduDataStream pdu data input stream
     * @param stringType TYPE_TEXT_STRING or TYPE_QUOTED_STRING
     * @return the string without End-of-string in byte array
     */
    protected static byte[] parseWapString(ByteArrayInputStream pduDataStream,
            int stringType) {
        assert(null != pduDataStream);
        /**
         * From wap-230-wsp-20010705-a.pdf
         * Text-string = [Quote] *TEXT End-of-string
         * If the first character in the TEXT is in the range of 128-255,
         * a Quote character must precede it.
         * Otherwise the Quote character must be omitted.
         * The Quote is not part of the contents.
         * Quote = <Octet 127>
         * End-of-string = <Octet 0>
         *
         * Quoted-string = <Octet 34> *TEXT End-of-string
         *
         * Token-text = Token End-of-string
         */

        // Mark supposed beginning of Text-string
        // We will have to mark again if first char is QUOTE or QUOTED_STRING_FLAG
        pduDataStream.mark(1);

        // Check first char
        int temp = pduDataStream.read();
        assert(-1 != temp);
        if (-1 == temp) {
            return null;
        }
        if ((TYPE_QUOTED_STRING == stringType) &&
                (QUOTED_STRING_FLAG == temp)) {
            // Mark again if QUOTED_STRING_FLAG and ignore it
            pduDataStream.mark(1);
        } else if ((TYPE_TEXT_STRING == stringType) &&
                (QUOTE == temp)) {
            // Mark again if QUOTE and ignore it
            pduDataStream.mark(1);
        } else {
            // Otherwise go back to origin
            pduDataStream.reset();
        }

        // We are now definitely at the beginning of string
        /**
         * Return *TOKEN or *TEXT (Text-String without QUOTE,
         * Quoted-String without QUOTED_STRING_FLAG and without End-of-string)
         */
        return getWapString(pduDataStream, stringType);
    }

    /**
     * Check TOKEN data defined in RFC2616.
     * @param ch checking data
     * @return true when ch is TOKEN, false when ch is not TOKEN
     */
    protected static boolean isTokenCharacter(int ch) {
        /**
         * Token      = 1*<any CHAR except CTLs or separators>
         * separators = "("(40) | ")"(41) | "<"(60) | ">"(62) | "@"(64)
         *            | ","(44) | ";"(59) | ":"(58) | "\"(92) | <">(34)
         *            | "/"(47) | "["(91) | "]"(93) | "?"(63) | "="(61)
         *            | "{"(123) | "}"(125) | SP(32) | HT(9)
         * CHAR       = <any US-ASCII character (octets 0 - 127)>
         * CTL        = <any US-ASCII control character
         *            (octets 0 - 31) and DEL (127)>
         * SP         = <US-ASCII SP, space (32)>
         * HT         = <US-ASCII HT, horizontal-tab (9)>
         */
        if((ch < 33) || (ch > 126)) {
            return false;
        }

        switch(ch) {
            case '"': /* '"' */
            case '(': /* '(' */
            case ')': /* ')' */
            case ',': /* ',' */
            case '/': /* '/' */
            case ':': /* ':' */
            case ';': /* ';' */
            case '<': /* '<' */
            case '=': /* '=' */
            case '>': /* '>' */
            case '?': /* '?' */
            case '@': /* '@' */
            case '[': /* '[' */
            case '\\': /* '\' */
            case ']': /* ']' */
            case '{': /* '{' */
            case '}': /* '}' */
                return false;
        }

        return true;
    }

    /**
     * Check TEXT data defined in RFC2616.
     * @param ch checking data
     * @return true when ch is TEXT, false when ch is not TEXT
     */
    protected static boolean isText(int ch) {
        /**
         * TEXT = <any OCTET except CTLs,
         *      but including LWS>
         * CTL  = <any US-ASCII control character
         *      (octets 0 - 31) and DEL (127)>
         * LWS  = [CRLF] 1*( SP | HT )
         * CRLF = CR LF
         * CR   = <US-ASCII CR, carriage return (13)>
         * LF   = <US-ASCII LF, linefeed (10)>
         */
        if(((ch >= 32) && (ch <= 126)) || ((ch >= 128) && (ch <= 255))) {
            return true;
        }

        switch(ch) {
            case '\t': /* '\t' */
            case '\n': /* '\n' */
            case '\r': /* '\r' */
                return true;
        }

        return false;
    }

    protected static byte[] getWapString(ByteArrayInputStream pduDataStream,
            int stringType) {
        assert(null != pduDataStream);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int temp = pduDataStream.read();
        assert(-1 != temp);
        if (-1 == temp) {
            return null;
        }
        while((-1 != temp) && ('\0' != temp)) {
            // check each of the character
            if (stringType == TYPE_TOKEN_STRING) {
                if (isTokenCharacter(temp)) {
                    out.write(temp);
                }
            } else {
                if (isText(temp)) {
                    out.write(temp);
                }
            }

            temp = pduDataStream.read();
            /* fork: a truncated PDU ends the string instead of failing the assert,
             * android runs with assertions disabled */
        }

        if (out.size() > 0) {
            return out.toByteArray();
        }

        return null;
    }

    /**
     * Extract a byte value from the input stream.
     *
     * @param pduDataStream pdu data input stream
     * @return the byte
     */
    protected static int extractByteValue(ByteArrayInputStream pduDataStream) {
        assert(null != pduDataStream);
        int temp = pduDataStream.read();
        assert(-1 != temp);
        return temp & 0xFF;
    }

    /**
     * Parse Short-Integer.
     *
     * @param pduDataStream pdu data input stream
     * @return the byte
     */
    protected static int parseShortInteger(ByteArrayInputStream pduDataStream) {
        /**
         * From wap-230-wsp-20010705-a.pdf
         * Short-integer = OCTET
         * Integers in range 0-127 shall be encoded as a one
         * octet value with the most significant bit set to one (1xxx xxxx)
         * and with the value in the remaining least significant bits.
         */
        assert(null != pduDataStream);
        int temp = pduDataStream.read();
        assert(-1 != temp);
        return temp & 0x7F;
    }

    /**
     * Parse Long-Integer.
     *
     * @param pduDataStream pdu data input stream
     * @return long integer
     */
    protected static long parseLongInteger(ByteArrayInputStream pduDataStream) {
        /**
         * From wap-230-wsp-20010705-a.pdf
         * Long-integer = Short-length Multi-octet-integer
         * The Short-length indicates the length of the Multi-octet-integer
         * Multi-octet-integer = 1*30 OCTET
         * The content octets shall be an unsigned integer value
         * with the most significant octet encoded first (big-endian representation).
         * The minimum number of octets must be used to encode the value.
         * Short-length = <Any octet 0-30>
         */
        assert(null != pduDataStream);
        int temp = pduDataStream.read();
        assert(-1 != temp);
        int count = temp & 0xFF;

        if (count > LONG_INTEGER_LENGTH_MAX) {
            throw new RuntimeException("Octet count greater than 8 and I can't represent that!");
        }

        long result = 0;

        for (int i = 0 ; i < count ; i++) {
            temp = pduDataStream.read();
            assert(-1 != temp);
            result <<= 8;
            result += (temp & 0xFF);
        }

        return result;
    }

    /**
     * Parse Integer-Value.
     *
     * @param pduDataStream pdu data input stream
     * @return long integer
     */
    protected static long parseIntegerValue(ByteArrayInputStream pduDataStream) {
        /**
         * From wap-230-wsp-20010705-a.pdf
         * Integer-Value = Short-integer | Long-integer
         */
        assert(null != pduDataStream);
        pduDataStream.mark(1);
        int temp = pduDataStream.read();
        assert(-1 != temp);
        pduDataStream.reset();
        if (temp > SHORT_INTEGER_MAX) {
            return parseShortInteger(pduDataStream);
        } else {
            return parseLongInteger(pduDataStream);
        }
    }

    /**
     * To skip length of the wap value.
     *
     * @param pduDataStream pdu data input stream
     * @param length area size
     * @return the values in this area
     */
    protected static int skipWapValue(ByteArrayInputStream pduDataStream, int length) {
        assert(null != pduDataStream);
        byte[] area = new byte[length];
        int readLen = pduDataStream.read(area, 0, length);
        if (readLen < length) { //The actually read length is lower than the length
            return -1;
        } else {
            return readLen;
        }
    }

    /**
     * Parse content type parameters. For now we just support
     * four parameters used in mms: "type", "start", "name", "charset".
     *
     * @param pduDataStream pdu data input stream
     * @param map to store parameters of Content-Type field
     * @param length length of all the parameters
     */
    protected static void parseContentTypeParams(ByteArrayInputStream pduDataStream,
            HashMap<Integer, Object> map, Integer length) {
        /**
         * From wap-230-wsp-20010705-a.pdf
         * Parameter = Typed-parameter | Untyped-parameter
         * Typed-parameter = Well-known-parameter-token Typed-value
         * the actual expected type of the value is implied by the well-known parameter
         * Well-known-parameter-token = Integer-value
         * the code values used for parameters are specified in the Assigned Numbers appendix
         * Typed-value = Compact-value | Text-value
         * In addition to the expected type, there may be no value.
         * If the value cannot be encoded using the expected type, it shall be encoded as text.
         * Compact-value = Integer-value |
         * Date-value | Delta-seconds-value | Q-value | Version-value |
         * Uri-value
         * Untyped-parameter = Token-text Untyped-value
         * the type of the value is unknown, but it shall be encoded as an integer,
         * if that is possible.
         * Untyped-value = Integer-value | Text-value
         */
        assert(null != pduDataStream);
        assert(length > 0);

        int startPos = pduDataStream.available();
        int tempPos = 0;
        int lastLen = length;
        while(0 < lastLen) {
            int param = pduDataStream.read();
            assert(-1 != param);
            lastLen--;

            switch (param) {
                /**
                 * From rfc2387, chapter 3.1
                 * The type parameter must be specified and its value is the MIME media
                 * type of the "root" body part. It permits a MIME user agent to
                 * determine the content-type without reference to the enclosed body
                 * part. If the value of the type parameter and the root body part's
                 * content-type differ then the User Agent's behavior is undefined.
                 *
                 * From wap-230-wsp-20010705-a.pdf
                 * type = Constrained-encoding
                 * Constrained-encoding = Extension-Media | Short-integer
                 * Extension-media = *TEXT End-of-string
                 */
                case PduPart.P_TYPE:
                case PduPart.P_CT_MR_TYPE:
                    pduDataStream.mark(1);
                    int first = extractByteValue(pduDataStream);
                    pduDataStream.reset();
                    if (first > TEXT_MAX) {
                        // Short-integer (well-known type)
                        int index = parseShortInteger(pduDataStream);

                        if (index < PduContentTypes.contentTypes.length) {
                            byte[] type = (PduContentTypes.contentTypes[index]).getBytes();
                            map.put(PduPart.P_TYPE, type);
                        } else {
                            //not support this type, ignore it.
                        }
                    } else {
                        // Text-String (extension-media)
                        byte[] type = parseWapString(pduDataStream, TYPE_TEXT_STRING);
                        if ((null != type) && (null != map)) {
                            map.put(PduPart.P_TYPE, type);
                        }
                    }

                    tempPos = pduDataStream.available();
                    lastLen = length - (startPos - tempPos);
                    break;

                    /**
                     * From oma-ts-mms-conf-v1_3.pdf, chapter 10.2.3.
                     * Start Parameter Referring to Presentation
                     *
                     * From rfc2387, chapter 3.2
                     * The start parameter, if given, is the content-ID of the compound
                     * object's "root". If not present the "root" is the first body part in
                     * the Multipart/Related entity. The "root" is the element the
                     * applications processes first.
                     *
                     * From wap-230-wsp-20010705-a.pdf
                     * start = Text-String
                     */
                case PduPart.P_START:
                case PduPart.P_DEP_START:
                    byte[] start = parseWapString(pduDataStream, TYPE_TEXT_STRING);
                    if ((null != start) && (null != map)) {
                        map.put(PduPart.P_START, start);
                    }

                    tempPos = pduDataStream.available();
                    lastLen = length - (startPos - tempPos);
                    break;

                    /**
                     * From oma-ts-mms-conf-v1_3.pdf
                     * In creation, the character set SHALL be either us-ascii
                     * (IANA MIBenum 3) or utf-8 (IANA MIBenum 106)[Unicode].
                     * In retrieval, both us-ascii and utf-8 SHALL be supported.
                     *
                     * From wap-230-wsp-20010705-a.pdf
                     * charset = Well-known-charset|Text-String
                     * Well-known-charset = Any-charset | Integer-value
                     * Both are encoded using values from Character Set
                     * Assignments table in Assigned Numbers
                     * Any-charset = <Octet 128>
                     * Equivalent to the special RFC2616 charset value "*"
                     */
                case PduPart.P_CHARSET:
                    pduDataStream.mark(1);
                    int firstValue = extractByteValue(pduDataStream);
                    pduDataStream.reset();
                    //Check first char
                    if (((firstValue > TEXT_MIN) && (firstValue < TEXT_MAX)) ||
                            (END_STRING_FLAG == firstValue)) {
                        //Text-String (extension-charset)
                        byte[] charsetStr = parseWapString(pduDataStream, TYPE_TEXT_STRING);
                        try {
                            int charsetInt = CharacterSets.getMibEnumValue(
                                    new String(charsetStr));
                            map.put(PduPart.P_CHARSET, charsetInt);
                        } catch (UnsupportedEncodingException e) {
                            // Not a well-known charset, use "*".
                            Log.e(LOG_TAG, Arrays.toString(charsetStr), e);
                            map.put(PduPart.P_CHARSET, CharacterSets.ANY_CHARSET);
                        }
                    } else {
                        //Well-known-charset
                        int charset = (int) parseIntegerValue(pduDataStream);
                        if (map != null) {
                            map.put(PduPart.P_CHARSET, charset);
                        }
                    }

                    tempPos = pduDataStream.available();
                    lastLen = length - (startPos - tempPos);
                    break;

                    /**
                     * From oma-ts-mms-conf-v1_3.pdf
                     * A name for multipart object SHALL be encoded using name-parameter
                     * for Content-Type header in WSP multipart headers.
                     *
                     * From wap-230-wsp-20010705-a.pdf
                     * name = Text-String
                     */
                case PduPart.P_DEP_NAME:
                case PduPart.P_NAME:
                    byte[] name = parseWapString(pduDataStream, TYPE_TEXT_STRING);
                    if ((null != name) && (null != map)) {
                        map.put(PduPart.P_NAME, name);
                    }

                    tempPos = pduDataStream.available();
                    lastLen = length - (startPos - tempPos);
                    break;
                default:
                    if (LOCAL_LOGV) {
                        Log.v(LOG_TAG, "Not supported Content-Type parameter");
                    }
                if (-1 == skipWapValue(pduDataStream, lastLen)) {
                    Log.e(LOG_TAG, "Corrupt Content-Type");
                } else {
                    lastLen = 0;
                }
                break;
            }
        }

        if (0 != lastLen) {
            Log.e(LOG_TAG, "Corrupt Content-Type");
        }
    }

    /**
     * Parse content type.
     *
     * @param pduDataStream pdu data input stream
     * @param map to store parameters in Content-Type header field
     * @return Content-Type value
     */
    protected static byte[] parseContentType(ByteArrayInputStream pduDataStream,
            HashMap<Integer, Object> map) {
        /**
         * From wap-230-wsp-20010705-a.pdf
         * Content-type-value = Constrained-media | Content-general-form
         * Content-general-form = Value-length Media-type
         * Media-type = (Well-known-media | Extension-Media) *(Parameter)
         */
        assert(null != pduDataStream);

        byte[] contentType = null;
        pduDataStream.mark(1);
        int temp = pduDataStream.read();
        assert(-1 != temp);
        pduDataStream.reset();

        int cur = (temp & 0xFF);

        if (cur < TEXT_MIN) {
            int length = parseValueLength(pduDataStream);
            int startPos = pduDataStream.available();
            if (length > startPos) {
                Log.e(LOG_TAG, "parseContentType: Invalid length " + length
                        + " when available bytes are " + startPos);
                return (PduContentTypes.contentTypes[0]).getBytes(); //"*/*"
            }
            pduDataStream.mark(1);
            temp = pduDataStream.read();
            assert(-1 != temp);
            pduDataStream.reset();
            int first = (temp & 0xFF);

            if ((first >= TEXT_MIN) && (first <= TEXT_MAX)) {
                contentType = parseWapString(pduDataStream, TYPE_TEXT_STRING);
            } else if (first > TEXT_MAX) {
                int index = parseShortInteger(pduDataStream);

                if (index < PduContentTypes.contentTypes.length) { //well-known type
                    contentType = (PduContentTypes.contentTypes[index]).getBytes();
                } else {
                    pduDataStream.reset();
                    contentType = parseWapString(pduDataStream, TYPE_TEXT_STRING);
                }
            } else {
                Log.e(LOG_TAG, "Corrupt content-type");
                return (PduContentTypes.contentTypes[0]).getBytes(); //"*/*"
            }

            int endPos = pduDataStream.available();
            int parameterLen = length - (startPos - endPos);
            if (parameterLen > 0) {//have parameters
                parseContentTypeParams(pduDataStream, map, parameterLen);
            }

            if (parameterLen < 0) {
                Log.e(LOG_TAG, "Corrupt MMS message");
                return (PduContentTypes.contentTypes[0]).getBytes(); //"*/*"
            }
        } else if (cur <= TEXT_MAX) {
            contentType = parseWapString(pduDataStream, TYPE_TEXT_STRING);
        } else {
            contentType =
                (PduContentTypes.contentTypes[parseShortInteger(pduDataStream)]).getBytes();
        }

        return contentType;
    }

}

/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2009 Sun Microsystems, Inc.
 */

package org.forgerock.opendj.ldap.schema;

import static com.forgerock.opendj.ldap.CoreMessages.*;
import static org.forgerock.opendj.ldap.schema.SchemaConstants.EMR_AUTH_PASSWORD_EXACT_OID;
import static org.forgerock.opendj.ldap.schema.SchemaConstants.SYNTAX_AUTH_PASSWORD_NAME;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizableMessageBuilder;
import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.DecodeException;

/**
 * This class defines the auth password attribute syntax, which is defined in
 * RFC 3112 and is used to hold authentication information. Only equality
 * matching will be allowed by default.
 */
final class AuthPasswordSyntaxImpl extends AbstractSyntaxImpl {
    /**
     * Decodes the provided authentication password value into its component
     * parts.
     *
     * @param authPasswordValue
     *            The authentication password value to be decoded.
     * @return A three-element array, containing the scheme, authInfo, and
     *         authValue components of the given string, in that order.
     * @throws DecodeException
     *             If a problem is encountered while attempting to decode the
     *             value.
     */
    static StringBuilder[] decodeAuthPassword(final String authPasswordValue)
            throws DecodeException {
        // Create placeholders for the values to return.
        final StringBuilder scheme = new StringBuilder();
        final StringBuilder authInfo = new StringBuilder();
        final StringBuilder authValue = new StringBuilder();

        // First, ignore any leading whitespace.
        final int length = authPasswordValue.length();
        int pos = 0;
        while (pos < length && authPasswordValue.charAt(pos) == ' ') {
            pos++;
        }

        // The next set of characters will be the scheme, which must consist
        // only of digits, uppercase alphabetic characters, dash, period,
        // slash, and underscore characters. It must be immediately followed
        // by one or more spaces or a dollar sign.
    readScheme:
        while (pos < length) {
            final char c = authPasswordValue.charAt(pos);

            switch (c) {
            case '0':
            case '1':
            case '2':
            case '3':
            case '4':
            case '5':
            case '6':
            case '7':
            case '8':
            case '9':
            case 'A':
            case 'B':
            case 'C':
            case 'D':
            case 'E':
            case 'F':
            case 'G':
            case 'H':
            case 'I':
            case 'J':
            case 'K':
            case 'L':
            case 'M':
            case 'N':
            case 'O':
            case 'P':
            case 'Q':
            case 'R':
            case 'S':
            case 'T':
            case 'U':
            case 'V':
            case 'W':
            case 'X':
            case 'Y':
            case 'Z':
            case '-':
            case '.':
            case '/':
            case '_':
                scheme.append(c);
                pos++;
                break;
            case ' ':
            case '$':
                break readScheme;
            default:
                final LocalizableMessage message =
                        ERR_ATTR_SYNTAX_AUTHPW_INVALID_SCHEME_CHAR.get(pos);
                throw DecodeException.error(message);
            }
        }

        // The scheme must consist of at least one character.
        if (scheme.length() == 0) {
            final LocalizableMessage message = ERR_ATTR_SYNTAX_AUTHPW_NO_SCHEME.get();
            throw DecodeException.error(message);
        }

        // Ignore any spaces before the dollar sign separator. Then read the
        // dollar sign and ignore any trailing spaces.
        while (pos < length && authPasswordValue.charAt(pos) == ' ') {
            pos++;
        }

        if (pos < length && authPasswordValue.charAt(pos) == '$') {
            pos++;
        } else {
            final LocalizableMessage message = ERR_ATTR_SYNTAX_AUTHPW_NO_SCHEME_SEPARATOR.get();
            throw DecodeException.error(message);
        }

        while (pos < length && authPasswordValue.charAt(pos) == ' ') {
            pos++;
        }

        // The next component must be the authInfo element, containing only
        // printable characters other than the dollar sign and space
        // character.
    readAuthInfo:
        while (pos < length) {
            final char c = authPasswordValue.charAt(pos);
            if (c == ' ' || c == '$') {
                break readAuthInfo;
            } else if (PrintableStringSyntaxImpl.isPrintableCharacter(c)) {
                authInfo.append(c);
                pos++;
            } else {
                final LocalizableMessage message =
                        ERR_ATTR_SYNTAX_AUTHPW_INVALID_AUTH_INFO_CHAR.get(pos);
                throw DecodeException.error(message);
            }
        }

        // The authInfo element must consist of at least one character.
        if (scheme.length() == 0) {
            final LocalizableMessage message = ERR_ATTR_SYNTAX_AUTHPW_NO_AUTH_INFO.get();
            throw DecodeException.error(message);
        }

        // Ignore any spaces before the dollar sign separator. Then read the
        // dollar sign and ignore any trailing spaces.
        while (pos < length && authPasswordValue.charAt(pos) == ' ') {
            pos++;
        }

        if (pos < length && authPasswordValue.charAt(pos) == '$') {
            pos++;
        } else {
            final LocalizableMessage message = ERR_ATTR_SYNTAX_AUTHPW_NO_AUTH_INFO_SEPARATOR.get();
            throw DecodeException.error(message);
        }

        while (pos < length && authPasswordValue.charAt(pos) == ' ') {
            pos++;
        }

        // The final component must be the authValue element, containing
        // only printable characters other than the dollar sign and space
        // character.
        while (pos < length) {
            final char c = authPasswordValue.charAt(pos);
            if (c == ' ' || c == '$') {
                break;
            } else if (PrintableStringSyntaxImpl.isPrintableCharacter(c)) {
                authValue.append(c);
                pos++;
            } else {
                final LocalizableMessage message =
                        ERR_ATTR_SYNTAX_AUTHPW_INVALID_AUTH_VALUE_CHAR.get(pos);
                throw DecodeException.error(message);
            }
        }

        // The authValue element must consist of at least one character.
        if (scheme.length() == 0) {
            final LocalizableMessage message = ERR_ATTR_SYNTAX_AUTHPW_NO_AUTH_VALUE.get();
            throw DecodeException.error(message);
        }

        // The only characters remaining must be whitespace.
        while (pos < length) {
            final char c = authPasswordValue.charAt(pos);
            if (c == ' ') {
                pos++;
            } else {
                final LocalizableMessage message =
                        ERR_ATTR_SYNTAX_AUTHPW_INVALID_TRAILING_CHAR.get(pos);
                throw DecodeException.error(message);
            }
        }

        // If we've gotten here, then everything must be OK.
        return new StringBuilder[] { scheme, authInfo, authValue };
    }

    /**
     * Indicates whether the provided value is encoded using the auth password
     * syntax.
     *
     * @param value
     *            The value for which to make the determination.
     * @return <CODE>true</CODE> if the value appears to be encoded using the
     *         auth password syntax, or <CODE>false</CODE> if not.
     */
    static boolean isEncoded(final ByteSequence value) {
        // FIXME -- Make this more efficient, and don't use exceptions for
        // flow control.

        try {
            decodeAuthPassword(value.toString());
            return true;
        } catch (final Exception e) {
            return false;
        }
    }

    @Override
    public String getEqualityMatchingRule() {
        return EMR_AUTH_PASSWORD_EXACT_OID;
    }

    public String getName() {
        return SYNTAX_AUTH_PASSWORD_NAME;
    }

    public boolean isHumanReadable() {
        return true;
    }

    public boolean valueIsAcceptable(final Schema schema, final ByteSequence value,
            final LocalizableMessageBuilder invalidReason) {
        try {
            decodeAuthPassword(value.toString());
            return true;
        } catch (final DecodeException de) {
            invalidReason.append(de.getMessageObject());
            return false;
        }
    }
}

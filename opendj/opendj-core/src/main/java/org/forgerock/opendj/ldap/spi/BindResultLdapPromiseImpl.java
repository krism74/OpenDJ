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
 *      Copyright 2009-2010 Sun Microsystems, Inc.
 *      Portions copyright 2011-2014 ForgeRock AS.
 */

package org.forgerock.opendj.ldap.spi;

import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.IntermediateResponseHandler;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.requests.BindClient;
import org.forgerock.opendj.ldap.requests.BindRequest;
import org.forgerock.opendj.ldap.responses.BindResult;
import org.forgerock.opendj.ldap.responses.Responses;
import org.forgerock.util.promise.PromiseImpl;

/**
 * Bind result promise implementation.
 */
public final class BindResultLdapPromiseImpl extends ResultLdapPromiseImpl<BindRequest, BindResult> {
    private final BindClient bindClient;

    BindResultLdapPromiseImpl(final int requestID, final BindRequest request, final BindClient bindClient,
            final IntermediateResponseHandler intermediateResponseHandler,
            final Connection connection) {
        super(new PromiseImpl<BindResult, LdapException>() {
            protected LdapException tryCancel(boolean mayInterruptIfRunning) {
                /*
                 * No other operations can be performed while a bind is active.
                 * Therefore it is not possible to cancel bind or requests,
                 * since doing so will leave the connection in a state which
                 * prevents other operations from being performed.
                 */
                return null;
            }
        }, requestID, request, intermediateResponseHandler, connection);
        this.bindClient = bindClient;
    }

    @Override
    public boolean isBindOrStartTLS() {
        return true;
    }

    /**
     * Returns the bind client.
     *
     * @return The bind client.
     */
    public BindClient getBindClient() {
        return bindClient;
    }

    @Override
    BindResult newErrorResult(final ResultCode resultCode, final String diagnosticMessage,
            final Throwable cause) {
        return Responses.newBindResult(resultCode).setDiagnosticMessage(diagnosticMessage)
                .setCause(cause);
    }
}

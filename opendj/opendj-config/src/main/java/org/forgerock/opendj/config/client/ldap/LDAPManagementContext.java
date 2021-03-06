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
 *      Copyright 2008 Sun Microsystems, Inc.
 *      Portions copyright 2014-2015 ForgeRock AS.
 */

package org.forgerock.opendj.config.client.ldap;

import static org.forgerock.opendj.ldap.Connections.*;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.LDAPProfile;
import org.forgerock.opendj.config.client.ManagementContext;
import org.forgerock.opendj.config.client.spi.Driver;
import org.forgerock.opendj.ldap.AbstractConnectionWrapper;
import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldap.MemoryBackend;
import org.forgerock.opendj.ldap.requests.UnbindRequest;
import org.forgerock.opendj.ldif.LDIF;
import org.forgerock.opendj.ldif.LDIFEntryReader;
import org.forgerock.opendj.ldif.LDIFEntryWriter;
import org.forgerock.util.Reject;

/**
 * An LDAP management connection context.
 */
public final class LDAPManagementContext extends ManagementContext {

    private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

    /**
     * Create a new LDAP management context using the provided LDAP connection.
     *
     * @param connection
     *            The LDAP connection.
     * @param profile
     *            The LDAP profile.
     * @return Returns the new management context.
     */
    public static ManagementContext newManagementContext(Connection connection, LDAPProfile profile) {
        Reject.ifNull(connection, profile);
        LDAPDriver driver = new LDAPDriver(connection, profile);
        LDAPManagementContext context = new LDAPManagementContext(driver);
        driver.setManagementContext(context);
        return context;
    }

    /**
     * Returns a LDIF management context on the provided LDIF file.
     *
     * @param ldifFile
     *            The LDIF file to manage
     * @param profile
     *            The LDAP profile
     * @param exceptions
     *            Contains {@code IOException} that may occurred during management context close.
     *            Could be {@code null}
     * @return A LDIF file management context
     * @throws IOException
     *             If problems occurs while reading the file.
     */
    public static ManagementContext newLDIFManagementContext(final File ldifFile, final LDAPProfile profile,
            final List<IOException> exceptions) throws IOException {
        final BufferedReader configReader = new BufferedReader(new FileReader(ldifFile));
        try {
            final MemoryBackend memoryBackend = new MemoryBackend(new LDIFEntryReader(configReader));
            final Connection co = new AbstractConnectionWrapper<Connection>(newInternalConnection(memoryBackend)) {
                @Override
                public void close() {
                    try {
                        final BufferedWriter configWriter = new BufferedWriter(new FileWriter(ldifFile));
                        try {
                            final Iterator<Entry> entries = memoryBackend.getAll().iterator();
                            entries.next(); // skip RootDSE
                            LDIF.copyTo(LDIF.newEntryIteratorReader(entries), new LDIFEntryWriter(configWriter));
                        } finally {
                            configWriter.close();
                        }
                    } catch (IOException e) {
                        if (exceptions != null) {
                            exceptions.add(e);
                        } else {
                            logger.error(LocalizableMessage.raw(
                                    "IOException occured during LDIF context management close:", e));
                        }
                    }
                }

                @Override
                public void close(UnbindRequest request, String reason) {
                    close();
                }
            };

            // We need to add the root dse entry to make the configuration framework work.
            co.add(LDIFEntryReader.valueOfLDIFEntry("dn:", "objectClass:top", "objectClass:ds-root-dse"));
            return LDAPManagementContext.newManagementContext(co, LDAPProfile.getInstance());
        } finally {
            configReader.close();
        }
    }

    /**
     * Returns a LDIF management context on the provided LDIF file.
     *
     * @param ldifFile
     *            The LDIF file to manage
     * @param profile
     *            The LDAP profile
     * @return A LDIF file management context
     * @throws IOException
     *             If problems occurs while reading the file.
     */
    public static ManagementContext newLDIFManagementContext(final File ldifFile, final LDAPProfile profile)
            throws IOException {
        return newLDIFManagementContext(ldifFile, profile, null);
    }

    /** The LDAP management context driver. */
    private final LDAPDriver driver;

    /** Private constructor. */
    private LDAPManagementContext(LDAPDriver driver) {
        this.driver = driver;
    }

    /** {@inheritDoc} */
    @Override
    protected Driver getDriver() {
        return driver;
    }
}

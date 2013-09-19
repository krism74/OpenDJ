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
 *      Copyright 2013 ForgeRock AS.
 */
package com.forgerock.opendj.ldap.tools;

import static org.fest.assertions.Assertions.*;

import org.forgerock.i18n.LocalizableMessageBuilder;
import org.forgerock.opendj.ldap.ConnectionFactory;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class ConnectionFactoryProviderTest extends ToolsTestCase {

    @Mock
    private ConsoleApplication app;

    private ArgumentParser argParser;

    private ConnectionFactoryProvider connectionFactoryProvider;

    @BeforeMethod
    public void init() throws Exception {
        MockitoAnnotations.initMocks(this);
        argParser = new ArgumentParser("unused", new LocalizableMessageBuilder().toMessage(), false);
        connectionFactoryProvider = new ConnectionFactoryProvider(argParser, app);
    }

    @Test
    /** Issue OPENDJ-734 */
    public void getConnectionFactoryShouldAllowNullTrustStorePassword() throws Exception {
        // provide a trustStorePath but no password
        String trustStorePath = getClass().getClassLoader().getResource("dummy-truststore").getFile();
        argParser.parseArguments(new String[] { "--useStartTLS", "--trustStorePath", trustStorePath });

        ConnectionFactory factory = connectionFactoryProvider.getConnectionFactory();

        assertThat(factory).isNotNull();
    }

}
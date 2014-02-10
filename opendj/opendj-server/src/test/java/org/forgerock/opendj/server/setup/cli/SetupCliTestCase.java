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
 *      Copyright 2014 ForgeRock AS.
 */
package org.forgerock.opendj.server.setup.cli;

import static com.forgerock.opendj.cli.Utils.MAX_LINE_WIDTH;
import static com.forgerock.opendj.cli.Utils.wrapText;
import static com.forgerock.opendj.cli.CliMessages.*;
import static org.fest.assertions.Assertions.assertThat;
import static org.forgerock.util.Utils.closeSilently;
import static org.testng.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;

import org.forgerock.i18n.LocalizableMessage;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import com.forgerock.opendj.cli.ReturnCode;
import com.forgerock.opendj.cli.Utils;

/**
 * This class tests the setup CLI functionality.
 */
public class SetupCliTestCase extends AbstractSetupCliTestCase {

    // @formatter:off
    @DataProvider(name = "validArguments")
    Object[][] createValidArguments() throws Exception {
        Object[][] data = new Object[][] {
            { args("--help"),
                expectedErrOutput(INFO_SETUP_DESCRIPTION.get()) },
            { args("--cli", "create-directory-server", "--doNotStart", "--ldapPort", "1389",
                "--adminConnectorPort", "4444",
                "-D", "cn=Directory Manager", "-w", "password", "-b", "dc=example,dc=com",
                "-a"), expectedErrOutput(LocalizableMessage.EMPTY) },
        };
        return data;
    }

    @DataProvider(name = "invalidArguments")
    Object[][] createInValidArguments() throws Exception {
        Object[][] data = new Object[][] {
            { args("-c"),
                expectedErrOutput(
                        ERR_ERROR_PARSING_ARGS.get(ERR_SUBCMDPARSER_NO_GLOBAL_ARGUMENT_FOR_SHORT_ID.get("c"))) },
            { args("-N"), expectedErrOutput(ERR_ERROR_PARSING_ARGS.get(
                    ERR_ARGPARSER_NO_VALUE_FOR_ARGUMENT_WITH_SHORT_ID.get("N"))) },
        };
        return data;
    }

    @DataProvider(name = "validPorts")
    Object[][] createValidPorts() throws Exception {
        Object[][] data = new Object[][] {
            { args("--cli", "--doNotStart", "--ldapPort", "1389", "--adminConnectorPort", "4444",
                    "-D", "cn=Directory Manager", "-w", "password", "-b", "dc=example,dc=com",
                    "-a"),
                null },
        };
        return data;
    }

    @DataProvider(name = "invalidPorts")
    Object[][] createInValidPorts() throws Exception {
        Object[][] data = new Object[][] {
            { args("--cli", "--doNotStart", "--ldapPort", "1389", "--adminConnectorPort", "4444",
                    "-D", "cn=Directory Manager", "-w", "password", "-b", "dc=example,dc=com",
                    "-a", "--jmxPort", "1389"),
                expectedErrOutput(
                        ERR_CANNOT_INITIALIZE_ARGS.get(ERR_PORT_ALREADY_SPECIFIED.get("1389"))) },
            { args("--cli", "--doNotStart", "--ldapPort", "1389", "--adminConnectorPort", "4444",
                    "-D", "cn=Directory Manager", "-w", "password", "-b", "dc=example,dc=com",
                    "-a", "--ldapsPort", "1389"),
                expectedErrOutput(
                        ERR_CANNOT_INITIALIZE_ARGS.get(ERR_PORT_ALREADY_SPECIFIED.get("1389"))) },
            { args("--cli", "--doNotStart", "--ldapPort", "70000", "--adminConnectorPort", "4444",
                    "-D", "cn=Directory Manager", "-w", "password", "-b", "dc=example,dc=com",
                    "-a"),
                expectedErrOutput(ERR_ERROR_PARSING_ARGS.get(
                        ERR_ARGPARSER_VALUE_UNACCEPTABLE_FOR_LONG_ID.get(70000, "ldapPort",
                        ERR_INTARG_VALUE_ABOVE_UPPER_BOUND.get("ldapPort", 70000, 65535)))) },
            { args("--cli", "--doNotStart", "--ldapPort", "-1", "--adminConnectorPort", "4444",
                    "-D", "cn=Directory Manager", "-w", "password", "-b", "dc=example,dc=com",
                    "-a"),
                expectedErrOutput(ERR_ERROR_PARSING_ARGS.get(
                        ERR_ARGPARSER_VALUE_UNACCEPTABLE_FOR_LONG_ID.get(-1, "ldapPort",
                                ERR_INTARG_VALUE_BELOW_LOWER_BOUND.get("ldapPort", -1, 1)))) },
        };
        return data;
    }
    // @formatter:on

    @Test(dataProvider = "validArguments")
    public void testRunValidArguments(String[] arguments, LocalizableMessage expectedErrOutput) throws Exception {
        run(arguments, true, expectedErrOutput);
    }

    @Test(dataProvider = "invalidArguments")
    public void testRunInvalidArguments(String[] arguments, LocalizableMessage expectedErrOutput) throws Exception {
        run(arguments, false, expectedErrOutput);
    }

    @Test(dataProvider = "validPorts")
    public void testcheckValidProvidedPorts(String[] arguments, LocalizableMessage expectedErrOutput) throws Exception {
        run(arguments, true, expectedErrOutput);
    }

    @Test(dataProvider = "invalidPorts")
    public void testcheckInvalidProvidedPorts(String[] arguments, LocalizableMessage expectedErrOutput)
            throws Exception {
        run(arguments, false, expectedErrOutput);
    }

    private void run(final String[] arguments, final boolean shouldSucceed, final LocalizableMessage expectedErrOutput)
            throws UnsupportedEncodingException {
        PrintStream outStream = null;
        PrintStream errStream = null;
        int resultCode = 0;
        try {
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            outStream = new PrintStream(out);
            final ByteArrayOutputStream err = new ByteArrayOutputStream();
            errStream = new PrintStream(err);

            final SetupCli setup = new SetupCli(outStream, errStream);
            resultCode = setup.run(arguments);

            if (shouldSucceed) {
                if (expectedErrOutput != null) {
                    assertThat(out.toString("UTF-8")).contains(wrapText(expectedErrOutput, MAX_LINE_WIDTH));
                }
                assertThat(err.size()).isEqualTo(0);
                assertThat(resultCode).isEqualTo(ReturnCode.SUCCESS.get());
            } else {
                assertThat(resultCode).isNotEqualTo(ReturnCode.SUCCESS.get());
                assertThat(err.size()).isGreaterThan(0);
                final String errorMsg = err.toString("UTF-8").replaceAll(Utils.LINE_SEPARATOR, " ");
                final String expectedMsg = expectedErrOutput.toString().replaceAll(Utils.LINE_SEPARATOR, " ");
                assertTrue(errorMsg.contains(expectedMsg), errorMsg + "\n >---< \n" + expectedMsg);
            }
        } finally {
            closeSilently(outStream, errStream);
        }
    }

    /** Arguments passed to the command */
    private String[] args(String... arguments) {
        return arguments;
    }

    /** A message the error output is expected to contain. */
    private LocalizableMessage expectedErrOutput(LocalizableMessage val) {
        return val;
    }
}
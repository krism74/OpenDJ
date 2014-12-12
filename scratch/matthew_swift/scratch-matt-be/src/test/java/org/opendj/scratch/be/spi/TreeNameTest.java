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
 *      Copyright 2014 ForgeRock AS
 */
package org.opendj.scratch.be.spi;

import org.opendj.scratch.be.spi.TreeName;
import org.testng.annotations.Test;

import static junit.framework.Assert.*;

@SuppressWarnings("javadoc")
public class TreeNameTest {

    @Test
    public void treeName() {
        final TreeName suffix = TreeName.of("dc=example,dc=com");
        final TreeName id2entry = suffix.child("id2entry");
        assertEquals(id2entry, TreeName.of("dc=example,dc=com", "id2entry"));
        assertTrue(suffix.isSuffixOf(id2entry));
        assertFalse(id2entry.isSuffixOf(suffix));
    }
}

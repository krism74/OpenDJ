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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 *      Portions copyright 2013 ForgeRock AS
 */
package org.opends.server.protocols.ldap ;

import static org.opends.server.config.ConfigConstants.*;

import java.util.Iterator;
import java.util.List;

import org.opends.server.DirectoryServerTestCase;
import org.opends.server.TestCaseUtils;
import org.opends.server.admin.server.AdminTestCaseUtils;
import org.opends.server.admin.std.meta.LDAPConnectionHandlerCfgDefn;
import org.opends.server.admin.std.server.LDAPConnectionHandlerCfg;
import org.opends.server.config.ConfigException;
import org.opends.server.types.Attribute;
import org.opends.server.types.Attributes;
import org.opends.server.types.Entry;
import org.testng.annotations.Test;

/**
 * An abstract class that all types  unit test should extend.
 */
@Test(groups = { "precommit", "ldap" }, sequential = true)
public abstract class LdapTestCase extends DirectoryServerTestCase
{

  /**
   * Determine whether one LDAPAttribute is equal to another.
   * The values of the attribute must be identical and in the same order.
   * @param a1 The first LDAPAttribute.
   * @param a2 The second LDAPAttribute.
   * @return true if the first LDAPAttribute is equal to the second.
   */
  static boolean testEqual(LDAPAttribute a1, LDAPAttribute a2)
  {
    if (a1.getAttributeType().equals(a2.getAttributeType()))
    {
      return a1.getValues().equals(a2.getValues());
    }
    return false;
  }

  /**
   * Determine whether one list of LDAPAttribute is equal to another.
   * @param list1 The first list of LDAPAttribute.
   * @param list2 The second list of LDAPAttribute.
   * @return true if the first list of LDAPAttribute is equal to the second.
   */
  static boolean testEqual(List<LDAPAttribute> list1, List<LDAPAttribute> list2)
  {
    Iterator<LDAPAttribute> e1 = list1.iterator();
    Iterator<LDAPAttribute> e2 = list2.iterator();
    while(e1.hasNext() && e2.hasNext()) {
      LDAPAttribute o1 = e1.next();
      LDAPAttribute o2 = e2.next();
      if (o1 == null ? o2 != null : !testEqual(o1, o2))
        return false;
    }
    return !e1.hasNext() && !e2.hasNext();
  }

  /**
   * Test toString methods.
   * @param op The op.
   * @throws Exception If the toString method fails.
   */
  static void toString(ProtocolOp op) throws Exception
  {
	  StringBuilder sb = new StringBuilder();
	  op.toString(sb);
	  op.toString(sb, 1);
  }

  /**
   * Generate a LDAPConnectionHandler from a entry. The listen port is
   * determined automatically, so no ATTR_LISTEN_PORT should be in the
   * entry.
   *
   * @param handlerEntry The entry to be used to configure the handle.
   * @return Returns the new LDAP connection handler.
   * @throws Exception if the handler cannot be initialized.
   */
  static LDAPConnectionHandler getLDAPHandlerInstance(Entry handlerEntry)
      throws Exception
  {
    long serverLdapPort = TestCaseUtils.findFreePort();
    Attribute a = Attributes.create(ATTR_LISTEN_PORT, String.valueOf(serverLdapPort));
    handlerEntry.addAttribute(a, null);
    LDAPConnectionHandlerCfg config = getConfiguration(handlerEntry);
    LDAPConnectionHandler handler = new LDAPConnectionHandler();
    handler.initializeConnectionHandler(config);
    return handler;
  }

  /**
   * Decode an LDAP connection handler configuration entry.
   *
   * @param handlerEntry
   *          The configuration entry.
   * @return Returns the decoded LDAP connection handler
   *         configuration.
   * @throws ConfigException
   *           If the configuration entry could not be decoded.
   */
  static LDAPConnectionHandlerCfg getConfiguration(
      Entry handlerEntry) throws ConfigException {
    return AdminTestCaseUtils.getConfiguration(
        LDAPConnectionHandlerCfgDefn.getInstance(), handlerEntry);
  }

}
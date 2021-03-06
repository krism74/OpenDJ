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
 */
package org.opends.server.schema;

import org.opends.server.api.EqualityMatchingRule;
import org.opends.server.schema.BooleanEqualityMatchingRuleTest;
import org.testng.annotations.DataProvider;

/**
 * Test the BooleanEqualityMatchingRule.
 */
public class BooleanEqualityMatchingRuleTest extends EqualityMatchingRuleTest
{

  /**
   * {@inheritDoc}
   */
  @Override
  @DataProvider(name= "equalitymatchingrules")
  public Object[][] createEqualityMatchingRuleTest()
  { 
    return new Object[][] {
        {"TRUE",  "true",  true},
        {"YES",   "true",  true},
        {"ON",    "true",  true},
        {"1",     "true",  true},
        {"FALSE", "false", true},
        {"NO",    "false", true},
        {"OFF",   "false", true},
        {"0",     "false", true},
        {"TRUE",  "false", false},
    };
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @DataProvider(name= "equalityMatchingRuleInvalidValues")
  public Object[][] createEqualityMatchingRuleInvalidValues()
  {
    return new Object[][] {
        {"garbage"},
    };
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected EqualityMatchingRule getRule()
  {
    return new BooleanEqualityMatchingRule();
  }

}

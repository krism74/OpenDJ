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
 */


package org.opends.server.schema;

import java.util.Collection;
import java.util.Collections;
import org.opends.server.api.MatchingRuleFactory;
import org.opends.server.admin.std.server.MatchingRuleCfg;
import org.opends.server.api.MatchingRule;
import org.opends.server.config.ConfigException;
import org.opends.server.types.InitializationException;

/**
 * This class is a factory class for
 * {@link PresentationAddressEqualityMatchingRule}.
 */
public final class PresentationAddressEqualityMatchingRuleFactory
        extends MatchingRuleFactory<MatchingRuleCfg>
{
  //Associated Matching Rule.
  private MatchingRule matchingRule;



 /**
  * {@inheritDoc}
  */
 @Override
 public final void initializeMatchingRule(MatchingRuleCfg configuration)
         throws ConfigException, InitializationException
 {
    matchingRule = new PresentationAddressEqualityMatchingRule();
 }



 /**
  * {@inheritDoc}
  */
 @Override
 public final Collection<MatchingRule> getMatchingRules()
 {
    return Collections.singleton(matchingRule);
 }
}

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
 *      Copyright 2008-2009 Sun Microsystems, Inc.
 *      Portions Copyright 2012-2014 ForgeRock AS
 */
package org.opends.server.extensions;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ConditionResult;
import org.forgerock.opendj.ldap.ResultCode;
import org.opends.server.admin.std.server.EntryUUIDVirtualAttributeCfg;
import org.opends.server.api.MatchingRule;
import org.opends.server.api.VirtualAttributeProvider;
import org.opends.server.core.SearchOperation;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.AttributeValues;
import org.opends.server.types.Entry;
import org.opends.server.types.VirtualAttributeRule;

import static org.opends.messages.ExtensionMessages.*;
import static org.opends.server.util.StaticUtils.*;

/**
 * This class implements a virtual attribute provider that is meant to serve the
 * entryUUID operational attribute as described in RFC 4530.  Note that this
 * should only be used for entries used in conjunction with data in private
 * backends (e.g., those holding the configuration, schema, monitor, and root
 * DSE entries).  Real user data should have entry UUID values generated at the
 * time the entries are added or imported.
 */
public class EntryUUIDVirtualAttributeProvider
       extends VirtualAttributeProvider<EntryUUIDVirtualAttributeCfg>
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /**
   * Creates a new instance of this entryUUID virtual attribute provider.
   */
  public EntryUUIDVirtualAttributeProvider()
  {
    super();

    // All initialization should be performed in the
    // initializeVirtualAttributeProvider method.
  }

  /** {@inheritDoc} */
  @Override()
  public boolean isMultiValued()
  {
    return false;
  }

  /** {@inheritDoc} */
  @Override()
  public Set<AttributeValue> getValues(Entry entry,
                                       VirtualAttributeRule rule)
  {
    String normDNString = entry.getName().toNormalizedString();
    String uuidString =
         UUID.nameUUIDFromBytes(getBytes(normDNString)).toString();
    AttributeValue value = AttributeValues.create(
         ByteString.valueOf(uuidString),
         ByteString.valueOf(uuidString));
    return Collections.singleton(value);
  }

  /** {@inheritDoc} */
  @Override()
  public boolean hasValue(Entry entry, VirtualAttributeRule rule)
  {
    // This virtual attribute provider will always generate a value.
    return true;
  }

  /** {@inheritDoc} */
  @Override()
  public boolean hasValue(Entry entry, VirtualAttributeRule rule,
                          AttributeValue value)
  {
    MatchingRule matchingRule =
        rule.getAttributeType().getEqualityMatchingRule();
    try
    {
      String normalizedDN = entry.getName().toNormalizedString();
      String uuidString =
           UUID.nameUUIDFromBytes(getBytes(normalizedDN)).toString();

      ByteString normValue = matchingRule.normalizeAttributeValue(value.getValue());
      return uuidString.equals(normValue.toString());
    }
    catch (Exception e)
    {
      logger.traceException(e);
      return false;
    }
  }

  /** {@inheritDoc} */
  @Override()
  public ConditionResult matchesSubstring(Entry entry,
                                          VirtualAttributeRule rule,
                                          ByteString subInitial,
                                          List<ByteString> subAny,
                                          ByteString subFinal)
  {
    // DNs cannot be used in substring matching.
    return ConditionResult.UNDEFINED;
  }

  /** {@inheritDoc} */
  @Override()
  public ConditionResult greaterThanOrEqualTo(Entry entry,
                              VirtualAttributeRule rule,
                              AttributeValue value)
  {
    // DNs cannot be used in ordering matching.
    return ConditionResult.UNDEFINED;
  }

  /** {@inheritDoc} */
  @Override()
  public ConditionResult lessThanOrEqualTo(Entry entry,
                              VirtualAttributeRule rule,
                              AttributeValue value)
  {
    // DNs cannot be used in ordering matching.
    return ConditionResult.UNDEFINED;
  }

  /** {@inheritDoc} */
  @Override()
  public ConditionResult approximatelyEqualTo(Entry entry,
                              VirtualAttributeRule rule,
                              AttributeValue value)
  {
    // DNs cannot be used in approximate matching.
    return ConditionResult.UNDEFINED;
  }

  /** {@inheritDoc} */
  @Override()
  public boolean isSearchable(VirtualAttributeRule rule,
                              SearchOperation searchOperation,
                              boolean isPreIndexed)
  {
    return false;
  }

  /** {@inheritDoc} */
  @Override()
  public void processSearch(VirtualAttributeRule rule,
                            SearchOperation searchOperation)
  {
    searchOperation.setResultCode(ResultCode.UNWILLING_TO_PERFORM);

    LocalizableMessage message = ERR_ENTRYUUID_VATTR_NOT_SEARCHABLE.get(
            rule.getAttributeType().getNameOrOID());
    searchOperation.appendErrorMessage(message);
  }
}


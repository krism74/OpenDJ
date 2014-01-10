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
 *      Portions Copyright 2011-2014 ForgeRock AS
 */
package org.opends.server.replication.common;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.opends.messages.Message;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.server.UserDefinedVirtualAttributeCfg;
import org.opends.server.api.VirtualAttributeProvider;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.SearchOperation;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.replication.server.ReplicationServer;
import org.opends.server.types.*;
import org.forgerock.opendj.ldap.ByteString;
import org.opends.server.workflowelement.externalchangelog.ECLWorkflowElement;

import static org.opends.messages.ExtensionMessages.*;
import static org.opends.server.loggers.debug.DebugLogger.*;

/**
 * Virtual attribute returning the newest change number from the changelogDB.
 */
public class LastChangeNumberVirtualAttributeProvider
       extends VirtualAttributeProvider<UserDefinedVirtualAttributeCfg>
       implements ConfigurationChangeListener<UserDefinedVirtualAttributeCfg>
{
  /** The tracer object for the debug logger. */
  private static final DebugTracer TRACER = getTracer();

  /**
   * Creates a new instance of this member virtual attribute provider.
   */
  public LastChangeNumberVirtualAttributeProvider()
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
  @Override
  public boolean hasValue(Entry entry, VirtualAttributeRule rule)
  {
    // There's only a value for the rootDSE, i.e. the Null DN.
    return entry.getName().isRootDN();
  }

  /** {@inheritDoc} */
  @Override()
  public Set<AttributeValue> getValues(Entry entry,VirtualAttributeRule rule)
  {
    String value = "0";
    try
    {
      ECLWorkflowElement eclwe = (ECLWorkflowElement)
      DirectoryServer.getWorkflowElement("EXTERNAL CHANGE LOG");
      if (eclwe != null)
      {
        final ReplicationServer rs = eclwe.getReplicationServer();
        final long[] limits = rs.getECLChangeNumberLimits();
        value = String.valueOf(limits[1]);
      }
    }
    catch(Exception e)
    {
      // We got an error computing this change number.
      // Rather than returning 0 which is no change, return -1 to
      // indicate the error.
      value = "-1";
      TRACER.debugCaught(DebugLogLevel.ERROR, e);
    }
    ByteString valueBS = ByteString.valueOf(value);
    return Collections.singleton(AttributeValues.create(valueBS, valueBS));
  }

  /** {@inheritDoc} */
  @Override()
  public boolean isSearchable(VirtualAttributeRule rule,
                              SearchOperation searchOperation,
                              boolean isPreIndexed)
  {
    // We do not allow search for this change number. It's a read-only
    // attribute of the RootDSE.
    return false;
  }

  /** {@inheritDoc} */
  @Override()
  public void processSearch(VirtualAttributeRule rule,
                            SearchOperation searchOperation)
  {
    searchOperation.setResultCode(ResultCode.UNWILLING_TO_PERFORM);
    final Message message = ERR_LASTCHANGENUMBER_VATTR_NOT_SEARCHABLE.get(
            rule.getAttributeType().getNameOrOID());
    searchOperation.appendErrorMessage(message);
  }

  /** {@inheritDoc} */
  @Override
  public boolean isConfigurationChangeAcceptable(
                      UserDefinedVirtualAttributeCfg configuration,
                      List<Message> unacceptableReasons)
  {
    return false;
  }

  /** {@inheritDoc} */
  @Override
  public ConfigChangeResult applyConfigurationChange(
                                 UserDefinedVirtualAttributeCfg configuration)
  {
    return new ConfigChangeResult(ResultCode.OTHER, false);
  }
}


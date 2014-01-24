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
 *      Portions Copyright 2011-2014 ForgeRock AS
 */
package org.opends.server.plugins;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import org.forgerock.i18n.LocalizableMessage;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.meta.PluginCfgDefn;
import org.opends.server.admin.std.server.PluginCfg;
import org.opends.server.admin.std.server.UniqueAttributePluginCfg;
import org.opends.server.api.AlertGenerator;
import org.opends.server.api.Backend;
import org.opends.server.api.plugin.*;
import org.opends.server.api.plugin.PluginResult.PostOperation;
import org.opends.server.api.plugin.PluginResult.PreOperation;
import org.opends.server.config.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.schema.SchemaConstants;
import org.opends.server.types.*;
import org.opends.server.types.operation.*;

import static org.opends.messages.PluginMessages.*;
import static org.opends.server.loggers.debug.DebugLogger.*;
import static org.opends.server.util.ServerConstants.*;

/**
 * This class implements a Directory Server plugin that can be used to ensure
 * that all values for a given attribute or set of attributes are unique within
 * the server (or optionally, below a specified set of base DNs).  It will
 * examine all add, modify, and modify DN operations to determine whether any
 * new conflicts are introduced.  If a conflict is detected then the operation
 * will be rejected, unless that operation is being applied through
 * synchronization in which case an alert will be generated to notify
 * administrators of the problem.
 */
public class UniqueAttributePlugin
        extends DirectoryServerPlugin<UniqueAttributePluginCfg>
        implements ConfigurationChangeListener<UniqueAttributePluginCfg>,
                   AlertGenerator
{
  /**
   * The debug log tracer that will be used for this plugin.
   */
  private static final DebugTracer TRACER = getTracer();



  /**
   * The set of attributes that will be requested when performing internal
   * search operations.  This indicates that no attributes should be returned.
   */
  private static final Set<String> SEARCH_ATTRS = new LinkedHashSet<String>(1);
  static
  {
    SEARCH_ATTRS.add(SchemaConstants.NO_ATTRIBUTES);
  }



  /** Current plugin configuration. */
  private UniqueAttributePluginCfg currentConfiguration;



  /**
   * The data structure to store the mapping between the attribute value and the
   * corresponding dn.
   */
  private ConcurrentHashMap<AttributeValue,DN> uniqueAttrValue2Dn;



  /**
   * {@inheritDoc}
   */
  @Override()
  public final void initializePlugin(Set<PluginType> pluginTypes,
                                     UniqueAttributePluginCfg configuration)
          throws ConfigException
  {
    configuration.addUniqueAttributeChangeListener(this);
    currentConfiguration = configuration;

    for (PluginType t : pluginTypes)
    {
      switch (t)
      {
        case PRE_OPERATION_ADD:
        case PRE_OPERATION_MODIFY:
        case PRE_OPERATION_MODIFY_DN:
        case POST_OPERATION_ADD:
        case POST_OPERATION_MODIFY:
        case POST_OPERATION_MODIFY_DN:
        case POST_SYNCHRONIZATION_ADD:
        case POST_SYNCHRONIZATION_MODIFY:
        case POST_SYNCHRONIZATION_MODIFY_DN:
          // These are acceptable.
          break;

        default:
          LocalizableMessage message =
                  ERR_PLUGIN_UNIQUEATTR_INVALID_PLUGIN_TYPE.get(t.toString());
          throw new ConfigException(message);

      }
    }

    Set<DN> cfgBaseDNs = configuration.getBaseDN();
    if ((cfgBaseDNs == null) || cfgBaseDNs.isEmpty())
    {
      cfgBaseDNs = DirectoryServer.getPublicNamingContexts().keySet();
    }

    for (AttributeType t : configuration.getType())
    {
      for (DN baseDN : cfgBaseDNs)
      {
        Backend b = DirectoryServer.getBackend(baseDN);
        if ((b != null) && (! b.isIndexed(t, IndexType.EQUALITY)))
        {
          throw new ConfigException(ERR_PLUGIN_UNIQUEATTR_ATTR_UNINDEXED.get(
                                         configuration.dn().toString(),
                                         t.getNameOrOID(),
                                         b.getBackendID()));
        }
      }
    }

    uniqueAttrValue2Dn  = new ConcurrentHashMap<AttributeValue,DN>();
    DirectoryServer.registerAlertGenerator(this);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public final void finalizePlugin()
  {
    currentConfiguration.removeUniqueAttributeChangeListener(this);
    DirectoryServer.deregisterAlertGenerator(this);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public final PluginResult.PreOperation
               doPreOperation(PreOperationAddOperation addOperation)
  {
    UniqueAttributePluginCfg config = currentConfiguration;
    Entry entry = addOperation.getEntryToAdd();

    Set<DN> baseDNs = getBaseDNs(config, entry.getName());
    if (baseDNs == null)
    {
      // The entry is outside the scope of this plugin.
      return PluginResult.PreOperation.continueOperationProcessing();
    }

    DN entryDN = entry.getName();
    List<AttributeValue> recordedValues = new LinkedList<AttributeValue>();
    for (AttributeType t : config.getType())
    {
      List<Attribute> attrList = entry.getAttribute(t);
      if (attrList != null)
      {
        for (Attribute a : attrList)
        {
          for (AttributeValue v : a)
          {
            PreOperation stop =
                checkUniqueness(entryDN, t, v, baseDNs, recordedValues, config);
            if (stop != null)
            {
              return stop;
            }
          }
        }
      }
    }

    return PluginResult.PreOperation.continueOperationProcessing();
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public final PluginResult.PreOperation
               doPreOperation(PreOperationModifyOperation modifyOperation)
  {
    UniqueAttributePluginCfg config = currentConfiguration;
    DN entryDN = modifyOperation.getEntryDN();

    Set<DN> baseDNs = getBaseDNs(config, entryDN);
    if (baseDNs == null)
    {
      // The entry is outside the scope of this plugin.
      return PluginResult.PreOperation.continueOperationProcessing();
    }

    List<AttributeValue> recordedValues = new LinkedList<AttributeValue>();
    for (Modification m : modifyOperation.getModifications())
    {
      Attribute a = m.getAttribute();
      AttributeType t = a.getAttributeType();
      if (! config.getType().contains(t))
      {
        // This modification isn't for a unique attribute.
        continue;
      }

      switch (m.getModificationType())
      {
        case ADD:
        case REPLACE:
          for (AttributeValue v : a)
          {
            PreOperation stop =
              checkUniqueness(entryDN, t, v, baseDNs, recordedValues, config);
            if (stop != null)
            {
              return stop;
            }
          }
          break;

        case INCREMENT:
          // We could calculate the new value, but we'll just take it from the
          // updated entry.
          List<Attribute> attrList =
               modifyOperation.getModifiedEntry().getAttribute(t,
                                                               a.getOptions());
          if (attrList != null)
          {
            for (Attribute updatedAttr : attrList)
            {
              if (! updatedAttr.optionsEqual(a.getOptions()))
              {
                continue;
              }

              for (AttributeValue v : updatedAttr)
              {
                PreOperation stop = checkUniqueness(
                    entryDN, t, v, baseDNs, recordedValues, config);
                if (stop != null)
                {
                  return stop;
                }
              }
            }
          }
          break;

        default:
          // We don't need to look at this modification because it's not a
          // modification type of interest.
          continue;
      }
    }

    return PluginResult.PreOperation.continueOperationProcessing();
  }



  private PreOperation checkUniqueness(DN entryDN, AttributeType t,
      AttributeValue v, Set<DN> baseDNs, List<AttributeValue> recordedValues,
      UniqueAttributePluginCfg config)
  {
    try
    {
      //Raise an exception if a conflicting concurrent operation is
      //in progress. Otherwise, store this attribute value with its
      //corresponding DN and proceed.
      DN conflictDN = uniqueAttrValue2Dn.putIfAbsent(v, entryDN);
      if (conflictDN == null)
      {
        recordedValues.add(v);
        conflictDN = getConflictingEntryDN(baseDNs, entryDN,
                                            config, v);
      }
      if (conflictDN != null)
      {
        // Before returning, we need to remove all values added
        // in the uniqueAttrValue2Dn map, because PostOperation
        // plugin does not get called.
        for (AttributeValue v2 : recordedValues)
        {
          uniqueAttrValue2Dn.remove(v2);
        }
        LocalizableMessage msg = ERR_PLUGIN_UNIQUEATTR_ATTR_NOT_UNIQUE.get(
            t.getNameOrOID(), v.getValue().toString(),
            conflictDN.toString());
        return PluginResult.PreOperation.stopProcessing(
            ResultCode.CONSTRAINT_VIOLATION, msg);
      }
    }
    catch (DirectoryException de)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, de);
      }

      LocalizableMessage message =
          ERR_PLUGIN_UNIQUEATTR_INTERNAL_ERROR.get(
                             de.getResultCode().toString(),
                             de.getMessageObject());

      // Try some cleanup before returning, to avoid memory leaks
      for (AttributeValue v2 : recordedValues)
      {
        uniqueAttrValue2Dn.remove(v2);
      }

      return PluginResult.PreOperation.stopProcessing(
          DirectoryServer.getServerErrorResultCode(), message);
    }
    return null;
  }

  /**
   * {@inheritDoc}
   */
  @Override()
  public final PluginResult.PreOperation doPreOperation(
                    PreOperationModifyDNOperation modifyDNOperation)
  {
    UniqueAttributePluginCfg config = currentConfiguration;

    Set<DN> baseDNs = getBaseDNs(config,
                                 modifyDNOperation.getUpdatedEntry().getName());
    if (baseDNs == null)
    {
      // The entry is outside the scope of this plugin.
      return PluginResult.PreOperation.continueOperationProcessing();
    }

    List<AttributeValue> recordedValues = new LinkedList<AttributeValue>();
    RDN newRDN = modifyDNOperation.getNewRDN();
    for (int i=0; i < newRDN.getNumValues(); i++)
    {
      AttributeType t = newRDN.getAttributeType(i);
      if (! config.getType().contains(t))
      {
        // We aren't interested in this attribute type.
        continue;
      }

      AttributeValue v = newRDN.getAttributeValue(i);
      DN entryDN = modifyDNOperation.getEntryDN();
      PreOperation stop =
          checkUniqueness(entryDN, t, v, baseDNs, recordedValues, config);
      if (stop != null)
      {
        return stop;
      }
    }

    return PluginResult.PreOperation.continueOperationProcessing();
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public final void doPostSynchronization(
                         PostSynchronizationAddOperation addOperation)
  {
    UniqueAttributePluginCfg config = currentConfiguration;
    Entry entry = addOperation.getEntryToAdd();

    Set<DN> baseDNs = getBaseDNs(config, entry.getName());
    if (baseDNs == null)
    {
      // The entry is outside the scope of this plugin.
      return;
    }

    DN entryDN = entry.getName();
    for (AttributeType t : config.getType())
    {
      List<Attribute> attrList = entry.getAttribute(t);
      if (attrList != null)
      {
        for (Attribute a : attrList)
        {
          for (AttributeValue v : a)
          {
            sendAlertForUnresolvedConflict(addOperation, entryDN, entryDN, t,
                v, baseDNs, config);
          }
        }
      }
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public final void doPostSynchronization(
                         PostSynchronizationModifyOperation modifyOperation)
  {
    UniqueAttributePluginCfg config = currentConfiguration;
    DN entryDN = modifyOperation.getEntryDN();

    Set<DN> baseDNs = getBaseDNs(config, entryDN);
    if (baseDNs == null)
    {
      // The entry is outside the scope of this plugin.
      return;
    }

    for (Modification m : modifyOperation.getModifications())
    {
      Attribute a = m.getAttribute();
      AttributeType t = a.getAttributeType();
      if (! config.getType().contains(t))
      {
        // This modification isn't for a unique attribute.
        continue;
      }

      switch (m.getModificationType())
      {
        case ADD:
        case REPLACE:
          for (AttributeValue v : a)
          {
            sendAlertForUnresolvedConflict(modifyOperation, entryDN, entryDN, t,
                v, baseDNs, config);
          }
          break;

        case INCREMENT:
          // We could calculate the new value, but we'll just take it from the
          // updated entry.
          List<Attribute> attrList =
               modifyOperation.getModifiedEntry().getAttribute(t,
                                                               a.getOptions());
          if (attrList != null)
          {
            for (Attribute updatedAttr : attrList)
            {
              if (! updatedAttr.optionsEqual(a.getOptions()))
              {
                continue;
              }

              for (AttributeValue v : updatedAttr)
              {
                sendAlertForUnresolvedConflict(modifyOperation, entryDN,
                    entryDN, t, v, baseDNs, config);
              }
            }
          }
          break;

        default:
          // We don't need to look at this modification because it's not a
          // modification type of interest.
          continue;
      }
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public final void doPostSynchronization(
                         PostSynchronizationModifyDNOperation modifyDNOperation)
  {
    UniqueAttributePluginCfg config = currentConfiguration;

    Set<DN> baseDNs = getBaseDNs(config,
                                 modifyDNOperation.getUpdatedEntry().getName());
    if (baseDNs == null)
    {
      // The entry is outside the scope of this plugin.
      return;
    }

    DN entryDN = modifyDNOperation.getEntryDN();
    DN updatedEntryDN = modifyDNOperation.getUpdatedEntry().getName();
    RDN newRDN = modifyDNOperation.getNewRDN();
    for (int i=0; i < newRDN.getNumValues(); i++)
    {
      AttributeType t = newRDN.getAttributeType(i);
      if (! config.getType().contains(t))
      {
        // We aren't interested in this attribute type.
        continue;
      }

      AttributeValue v = newRDN.getAttributeValue(i);
      sendAlertForUnresolvedConflict(modifyDNOperation, entryDN,
          updatedEntryDN, t, v, baseDNs, config);
    }
  }



  private void sendAlertForUnresolvedConflict(PluginOperation operation,
      DN entryDN, DN updatedEntryDN, AttributeType t, AttributeValue v,
      Set<DN> baseDNs, UniqueAttributePluginCfg config)
  {
    try
    {
      DN conflictDN = uniqueAttrValue2Dn.get(v);
      if (conflictDN == null)
      {
        conflictDN = getConflictingEntryDN(baseDNs, entryDN, config, v);
      }
      if (conflictDN != null)
      {
        LocalizableMessage message = ERR_PLUGIN_UNIQUEATTR_SYNC_NOT_UNIQUE.get(
                               t.getNameOrOID(),
                               operation.getConnectionID(),
                               operation.getOperationID(),
                               v.getValue().toString(),
                               updatedEntryDN.toString(),
                               conflictDN.toString());
        DirectoryServer.sendAlertNotification(this,
                             ALERT_TYPE_UNIQUE_ATTR_SYNC_CONFLICT,
                             message);
      }
    }
    catch (DirectoryException de)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, de);
      }

      LocalizableMessage message = ERR_PLUGIN_UNIQUEATTR_INTERNAL_ERROR_SYNC.get(
                            operation.getConnectionID(),
                            operation.getOperationID(),
                            updatedEntryDN.toString(),
                            de.getResultCode().toString(),
                            de.getMessageObject());
      DirectoryServer.sendAlertNotification(this,
                           ALERT_TYPE_UNIQUE_ATTR_SYNC_ERROR, message);
    }
  }



  /**
   * Retrieves the set of base DNs below which uniqueness checks should be
   * performed.  If no uniqueness checks should be performed for the specified
   * entry, then {@code null} will be returned.
   *
   * @param  config   The plugin configuration to use to make the determination.
   * @param  entryDN  The DN of the entry for which the checks will be
   *                  performed.
   */
  private Set<DN> getBaseDNs(UniqueAttributePluginCfg config, DN entryDN)
  {
    Set<DN> baseDNs = config.getBaseDN();
    if ((baseDNs == null) || baseDNs.isEmpty())
    {
      baseDNs = DirectoryServer.getPublicNamingContexts().keySet();
    }

    for (DN baseDN : baseDNs)
    {
      if (entryDN.isDescendantOf(baseDN))
      {
        return baseDNs;
      }
    }

    return null;
  }



  /**
   * Retrieves the DN of the first entry identified that conflicts with the
   * provided value.
   *
   * @param  baseDNs   The set of base DNs below which the search is to be
   *                   performed.
   * @param  targetDN  The DN of the entry at which the change is targeted.  If
   *                   a conflict is found in that entry, then it will be
   *                   ignored.
   * @param  config    The plugin configuration to use when making the
   *                   determination.
   * @param  value     The value for which to identify any conflicting entries.
   *
   * @return  The DN of the first entry identified that contains a conflicting
   *          value.
   *
   * @throws  DirectoryException  If a problem occurred while attempting to
   *                              make the determination.
   */
  private DN getConflictingEntryDN(Set<DN> baseDNs, DN targetDN,
                                   UniqueAttributePluginCfg config,
                                   AttributeValue value)
          throws DirectoryException
  {
    SearchFilter filter;
    Set<AttributeType> attrTypes = config.getType();
    if (attrTypes.size() == 1)
    {
      filter = SearchFilter.createEqualityFilter(attrTypes.iterator().next(),
                                                 value);
    }
    else
    {
      List<SearchFilter> equalityFilters =
           new ArrayList<SearchFilter>(attrTypes.size());
      for (AttributeType t : attrTypes)
      {
        equalityFilters.add(SearchFilter.createEqualityFilter(t, value));
      }
      filter = SearchFilter.createORFilter(equalityFilters);
    }

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    for (DN baseDN : baseDNs)
    {
      InternalSearchOperation searchOperation =
           conn.processSearch(baseDN, SearchScope.WHOLE_SUBTREE,
                              DereferencePolicy.NEVER_DEREF_ALIASES, 2, 0,
                              false, filter, SEARCH_ATTRS);
      for (SearchResultEntry e : searchOperation.getSearchEntries())
      {
        if (! e.getName().equals(targetDN))
        {
          return e.getName();
        }
      }

      switch (searchOperation.getResultCode())
      {
        case SUCCESS:
        case NO_SUCH_OBJECT:
          // These are fine.  Either the search was successful or the base DN
          // didn't exist.
          break;

        default:
          // An error occurred that prevented the search from completing
          // successfully.
          throw new DirectoryException(searchOperation.getResultCode(),
                         searchOperation.getErrorMessage().toMessage());
      }
    }

    // If we've gotten here, then no conflict was found.
    return null;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean isConfigurationAcceptable(PluginCfg configuration,
                                           List<LocalizableMessage> unacceptableReasons)
  {
    UniqueAttributePluginCfg cfg = (UniqueAttributePluginCfg) configuration;
    return isConfigurationChangeAcceptable(cfg, unacceptableReasons);
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isConfigurationChangeAcceptable(
                      UniqueAttributePluginCfg configuration,
                      List<LocalizableMessage> unacceptableReasons)
  {
    boolean configAcceptable = true;

    for (PluginCfgDefn.PluginType pluginType : configuration.getPluginType())
    {
      switch (pluginType)
      {
        case PREOPERATIONADD:
        case PREOPERATIONMODIFY:
        case PREOPERATIONMODIFYDN:
        case POSTOPERATIONADD:
        case POSTOPERATIONMODIFY:
        case POSTOPERATIONMODIFYDN:
        case POSTSYNCHRONIZATIONADD:
        case POSTSYNCHRONIZATIONMODIFY:
        case POSTSYNCHRONIZATIONMODIFYDN:
          // These are acceptable.
          break;

        default:
          LocalizableMessage message = ERR_PLUGIN_UNIQUEATTR_INVALID_PLUGIN_TYPE.get(
                                 pluginType.toString());
          unacceptableReasons.add(message);
          configAcceptable = false;
      }
    }

    Set<DN> cfgBaseDNs = configuration.getBaseDN();
    if ((cfgBaseDNs == null) || cfgBaseDNs.isEmpty())
    {
      cfgBaseDNs = DirectoryServer.getPublicNamingContexts().keySet();
    }

    for (AttributeType t : configuration.getType())
    {
      for (DN baseDN : cfgBaseDNs)
      {
        Backend b = DirectoryServer.getBackend(baseDN);
        if ((b != null) && (! b.isIndexed(t, IndexType.EQUALITY)))
        {
          unacceptableReasons.add(ERR_PLUGIN_UNIQUEATTR_ATTR_UNINDEXED.get(
                                       configuration.dn().toString(),
                                       t.getNameOrOID(), b.getBackendID()));
          configAcceptable = false;
        }
      }
    }

    return configAcceptable;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public ConfigChangeResult applyConfigurationChange(
                                 UniqueAttributePluginCfg newConfiguration)
  {
    currentConfiguration = newConfiguration;
    return new ConfigChangeResult(ResultCode.SUCCESS, false);
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public DN getComponentEntryDN()
  {
    return currentConfiguration.dn();
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public String getClassName()
  {
    return UniqueAttributePlugin.class.getName();
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public Map<String,String> getAlerts()
  {
    Map<String,String> alerts = new LinkedHashMap<String,String>(2);

    alerts.put(ALERT_TYPE_UNIQUE_ATTR_SYNC_CONFLICT,
               ALERT_DESCRIPTION_UNIQUE_ATTR_SYNC_CONFLICT);
    alerts.put(ALERT_TYPE_UNIQUE_ATTR_SYNC_ERROR,
               ALERT_DESCRIPTION_UNIQUE_ATTR_SYNC_ERROR);

    return alerts;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public final PluginResult.PostOperation
       doPostOperation(PostOperationAddOperation addOperation)
  {
    UniqueAttributePluginCfg config = currentConfiguration;
    Entry entry = addOperation.getEntryToAdd();

    Set<DN> baseDNs = getBaseDNs(config, entry.getName());
    if (baseDNs == null)
    {
      // The entry is outside the scope of this plugin.
      return PluginResult.PostOperation.continueOperationProcessing();
    }

    //Remove the attribute value from the map.
    for (AttributeType t : config.getType())
    {
      List<Attribute> attrList = entry.getAttribute(t);
      if (attrList != null)
      {
        for (Attribute a : attrList)
        {
          for (AttributeValue v : a)
          {
            uniqueAttrValue2Dn.remove(v);
          }
        }
      }
    }

    return PluginResult.PostOperation.continueOperationProcessing();
  }




  /**
   * {@inheritDoc}
   */
  @Override()
  public final PluginResult.PostOperation
       doPostOperation(PostOperationModifyOperation modifyOperation)
  {
    UniqueAttributePluginCfg config = currentConfiguration;
    DN entryDN = modifyOperation.getEntryDN();

    Set<DN> baseDNs = getBaseDNs(config, entryDN);
    if (baseDNs == null)
    {
      // The entry is outside the scope of this plugin.
      return PluginResult.PostOperation.continueOperationProcessing();
    }

    for (Modification m : modifyOperation.getModifications())
    {
      Attribute a = m.getAttribute();
      AttributeType t = a.getAttributeType();
      if (! config.getType().contains(t))
      {
        // This modification isn't for a unique attribute.
        continue;
      }

      switch (m.getModificationType())
      {
        case ADD:
        case REPLACE:
          for (AttributeValue v : a)
          {
            uniqueAttrValue2Dn.remove(v);
          }
          break;

        case INCREMENT:
          // We could calculate the new value, but we'll just take it from the
          // updated entry.
          List<Attribute> attrList =
               modifyOperation.getModifiedEntry().getAttribute(t,
                                                           a.getOptions());
          if (attrList != null)
          {
            for (Attribute updatedAttr : attrList)
            {
              if (! updatedAttr.optionsEqual(a.getOptions()))
              {
                continue;
              }

              for (AttributeValue v : updatedAttr)
              {
                uniqueAttrValue2Dn.remove(v);
              }
            }
          }
          break;

        default:
          // We don't need to look at this modification because it's not a
          // modification type of interest.
          continue;
      }
    }

    return PluginResult.PostOperation.continueOperationProcessing();
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public final PluginResult.PostOperation
       doPostOperation(PostOperationModifyDNOperation modifyDNOperation)
  {
    UniqueAttributePluginCfg config = currentConfiguration;
    Set<DN> baseDNs = getBaseDNs(config,
                                 modifyDNOperation.getUpdatedEntry().getName());
    if (baseDNs == null)
    {
      // The entry is outside the scope of this plugin.
      return PostOperation.continueOperationProcessing();
    }

    RDN newRDN = modifyDNOperation.getNewRDN();
    for (int i=0; i < newRDN.getNumValues(); i++)
    {
      AttributeType t = newRDN.getAttributeType(i);
      if (! config.getType().contains(t))
      {
        // We aren't interested in this attribute type.
        continue;
      }
      AttributeValue v = newRDN.getAttributeValue(i);
      uniqueAttrValue2Dn.remove(v);
    }
    return PostOperation.continueOperationProcessing();
  }
}


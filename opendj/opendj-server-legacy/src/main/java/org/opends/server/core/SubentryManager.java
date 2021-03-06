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
 *      Copyright 2009-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2015 ForgeRock AS
 */
package org.opends.server.core;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SearchScope;
import org.opends.server.api.Backend;
import org.opends.server.api.BackendInitializationListener;
import org.opends.server.api.ClientConnection;
import org.opends.server.api.DITCacheMap;
import org.opends.server.api.SubentryChangeListener;
import org.opends.server.api.plugin.InternalDirectoryServerPlugin;
import org.opends.server.api.plugin.PluginResult;
import org.opends.server.api.plugin.PluginResult.PostOperation;
import org.opends.server.api.plugin.PluginResult.PreOperation;
import org.opends.server.api.plugin.PluginType;
import org.opends.server.controls.SubentriesControl;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.protocols.internal.SearchRequest;
import org.opends.server.types.*;
import org.opends.server.types.operation.PostOperationAddOperation;
import org.opends.server.types.operation.PostOperationDeleteOperation;
import org.opends.server.types.operation.PostOperationModifyDNOperation;
import org.opends.server.types.operation.PostOperationModifyOperation;
import org.opends.server.types.operation.PostSynchronizationAddOperation;
import org.opends.server.types.operation.PostSynchronizationDeleteOperation;
import org.opends.server.types.operation.PostSynchronizationModifyDNOperation;
import org.opends.server.types.operation.PostSynchronizationModifyOperation;
import org.opends.server.types.operation.PreOperationAddOperation;
import org.opends.server.types.operation.PreOperationDeleteOperation;
import org.opends.server.types.operation.PreOperationModifyDNOperation;
import org.opends.server.types.operation.PreOperationModifyOperation;
import org.opends.server.workflowelement.localbackend.LocalBackendSearchOperation;

import static org.opends.messages.CoreMessages.*;
import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.protocols.internal.InternalClientConnection.*;
import static org.opends.server.protocols.internal.Requests.*;
import static org.opends.server.util.ServerConstants.*;

/**
 * This class provides a mechanism for interacting with subentries defined in
 * the Directory Server.  It will handle all necessary processing at server
 * startup to identify and load subentries within the server.
 * <BR><BR>
 * FIXME:  At the present time, it assumes that all of the necessary
 * information about subentries defined in the server can be held in
 * memory.  If it is determined that this approach is not workable
 * in all cases, then we will need an alternate strategy.
 */
public class SubentryManager extends InternalDirectoryServerPlugin
        implements BackendInitializationListener
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** A mapping between the DNs and applicable subentries. */
  private HashMap<DN,List<SubEntry>> dn2SubEntry;

  /** A mapping between the DNs and applicable collective subentries. */
  private HashMap<DN,List<SubEntry>> dn2CollectiveSubEntry;

  /** A mapping between subentry DNs and subentry objects. */
  private DITCacheMap<SubEntry> dit2SubEntry;

  /** Internal search all operational attributes. */
  private LinkedHashSet<String> requestAttrs;

  /** Lock to protect internal data structures. */
  private final ReentrantReadWriteLock lock;

  /** The set of change notification listeners. */
  private CopyOnWriteArrayList<SubentryChangeListener>
               changeListeners;

  /** Dummy configuration DN for Subentry Manager. */
  private static final String CONFIG_DN = "cn=Subentry Manager,cn=config";

  /**
   * Creates a new instance of this subentry manager.
   *
   * @throws DirectoryException If a problem occurs while
   *                            creating an instance of
   *                            the subentry manager.
   */
  public SubentryManager() throws DirectoryException
  {
    super(DN.valueOf(CONFIG_DN), EnumSet.of(
          PluginType.PRE_OPERATION_ADD,
          PluginType.PRE_OPERATION_DELETE,
          PluginType.PRE_OPERATION_MODIFY,
          PluginType.PRE_OPERATION_MODIFY_DN,
          PluginType.POST_OPERATION_ADD,
          PluginType.POST_OPERATION_DELETE,
          PluginType.POST_OPERATION_MODIFY,
          PluginType.POST_OPERATION_MODIFY_DN,
          PluginType.POST_SYNCHRONIZATION_ADD,
          PluginType.POST_SYNCHRONIZATION_DELETE,
          PluginType.POST_SYNCHRONIZATION_MODIFY,
          PluginType.POST_SYNCHRONIZATION_MODIFY_DN),
          true);

    lock = new ReentrantReadWriteLock();

    dn2SubEntry = new HashMap<DN,List<SubEntry>>();
    dn2CollectiveSubEntry = new HashMap<DN,List<SubEntry>>();
    dit2SubEntry = new DITCacheMap<SubEntry>();

    changeListeners =
            new CopyOnWriteArrayList<SubentryChangeListener>();

    requestAttrs = new LinkedHashSet<String>();
    requestAttrs.add("*");
    requestAttrs.add("+");

    DirectoryServer.registerInternalPlugin(this);
    DirectoryServer.registerBackendInitializationListener(this);
  }

  /**
   * Perform any required finalization tasks for Subentry Manager.
   * This should only be called at Directory Server shutdown.
   */
  public void finalizeSubentryManager()
  {
    // Deregister as internal plugin and
    // backend initialization listener.
    DirectoryServer.deregisterInternalPlugin(this);
    DirectoryServer.deregisterBackendInitializationListener(this);
  }

  /**
   * Registers the provided change notification listener with this manager
   * so that it will be notified of any add, delete, modify, or modify DN
   * operations that are performed.
   *
   * @param  changeListener  The change notification listener to register
   *                         with this manager.
   */
  public void registerChangeListener(
                          SubentryChangeListener changeListener)
  {
    changeListeners.add(changeListener);
  }

  /**
   * Deregisters the provided change notification listener with this manager
   * so that it will no longer be notified of any add, delete, modify, or
   * modify DN operations that are performed.
   *
   * @param  changeListener  The change notification listener to deregister
   *                         with this manager.
   */
  public void deregisterChangeListener(
                          SubentryChangeListener changeListener)
  {
    changeListeners.remove(changeListener);
  }

  /**
   * Add a given entry to this subentry manager.
   * @param entry to add.
   */
  private void addSubentry(Entry entry) throws DirectoryException
  {
    SubEntry subEntry = new SubEntry(entry);
    SubtreeSpecification subSpec =
            subEntry.getSubTreeSpecification();
    DN subDN = subSpec.getBaseDN();
    List<SubEntry> subList = null;
    lock.writeLock().lock();
    try
    {
      if (subEntry.isCollective() || subEntry.isInheritedCollective())
      {
        subList = dn2CollectiveSubEntry.get(subDN);
      }
      else
      {
        subList = dn2SubEntry.get(subDN);
      }
      if (subList == null)
      {
        subList = new ArrayList<SubEntry>();
        if (subEntry.isCollective() || subEntry.isInheritedCollective())
        {
          dn2CollectiveSubEntry.put(subDN, subList);
        }
        else
        {
          dn2SubEntry.put(subDN, subList);
        }
      }
      dit2SubEntry.put(entry.getName(), subEntry);
      subList.add(subEntry);
    }
    finally
    {
      lock.writeLock().unlock();
    }
  }

  /**
   * Remove a given entry from this subentry manager.
   * @param entry to remove.
   */
  private void removeSubentry(Entry entry)
  {
    lock.writeLock().lock();
    try
    {
      boolean removed = false;
      Iterator<Map.Entry<DN, List<SubEntry>>> setIterator =
              dn2SubEntry.entrySet().iterator();
      while (setIterator.hasNext())
      {
        Map.Entry<DN, List<SubEntry>> mapEntry = setIterator.next();
        List<SubEntry> subList = mapEntry.getValue();
        Iterator<SubEntry> listIterator = subList.iterator();
        while (listIterator.hasNext())
        {
          SubEntry subEntry = listIterator.next();
          if (subEntry.getDN().equals(entry.getName()))
          {
            dit2SubEntry.remove(entry.getName());
            listIterator.remove();
            removed = true;
            break;
          }
        }
        if (subList.isEmpty())
        {
          setIterator.remove();
        }
        if (removed)
        {
          return;
        }
      }
      setIterator = dn2CollectiveSubEntry.entrySet().iterator();
      while (setIterator.hasNext())
      {
        Map.Entry<DN, List<SubEntry>> mapEntry = setIterator.next();
        List<SubEntry> subList = mapEntry.getValue();
        Iterator<SubEntry> listIterator = subList.iterator();
        while (listIterator.hasNext())
        {
          SubEntry subEntry = listIterator.next();
          if (subEntry.getDN().equals(entry.getName()))
          {
            dit2SubEntry.remove(entry.getName());
            listIterator.remove();
            removed = true;
            break;
          }
        }
        if (subList.isEmpty())
        {
          setIterator.remove();
        }
        if (removed)
        {
          return;
        }
      }
    }
    finally
    {
      lock.writeLock().unlock();
    }
  }

  /**
   * {@inheritDoc}  In this case, the server will search the backend to find
   * all subentries that it may contain and register them with this manager.
   */
  @Override
  public void performBackendInitializationProcessing(Backend backend)
  {
    InternalClientConnection conn = getRootConnection();
    SubentriesControl control = new SubentriesControl(true, true);

    SearchFilter filter = null;
    try
    {
      filter = SearchFilter.createFilterFromString("(|" +
            "(" + ATTR_OBJECTCLASS + "=" + OC_SUBENTRY + ")" +
            "(" + ATTR_OBJECTCLASS + "=" + OC_LDAP_SUBENTRY + ")" +
            ")");
      if (backend.getEntryCount() > 0 && ! backend.isIndexed(filter))
      {
        logger.warn(WARN_SUBENTRY_FILTER_NOT_INDEXED, filter, backend.getBackendID());
      }
    }
    catch (Exception e)
    {
      logger.traceException(e);
    }

    for (DN baseDN : backend.getBaseDNs())
    {
      try
      {
        if (! backend.entryExists(baseDN))
        {
          continue;
        }
      }
      catch (Exception e)
      {
        logger.traceException(e);

        // FIXME -- Is there anything that we need to do here?
        continue;
      }

      SearchRequest request = newSearchRequest(baseDN, SearchScope.WHOLE_SUBTREE, filter)
          .addAttribute(requestAttrs)
          .addControl(control);
      InternalSearchOperation internalSearch =
          new InternalSearchOperation(conn, nextOperationID(), nextMessageID(), request);
      LocalBackendSearchOperation localSearch = new LocalBackendSearchOperation(internalSearch);

      try
      {
        backend.search(localSearch);
      }
      catch (Exception e)
      {
        logger.traceException(e);

        // FIXME -- Is there anything that we need to do here?
        continue;
      }

      for (SearchResultEntry entry : internalSearch.getSearchEntries())
      {
        if (entry.isSubentry() || entry.isLDAPSubentry())
        {
          try
          {
            addSubentry(entry);

            // Notify change listeners.
            for (SubentryChangeListener changeListener :
              changeListeners)
            {
              try
              {
                changeListener.handleSubentryAdd(entry);
              }
              catch (Exception e)
              {
                logger.traceException(e);
              }
            }
          }
          catch (Exception e)
          {
            logger.traceException(e);

            // FIXME -- Handle this.
            continue;
          }
        }
      }
    }
  }

  /**
   * Return all subentries for this manager.
   * Note that this getter will skip any collective subentries,
   * returning only applicable regular subentries.
   * @return all subentries for this manager.
   */
  public List<SubEntry> getSubentries()
  {
    if (dn2SubEntry.isEmpty())
    {
      return Collections.emptyList();
    }

    List<SubEntry> subentries = new ArrayList<SubEntry>();

    lock.readLock().lock();
    try
    {
      for (List<SubEntry> subList : dn2SubEntry.values())
      {
        subentries.addAll(subList);
      }
    }
    finally
    {
      lock.readLock().unlock();
    }

    return subentries;
  }

  /**
   * Return subentries applicable to specific DN.
   * Note that this getter will skip any collective subentries,
   * returning only applicable regular subentries.
   * @param  dn for which to retrieve applicable
   *         subentries.
   * @return applicable subentries.
   */
  public List<SubEntry> getSubentries(DN dn)
  {
    if (dn2SubEntry.isEmpty())
    {
      return Collections.emptyList();
    }

    List<SubEntry> subentries = new ArrayList<SubEntry>();

    lock.readLock().lock();
    try
    {
      for (DN subDN = dn; subDN != null;
           subDN = subDN.parent())
      {
        List<SubEntry> subList = dn2SubEntry.get(subDN);
        if (subList != null)
        {
          for (SubEntry subEntry : subList)
          {
            SubtreeSpecification subSpec =
                    subEntry.getSubTreeSpecification();
            if (subSpec.isDNWithinScope(dn))
            {
              subentries.add(subEntry);
            }
          }
        }
      }
    }
    finally
    {
      lock.readLock().unlock();
    }

    return subentries;
  }

  /**
   * Return subentries applicable to specific entry.
   * Note that this getter will skip any collective subentries,
   * returning only applicable regular subentries.
   * @param  entry for which to retrieve applicable
   *         subentries.
   * @return applicable subentries.
   */
  public List<SubEntry> getSubentries(Entry entry)
  {
    if (dn2SubEntry.isEmpty())
    {
      return Collections.emptyList();
    }

    List<SubEntry> subentries = new ArrayList<SubEntry>();

    lock.readLock().lock();
    try
    {
      for (DN subDN = entry.getName(); subDN != null;
           subDN = subDN.parent())
      {
        List<SubEntry> subList = dn2SubEntry.get(subDN);
        if (subList != null)
        {
          for (SubEntry subEntry : subList)
          {
            SubtreeSpecification subSpec =
                    subEntry.getSubTreeSpecification();
            if (subSpec.isWithinScope(entry))
            {
              subentries.add(subEntry);
            }
          }
        }
      }
    }
    finally
    {
      lock.readLock().unlock();
    }

    return subentries;
  }

  /**
   * Return collective subentries applicable to specific DN.
   * Note that this getter will skip any regular subentries,
   * returning only applicable collective subentries.
   * @param  dn for which to retrieve applicable
   *         subentries.
   * @return applicable subentries.
   */
  public List<SubEntry> getCollectiveSubentries(DN dn)
  {
    if (dn2CollectiveSubEntry.isEmpty())
    {
      return Collections.emptyList();
    }

    List<SubEntry> subentries = new ArrayList<SubEntry>();

    lock.readLock().lock();
    try
    {
      for (DN subDN = dn; subDN != null;
           subDN = subDN.parent())
      {
        List<SubEntry> subList = dn2CollectiveSubEntry.get(subDN);
        if (subList != null)
        {
          for (SubEntry subEntry : subList)
          {
            SubtreeSpecification subSpec =
                    subEntry.getSubTreeSpecification();
            if (subSpec.isDNWithinScope(dn))
            {
              subentries.add(subEntry);
            }
          }
        }
      }
    }
    finally
    {
      lock.readLock().unlock();
    }

    return subentries;
  }

  /**
   * Return collective subentries applicable to specific entry.
   * Note that this getter will skip any regular subentries,
   * returning only applicable collective subentries.
   * @param  entry for which to retrieve applicable
   *         subentries.
   * @return applicable subentries.
   */
  public List<SubEntry> getCollectiveSubentries(Entry entry)
  {
    if (dn2CollectiveSubEntry.isEmpty())
    {
      return Collections.emptyList();
    }

    List<SubEntry> subentries = new ArrayList<SubEntry>();

    lock.readLock().lock();
    try
    {
      for (DN subDN = entry.getName(); subDN != null;
           subDN = subDN.parent())
      {
        List<SubEntry> subList = dn2CollectiveSubEntry.get(subDN);
        if (subList != null)
        {
          for (SubEntry subEntry : subList)
          {
            SubtreeSpecification subSpec =
                    subEntry.getSubTreeSpecification();
            if (subSpec.isWithinScope(entry))
            {
              subentries.add(subEntry);
            }
          }
        }
      }
    }
    finally
    {
      lock.readLock().unlock();
    }

    return subentries;
  }

  /**
   * {@inheritDoc}  In this case, the server will de-register
   * all subentries associated with the provided backend.
   */
  @Override
  public void performBackendFinalizationProcessing(Backend backend)
  {
    lock.writeLock().lock();
    try
    {
      Iterator<Map.Entry<DN, List<SubEntry>>> setIterator =
              dn2SubEntry.entrySet().iterator();
      while (setIterator.hasNext())
      {
        Map.Entry<DN, List<SubEntry>> mapEntry = setIterator.next();
        List<SubEntry> subList = mapEntry.getValue();
        Iterator<SubEntry> listIterator = subList.iterator();
        while (listIterator.hasNext())
        {
          SubEntry subEntry = listIterator.next();
          if (backend.handlesEntry(subEntry.getDN()))
          {
            dit2SubEntry.remove(subEntry.getDN());
            listIterator.remove();

            // Notify change listeners.
            for (SubentryChangeListener changeListener :
              changeListeners)
            {
              try
              {
                changeListener.handleSubentryDelete(
                        subEntry.getEntry());
              }
              catch (Exception e)
              {
                logger.traceException(e);
              }
            }
          }
        }
        if (subList.isEmpty())
        {
          setIterator.remove();
        }
      }
      setIterator = dn2CollectiveSubEntry.entrySet().iterator();
      while (setIterator.hasNext())
      {
        Map.Entry<DN, List<SubEntry>> mapEntry = setIterator.next();
        List<SubEntry> subList = mapEntry.getValue();
        Iterator<SubEntry> listIterator = subList.iterator();
        while (listIterator.hasNext())
        {
          SubEntry subEntry = listIterator.next();
          if (backend.handlesEntry(subEntry.getDN()))
          {
            dit2SubEntry.remove(subEntry.getDN());
            listIterator.remove();

            // Notify change listeners.
            for (SubentryChangeListener changeListener :
              changeListeners)
            {
              try
              {
                changeListener.handleSubentryDelete(
                        subEntry.getEntry());
              }
              catch (Exception e)
              {
                logger.traceException(e);
              }
            }
          }
        }
        if (subList.isEmpty())
        {
          setIterator.remove();
        }
      }
    }
    finally
    {
      lock.writeLock().unlock();
    }
  }

  private void doPostAdd(Entry entry)
  {
    if (entry.isSubentry() || entry.isLDAPSubentry())
    {
      lock.writeLock().lock();
      try
      {
        try
        {
          addSubentry(entry);

          // Notify change listeners.
          for (SubentryChangeListener changeListener :
            changeListeners)
          {
            try
            {
              changeListener.handleSubentryAdd(entry);
            }
            catch (Exception e)
            {
              logger.traceException(e);
            }
          }
        }
        catch (Exception e)
        {
          logger.traceException(e);

          // FIXME -- Handle this.
        }
      }
      finally
      {
        lock.writeLock().unlock();
      }
    }
  }

  private void doPostDelete(Entry entry)
  {
    lock.writeLock().lock();
    try
    {
      for (SubEntry subEntry : dit2SubEntry.getSubtree(entry.getName()))
      {
        removeSubentry(subEntry.getEntry());

        // Notify change listeners.
        for (SubentryChangeListener changeListener :
                changeListeners)
        {
          try
          {
            changeListener.handleSubentryDelete(subEntry.getEntry());
          }
          catch (Exception e)
          {
            logger.traceException(e);
          }
        }
      }
    }
    finally
    {
      lock.writeLock().unlock();
    }
  }

  private void doPostModify(Entry oldEntry, Entry newEntry)
  {
    boolean notify = false;

    lock.writeLock().lock();
    try
    {
      if (oldEntry.isSubentry() || oldEntry.isLDAPSubentry())
      {
        removeSubentry(oldEntry);
        notify = true;
      }
      if (newEntry.isSubentry() || newEntry.isLDAPSubentry())
      {
        try
        {
          addSubentry(newEntry);
          notify = true;
        }
        catch (Exception e)
        {
          logger.traceException(e);

          // FIXME -- Handle this.
        }
      }

      if (notify)
      {
        // Notify change listeners.
        for (SubentryChangeListener changeListener :
          changeListeners)
        {
          try
          {
            changeListener.handleSubentryModify(
                    oldEntry, newEntry);
          }
          catch (Exception e)
          {
            logger.traceException(e);
          }
        }
      }
    }
    finally
    {
      lock.writeLock().unlock();
    }
  }

  private void doPostModifyDN(final Entry oldEntry, final Entry newEntry)
  {
    lock.writeLock().lock();
    try
    {
      Collection<SubEntry> setToDelete = dit2SubEntry.getSubtree(oldEntry.getName());
      for (SubEntry subentry : setToDelete)
      {
        final Entry currentSubentry = subentry.getEntry();
        removeSubentry(currentSubentry);

        Entry renamedSubentry = null;
        try
        {
          renamedSubentry = currentSubentry.duplicate(false);
          final DN renamedDN = currentSubentry.getName().rename(oldEntry.getName(), newEntry.getName());
          renamedSubentry.setDN(renamedDN);
          addSubentry(renamedSubentry);
        }
        catch (Exception e)
        {
          // Shouldnt happen.
          logger.traceException(e);
        }

        // Notify change listeners.
        for (SubentryChangeListener changeListener :
          changeListeners)
        {
          try
          {
            changeListener.handleSubentryModify(currentSubentry, renamedSubentry);
          }
          catch (Exception e)
          {
            logger.traceException(e);
          }
        }
      }
    }
    finally
    {
      lock.writeLock().unlock();
    }
  }

  /** {@inheritDoc} */
  @Override
  public PreOperation doPreOperation(
          PreOperationAddOperation addOperation)
  {
    Entry entry = addOperation.getEntryToAdd();

    if (entry.isSubentry() || entry.isLDAPSubentry())
    {
      ClientConnection conn = addOperation.getClientConnection();
      if (!conn.hasPrivilege(Privilege.SUBENTRY_WRITE,
           conn.getOperationInProgress(
             addOperation.getMessageID())))
      {
        return PluginResult.PreOperation.stopProcessing(
                ResultCode.INSUFFICIENT_ACCESS_RIGHTS,
                ERR_SUBENTRY_WRITE_INSUFFICIENT_PRIVILEGES.get());
      }
      for (SubentryChangeListener changeListener :
              changeListeners)
      {
        try
        {
          changeListener.checkSubentryAddAcceptable(entry);
        }
        catch (DirectoryException de)
        {
          logger.traceException(de);

          return PluginResult.PreOperation.stopProcessing(
                  de.getResultCode(), de.getMessageObject());
        }
      }
    }

    return PluginResult.PreOperation.continueOperationProcessing();
  }

  /** {@inheritDoc} */
  @Override
  public PreOperation doPreOperation(
          PreOperationDeleteOperation deleteOperation)
  {
    Entry entry = deleteOperation.getEntryToDelete();
    boolean hasSubentryWritePrivilege = false;

    lock.readLock().lock();
    try
    {
      for (SubEntry subEntry : dit2SubEntry.getSubtree(entry.getName()))
      {
        if (!hasSubentryWritePrivilege)
        {
          ClientConnection conn = deleteOperation.getClientConnection();
          if (!conn.hasPrivilege(Privilege.SUBENTRY_WRITE,
               conn.getOperationInProgress(
                 deleteOperation.getMessageID())))
          {
            return PluginResult.PreOperation.stopProcessing(
                    ResultCode.INSUFFICIENT_ACCESS_RIGHTS,
                    ERR_SUBENTRY_WRITE_INSUFFICIENT_PRIVILEGES.get());
          }
          else
          {
            hasSubentryWritePrivilege = true;
          }
        }
        for (SubentryChangeListener changeListener :
                changeListeners)
        {
          try
          {
            changeListener.checkSubentryDeleteAcceptable(
                    subEntry.getEntry());
          }
          catch (DirectoryException de)
          {
            logger.traceException(de);

            return PluginResult.PreOperation.stopProcessing(
                    de.getResultCode(), de.getMessageObject());
          }
        }
      }
    }
    finally
    {
      lock.readLock().unlock();
    }

    return PluginResult.PreOperation.continueOperationProcessing();
  }

  /** {@inheritDoc} */
  @Override
  public PreOperation doPreOperation(
          PreOperationModifyOperation modifyOperation)
  {
    Entry oldEntry = modifyOperation.getCurrentEntry();
    Entry newEntry = modifyOperation.getModifiedEntry();

    if ((newEntry.isSubentry() || newEntry.isLDAPSubentry()) ||
        (oldEntry.isSubentry() || oldEntry.isLDAPSubentry()))
    {
      ClientConnection conn = modifyOperation.getClientConnection();
      if (!conn.hasPrivilege(Privilege.SUBENTRY_WRITE,
           conn.getOperationInProgress(
             modifyOperation.getMessageID())))
      {
        return PluginResult.PreOperation.stopProcessing(
                ResultCode.INSUFFICIENT_ACCESS_RIGHTS,
                ERR_SUBENTRY_WRITE_INSUFFICIENT_PRIVILEGES.get());
      }
      for (SubentryChangeListener changeListener :
              changeListeners)
      {
        try
        {
          changeListener.checkSubentryModifyAcceptable(
                  oldEntry, newEntry);
        }
        catch (DirectoryException de)
        {
          logger.traceException(de);

          return PluginResult.PreOperation.stopProcessing(
                  de.getResultCode(), de.getMessageObject());
        }
      }
    }

    return PluginResult.PreOperation.continueOperationProcessing();
  }

  /** {@inheritDoc} */
  @Override
  public PreOperation doPreOperation(PreOperationModifyDNOperation modifyDNOperation)
  {
    boolean hasSubentryWritePrivilege = false;

    lock.readLock().lock();
    try
    {
      final Entry oldEntry = modifyDNOperation.getOriginalEntry();
      Collection<SubEntry> setToDelete =
              dit2SubEntry.getSubtree(oldEntry.getName());
      for (SubEntry subentry : setToDelete)
      {
        if (!hasSubentryWritePrivilege)
        {
          ClientConnection conn = modifyDNOperation.getClientConnection();
          if (!conn.hasPrivilege(Privilege.SUBENTRY_WRITE,
               conn.getOperationInProgress(
                 modifyDNOperation.getMessageID())))
          {
            return PluginResult.PreOperation.stopProcessing(
                    ResultCode.INSUFFICIENT_ACCESS_RIGHTS,
                    ERR_SUBENTRY_WRITE_INSUFFICIENT_PRIVILEGES.get());
          }
          else
          {
            hasSubentryWritePrivilege = true;
          }
        }

        final Entry newEntry = modifyDNOperation.getUpdatedEntry();
        final Entry currentSubentry = subentry.getEntry();
        final Entry renamedSubentry = currentSubentry.duplicate(false);
        final DN renamedDN = currentSubentry.getName().rename(oldEntry.getName(), newEntry.getName());
        renamedSubentry.setDN(renamedDN);

        for (SubentryChangeListener changeListener : changeListeners)
        {
          try
          {
            changeListener.checkSubentryModifyAcceptable(currentSubentry, renamedSubentry);
          }
          catch (DirectoryException de)
          {
            logger.traceException(de);

            return PluginResult.PreOperation.stopProcessing(
                    de.getResultCode(), de.getMessageObject());
          }
        }
      }
    }
    finally
    {
      lock.readLock().unlock();
    }

    return PluginResult.PreOperation.continueOperationProcessing();
  }

  /** {@inheritDoc} */
  @Override
  public PostOperation doPostOperation(
          PostOperationAddOperation addOperation)
  {
    // Only do something if the operation is successful, meaning there
    // has been a change.
    if (addOperation.getResultCode() == ResultCode.SUCCESS)
    {
      doPostAdd(addOperation.getEntryToAdd());
    }

    // If we've gotten here, then everything is acceptable.
    return PluginResult.PostOperation.continueOperationProcessing();
  }

  /** {@inheritDoc} */
  @Override
  public PostOperation doPostOperation(
          PostOperationDeleteOperation deleteOperation)
  {
    // Only do something if the operation is successful, meaning there
    // has been a change.
    if (deleteOperation.getResultCode() == ResultCode.SUCCESS)
    {
      doPostDelete(deleteOperation.getEntryToDelete());
    }

    // If we've gotten here, then everything is acceptable.
    return PluginResult.PostOperation.continueOperationProcessing();
  }

  /** {@inheritDoc} */
  @Override
  public PostOperation doPostOperation(
          PostOperationModifyOperation modifyOperation)
  {
    // Only do something if the operation is successful, meaning there
    // has been a change.
    if (modifyOperation.getResultCode() == ResultCode.SUCCESS)
    {
      doPostModify(modifyOperation.getCurrentEntry(),
            modifyOperation.getModifiedEntry());
    }

    // If we've gotten here, then everything is acceptable.
    return PluginResult.PostOperation.continueOperationProcessing();
  }

  /** {@inheritDoc} */
  @Override
  public PostOperation doPostOperation(
          PostOperationModifyDNOperation modifyDNOperation)
  {
    // Only do something if the operation is successful, meaning there
    // has been a change.
    if (modifyDNOperation.getResultCode() == ResultCode.SUCCESS)
    {
      doPostModifyDN(modifyDNOperation.getOriginalEntry(),
            modifyDNOperation.getUpdatedEntry());
    }

    // If we've gotten here, then everything is acceptable.
    return PluginResult.PostOperation.continueOperationProcessing();
  }

  /** {@inheritDoc} */
  @Override
  public void doPostSynchronization(
      PostSynchronizationAddOperation addOperation)
  {
    Entry entry = addOperation.getEntryToAdd();
    if (entry != null)
    {
      doPostAdd(entry);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void doPostSynchronization(
      PostSynchronizationDeleteOperation deleteOperation)
  {
    Entry entry = deleteOperation.getEntryToDelete();
    if (entry != null)
    {
      doPostDelete(entry);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void doPostSynchronization(
      PostSynchronizationModifyOperation modifyOperation)
  {
    Entry entry = modifyOperation.getCurrentEntry();
    Entry modEntry = modifyOperation.getModifiedEntry();
    if ((entry != null) && (modEntry != null))
    {
      doPostModify(entry, modEntry);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void doPostSynchronization(
      PostSynchronizationModifyDNOperation modifyDNOperation)
  {
    Entry oldEntry = modifyDNOperation.getOriginalEntry();
    Entry newEntry = modifyDNOperation.getUpdatedEntry();
    if ((oldEntry != null) && (newEntry != null))
    {
      doPostModifyDN(oldEntry, newEntry);
    }
  }
}

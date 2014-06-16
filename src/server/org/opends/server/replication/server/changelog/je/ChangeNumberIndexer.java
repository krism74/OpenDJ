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
 *      Portions Copyright 2013-2014 ForgeRock AS
 */
package org.opends.server.replication.server.changelog.je;

import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ConcurrentSkipListSet;

import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.opends.server.api.DirectoryThread;
import org.opends.server.replication.common.CSN;
import org.opends.server.replication.common.MultiDomainServerState;
import org.opends.server.replication.common.ServerState;
import org.opends.server.replication.plugin.MultimasterReplication;
import org.opends.server.replication.protocol.UpdateMsg;
import org.opends.server.replication.server.ChangelogState;
import org.opends.server.replication.server.changelog.api.*;
import org.opends.server.types.DN;
import org.opends.server.types.DirectoryException;
import org.opends.server.util.StaticUtils;

import com.forgerock.opendj.util.Pair;

import static org.opends.messages.ReplicationMessages.*;
import static org.opends.server.util.StaticUtils.*;

/**
 * Thread responsible for inserting replicated changes into the ChangeNumber
 * Index DB (CNIndexDB for short). Only changes older than the medium
 * consistency point are inserted in the CNIndexDB. As a consequence this class
 * is also responsible for maintaining the medium consistency point.
 */
public class ChangeNumberIndexer extends DirectoryThread
{
  /** The tracer object for the debug logger. */
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /**
   * If it contains nothing, then the run method executes normally.
   * Otherwise, the {@link #run()} method must clear its state
   * for the supplied domain baseDNs. If a supplied domain is
   * {@link DN#NULL_DN}, then all domains will be cleared.
   */
  private final ConcurrentSkipListSet<DN> domainsToClear =
      new ConcurrentSkipListSet<DN>();
  private final ChangelogDB changelogDB;
  /** Only used for initialization, and then discarded. */
  private ChangelogState changelogState;

  /*
   * mediumConsistencyRUV and lastSeenUpdates must be thread safe, because
   * 1) initialization can happen while the replication server starts receiving
   * updates 2) many updates can happen concurrently.
   */
  /**
   * Holds the cross domain medium consistency Replication Update Vector for the
   * current replication server, also known as the previous cookie.
   * <p>
   * Stores the value of the cookie before the change currently processed is
   * inserted in the DB. After insert, it is updated with the CSN of the change
   * currently processed (thus becoming the "current" cookie just before the
   * change is returned.
   * <p>
   * Note: This object is only updated by changes/updates.
   *
   * @see <a href=
   * "https://wikis.forgerock.org/confluence/display/OPENDJ/OpenDJ+Domain+Names"
   * >OpenDJ Domain Names - medium consistency RUV</a>
   */
  private final MultiDomainServerState mediumConsistencyRUV =
      new MultiDomainServerState();

  /**
   * Holds the last time each replica was seen alive, whether via updates or
   * heartbeat notifications, or offline notifications. Data is held for each
   * serverId cross domain.
   * <p>
   * Updates are persistent and stored in the replicaDBs, heartbeats are
   * transient and are easily constructed on normal operations.
   * <p>
   * Note: This object is updated by both heartbeats and changes/updates.
   */
  private final MultiDomainServerState lastAliveCSNs =
      new MultiDomainServerState();
  /** Note: This object is updated by replica offline messages. */
  private final MultiDomainServerState replicasOffline =
      new MultiDomainServerState();

  /**
   * Cursor across all the replicaDBs for all the replication domains. It is
   * positioned on the next change that needs to be inserted in the CNIndexDB.
   * <p>
   * Note: it is only accessed from the {@link #run()} method.
   *
   * @NonNull
   */
  @SuppressWarnings("unchecked")
  private CompositeDBCursor<DN> nextChangeForInsertDBCursor =
      new CompositeDBCursor<DN>(Collections.EMPTY_MAP, false);

  /**
   * New cursors for this Map must be created from the {@link #run()} method,
   * i.e. from the same thread that will make use of them. If this rule is not
   * obeyed, then a JE exception will be thrown about
   * "Non-transactional Cursors may not be used in multiple threads;".
   */
  private Map<DN, Map<Integer, DBCursor<UpdateMsg>>> allCursors =
      new HashMap<DN, Map<Integer, DBCursor<UpdateMsg>>>();
  /**
   * Holds the newCursors that will have to be created in the next iteration
   * inside the {@link #run()} method.
   * <p>
   * This map can be updated by multiple threads.
   */
  private ConcurrentMap<Pair<DN, Integer>, CSN> newCursors =
      new ConcurrentSkipListMap<Pair<DN, Integer>, CSN>(
          new Comparator<Pair<DN, Integer>>()
          {
            @Override
            public int compare(Pair<DN, Integer> o1, Pair<DN, Integer> o2)
            {
              final int compareBaseDN = o1.getFirst().compareTo(o2.getFirst());
              if (compareBaseDN == 0)
              {
                return o1.getSecond().compareTo(o2.getSecond());
              }
              return compareBaseDN;
            }
          });

  /**
   * Builds a ChangeNumberIndexer object.
   *
   * @param changelogDB
   *          the changelogDB
   * @param changelogState
   *          the changelog state used for initialization
   */
  ChangeNumberIndexer(ChangelogDB changelogDB, ChangelogState changelogState)
  {
    super("Change number indexer");
    this.changelogDB = changelogDB;
    this.changelogState = changelogState;
  }

  /**
   * Ensures the medium consistency point is updated by heartbeats.
   *
   * @param baseDN
   *          the baseDN of the domain for which the heartbeat is published
   * @param heartbeatCSN
   *          the CSN coming from the heartbeat
   */
  public void publishHeartbeat(DN baseDN, CSN heartbeatCSN)
  {
    if (!isECLEnabledDomain(baseDN))
    {
      return;
    }

    final CSN oldestCSNBefore = getOldestLastAliveCSN();
    lastAliveCSNs.update(baseDN, heartbeatCSN);
    tryNotify(oldestCSNBefore);
  }

  /**
   * Ensures the medium consistency point is updated by UpdateMsg.
   *
   * @param baseDN
   *          the baseDN of the domain for which the heartbeat is published
   * @param updateMsg
   *          the updateMsg that will update the medium consistency point
   * @throws ChangelogException
   *           If a database problem happened
   */
  public void publishUpdateMsg(DN baseDN, UpdateMsg updateMsg)
      throws ChangelogException
  {
    if (!isECLEnabledDomain(baseDN))
    {
      return;
    }

    final CSN csn = updateMsg.getCSN();
    // only keep the oldest CSN that will be the new cursor's starting point
    newCursors.putIfAbsent(Pair.of(baseDN, csn.getServerId()), csn);
    final CSN oldestCSNBefore = getOldestLastAliveCSN();
    lastAliveCSNs.update(baseDN, csn);
    tryNotify(oldestCSNBefore);
  }

  /**
   * Returns whether the provided baseDN represents a replication domain enabled
   * for the external changelog.
   * <p>
   * This method is a test seam that break the dependency on a static method.
   *
   * @param baseDN
   *          the replication domain to check
   * @return true if the provided baseDN is enabled for the external changelog,
   *         false if the provided baseDN is disabled for the external changelog
   *         or unknown to multimaster replication.
   * @see MultimasterReplication#isECLEnabledDomain(DN)
   */
  protected boolean isECLEnabledDomain(DN baseDN)
  {
    return MultimasterReplication.isECLEnabledDomain(baseDN);
  }

  /**
   * Signals a replica went offline.
   *
   * @param baseDN
   *          the replica's replication domain
   * @param offlineCSN
   *          the serverId and time of the replica that went offline
   */
  public void replicaOffline(DN baseDN, CSN offlineCSN)
  {
    if (!isECLEnabledDomain(baseDN))
    {
      return;
    }

    replicasOffline.update(baseDN, offlineCSN);
    final CSN oldestCSNBefore = getOldestLastAliveCSN();
    lastAliveCSNs.update(baseDN, offlineCSN);
    tryNotify(oldestCSNBefore);
  }

  private CSN getOldestLastAliveCSN()
  {
    return lastAliveCSNs.getOldestCSNExcluding(replicasOffline).getSecond();
  }

  /**
   * Notifies the Change number indexer thread if it will be able to do some
   * work.
   */
  private void tryNotify(final CSN oldestCSNBefore)
  {
    if (mightMoveForwardMediumConsistencyPoint(oldestCSNBefore))
    {
      synchronized (this)
      {
        notify();
      }
    }
  }

  /**
   * Used for waking up the {@link ChangeNumberIndexer} thread because it might
   * have some work to do.
   */
  private boolean mightMoveForwardMediumConsistencyPoint(CSN oldestCSNBefore)
  {
    final CSN oldestCSNAfter = getOldestLastAliveCSN();
    // ensure that all initial replicas alive information have been updated
    // with CSNs that are acceptable for moving the medium consistency forward
    return allInitialReplicasAreOfflineOrAlive()
        && oldestCSNBefore != null // then oldestCSNAfter cannot be null
        // has the oldest CSN changed?
        && oldestCSNBefore.isOlderThan(oldestCSNAfter);
  }

  /**
   * Used by the {@link ChangeNumberIndexer} thread to determine whether the CSN
   * must be persisted to the change number index DB.
   */
  private boolean canMoveForwardMediumConsistencyPoint(CSN nextCSNToPersist)
  {
    // ensure that all initial replicas alive information have been updated
    // with CSNs that are acceptable for moving the medium consistency forward
    return allInitialReplicasAreOfflineOrAlive()
        // can we persist the next CSN?
        && nextCSNToPersist.isOlderThanOrEqualTo(getOldestLastAliveCSN());
  }

  /**
   * Returns true only if the initial replicas known from the changelog state DB
   * are either:
   * <ul>
   * <li>offline, so do not wait for them in order to compute medium consistency
   * </li>
   * <li>alive, because we received heartbeats or changes (so their last alive
   * CSN has been updated to something past the oldest possible CSN), we have
   * enough info to compute medium consistency</li>
   * </ul>
   * In both cases, we have enough information to compute medium consistency
   * without waiting any further.
   */
  private boolean allInitialReplicasAreOfflineOrAlive()
  {
    for (DN baseDN : lastAliveCSNs)
    {
      for (CSN csn : lastAliveCSNs.getServerState(baseDN))
      {
        if (csn.getTime() == 0
            && replicasOffline.getCSN(baseDN, csn.getServerId()) == null)
        {
          // this is the oldest possible CSN, but the replica is not offline
          // we must wait for more up to date information from this replica
          return false;
        }
      }
    }
    return true;
  }

  /**
   * Restores in memory data needed to build the CNIndexDB, including the medium
   * consistency point.
   */
  private void initialize() throws ChangelogException, DirectoryException
  {
    final ChangeNumberIndexRecord newestRecord =
        changelogDB.getChangeNumberIndexDB().getNewestRecord();
    if (newestRecord != null)
    {
      // restore the mediumConsistencyRUV from DB
      mediumConsistencyRUV.update(
          new MultiDomainServerState(newestRecord.getPreviousCookie()));
      // Do not update with the newestRecord CSN
      // as it will be used for a sanity check later in the same method
    }

    // initialize the DB cursor and the last seen updates
    // to ensure the medium consistency CSN can move forward
    final ReplicationDomainDB domainDB = changelogDB.getReplicationDomainDB();
    for (Entry<DN, List<Integer>> entry
        : changelogState.getDomainToServerIds().entrySet())
    {
      final DN baseDN = entry.getKey();
      if (!isECLEnabledDomain(baseDN))
      {
        continue;
      }

      for (Integer serverId : entry.getValue())
      {
        /*
         * initialize with the oldest possible CSN in order for medium
         * consistency to wait for all replicas to be alive before moving
         * forward
         */
        lastAliveCSNs.update(baseDN, oldestPossibleCSN(serverId));
        // start after the actual CSN when initializing from the previous cookie
        final CSN csn = mediumConsistencyRUV.getCSN(baseDN, serverId);
        ensureCursorExists(baseDN, serverId, csn);
      }

      ServerState latestKnownState = domainDB.getDomainNewestCSNs(baseDN);
      lastAliveCSNs.update(baseDN, latestKnownState);
    }
    resetNextChangeForInsertDBCursor();

    if (newestRecord != null)
    {
      // restore the "previousCookie" state before shutdown
      final UpdateMsg record = nextChangeForInsertDBCursor.getRecord();
      // sanity check: ensure that when initializing the cursors at the previous
      // cookie, the next change we find is the newest record in the CNIndexDB
      if (!record.getCSN().equals(newestRecord.getCSN()))
      {
        throw new ChangelogException(
            ERR_CHANGE_NUMBER_INDEXER_INCONSISTENT_CSN_READ.get(newestRecord
                .getCSN().toStringUI(), record.getCSN().toStringUI()));
      }
      // Now we can update the mediumConsistencyRUV
      mediumConsistencyRUV.update(newestRecord.getBaseDN(), record.getCSN());
      nextChangeForInsertDBCursor.next();
    }

    for (Entry<DN, List<CSN>> entry : changelogState.getOfflineReplicas()
        .entrySet())
    {
      final DN baseDN = entry.getKey();
      final List<CSN> offlineCSNs = entry.getValue();
      for (CSN offlineCSN : offlineCSNs)
      {
        if (isECLEnabledDomain(baseDN))
        {
          replicasOffline.update(baseDN, offlineCSN);
          // a replica offline message could also be the very last time
          // we heard from this replica :)
          lastAliveCSNs.update(baseDN, offlineCSN);
        }
      }
    }

    // this will not be used any more. Discard for garbage collection.
    this.changelogState = null;
  }

  private CSN oldestPossibleCSN(int serverId)
  {
    return new CSN(0, 0, serverId);
  }

  private void resetNextChangeForInsertDBCursor() throws ChangelogException
  {
    final Map<DBCursor<UpdateMsg>, DN> cursors =
        new HashMap<DBCursor<UpdateMsg>, DN>();
    for (Entry<DN, Map<Integer, DBCursor<UpdateMsg>>> entry
        : this.allCursors.entrySet())
    {
      for (Entry<Integer, DBCursor<UpdateMsg>> entry2
          : entry.getValue().entrySet())
      {
        cursors.put(entry2.getValue(), entry.getKey());
      }
    }

    // CNIndexer manages the cursor itself,
    // so do not try to recycle exhausted cursors
    CompositeDBCursor<DN> result = new CompositeDBCursor<DN>(cursors, false);
    result.next();
    nextChangeForInsertDBCursor = result;
  }

  private boolean ensureCursorExists(DN baseDN, Integer serverId,
      CSN startAfterCSN) throws ChangelogException
  {
    Map<Integer, DBCursor<UpdateMsg>> map = allCursors.get(baseDN);
    if (map == null)
    {
      map = new ConcurrentSkipListMap<Integer, DBCursor<UpdateMsg>>();
      allCursors.put(baseDN, map);
    }
    DBCursor<UpdateMsg> cursor = map.get(serverId);
    if (cursor == null)
    {
      final ReplicationDomainDB domainDB = changelogDB.getReplicationDomainDB();
      cursor = domainDB.getCursorFrom(baseDN, serverId, startAfterCSN);
      map.put(serverId, cursor);
      return false;
    }
    return true;
  }

  /**
   * Returns the immediately preceding CSN.
   *
   * @param csn
   *          the CSN to use
   * @return the immediately preceding CSN or null if the provided CSN is null.
   */
  CSN getPrecedingCSN(CSN csn)
  {
    if (csn == null)
    {
      return null;
    }
    if (csn.getSeqnum() > 0)
    {
      return new CSN(csn.getTime(), csn.getSeqnum() - 1, csn.getServerId());
    }
    return new CSN(csn.getTime() - 1, Integer.MAX_VALUE, csn.getServerId());
  }

  /** {@inheritDoc} */
  @Override
  public void initiateShutdown()
  {
    super.initiateShutdown();
    synchronized (this)
    {
      notify();
    }
  }

  /** {@inheritDoc} */
  @Override
  public void run()
  {
    try
    {
      /*
       * initialize here to allow fast application start up and avoid errors due
       * cursors being created in a different thread to the one where they are
       * used.
       */
      initialize();

      while (!isShutdownInitiated())
      {
        try
        {
          if (!domainsToClear.isEmpty())
          {
            while (!domainsToClear.isEmpty())
            {
              final DN baseDNToClear = domainsToClear.first();
              removeCursors(baseDNToClear);
              // Only release the waiting thread
              // once this domain's state has been cleared.
              domainsToClear.remove(baseDNToClear);
            }
            resetNextChangeForInsertDBCursor();
          }
          else
          {
            final boolean createdCursors = createNewCursors();
            final boolean recycledCursors = recycleExhaustedCursors();
            if (createdCursors || recycledCursors)
            {
              resetNextChangeForInsertDBCursor();
            }
          }

          final UpdateMsg msg = nextChangeForInsertDBCursor.getRecord();
          if (msg == null)
          {
            synchronized (this)
            {
              if (isShutdownInitiated())
              {
                continue;
              }
              wait();
            }
            // loop to check whether new changes have been added to the
            // ReplicaDBs
            continue;
          }

          final CSN csn = msg.getCSN();
          final DN baseDN = nextChangeForInsertDBCursor.getData();
          // FIXME problem: what if the serverId is not part of the ServerState?
          // right now, change number will be blocked
          if (!canMoveForwardMediumConsistencyPoint(csn))
          {
            // the oldest record to insert is newer than the medium consistency
            // point. Let's wait for a change that can be published.
            synchronized (this)
            {
              // double check to protect against a missed call to notify()
              if (!canMoveForwardMediumConsistencyPoint(csn))
              {
                if (isShutdownInitiated())
                {
                  return;
                }
                wait();
                // loop to check if changes older than the medium consistency
                // point have been added to the ReplicaDBs
                continue;
              }
            }
          }


          // OK, the oldest change is older than the medium consistency point
          // let's publish it to the CNIndexDB.
          final String previousCookie = mediumConsistencyRUV.toString();
          final ChangeNumberIndexRecord record =
              new ChangeNumberIndexRecord(previousCookie, baseDN, csn);
          changelogDB.getChangeNumberIndexDB().addRecord(record);
          moveForwardMediumConsistencyPoint(csn, baseDN);
        }
        catch (InterruptedException ignored)
        {
          // was shutdown called? loop to figure it out.
          Thread.currentThread().interrupt();
        }
      }
    }
    catch (RuntimeException e)
    {
      // Nothing can be done about it.
      // Rely on the DirectoryThread uncaught exceptions handler
      // for logging error + alert.
      // LocalizableMessage logged here gives corrective information to the administrator.
      logger.trace(ERR_CHANGE_NUMBER_INDEXER_UNEXPECTED_EXCEPTION,
          getClass().getSimpleName(), stackTraceToSingleLineString(e));
      throw e;
    }
    catch (Exception e)
    {
      // Nothing can be done about it.
      // Rely on the DirectoryThread uncaught exceptions handler
      // for logging error + alert.
      // LocalizableMessage logged here gives corrective information to the administrator.
      logger.trace(ERR_CHANGE_NUMBER_INDEXER_UNEXPECTED_EXCEPTION,
          getClass().getSimpleName(), stackTraceToSingleLineString(e));
      throw new RuntimeException(e);
    }
    finally
    {
      removeCursors(DN.NULL_DN);
    }
  }

  private void moveForwardMediumConsistencyPoint(final CSN mcCSN,
      final DN mcBaseDN) throws ChangelogException
  {
    // update, so it becomes the previous cookie for the next change
    mediumConsistencyRUV.update(mcBaseDN, mcCSN);

    boolean callNextOnCursor = true;
    final int mcServerId = mcCSN.getServerId();
    final CSN offlineCSN = replicasOffline.getCSN(mcBaseDN, mcServerId);
    final CSN lastAliveCSN = lastAliveCSNs.getCSN(mcBaseDN, mcServerId);
    if (offlineCSN != null)
    {
      if (lastAliveCSN != null && offlineCSN.isOlderThan(lastAliveCSN))
      {
        // replica is back online, we can forget the last time it was offline
        replicasOffline.removeCSN(mcBaseDN, offlineCSN);
      }
      else if (offlineCSN.isOlderThan(mcCSN))
      {
        Pair<DBCursor<UpdateMsg>, Iterator<Entry<Integer, DBCursor<UpdateMsg>>>>
            pair = getCursor(mcBaseDN, mcCSN.getServerId());
        Iterator<Entry<Integer, DBCursor<UpdateMsg>>> iter = pair.getSecond();
        if (iter != null && !iter.hasNext())
        {
          /*
           * replica is not back online, Medium consistency point has gone past
           * its last offline time, and there are no more changes after the
           * offline CSN in the cursor: remove everything known about it:
           * cursor, offlineCSN from lastAliveCSN and remove all knowledge of
           * this replica from the medium consistency RUV.
           */
          iter.remove();
          StaticUtils.close(pair.getFirst());
          resetNextChangeForInsertDBCursor();
          callNextOnCursor = false;
          lastAliveCSNs.removeCSN(mcBaseDN, offlineCSN);
          mediumConsistencyRUV.removeCSN(mcBaseDN, offlineCSN);
        }
      }
    }

    if (callNextOnCursor)
    {
      // advance the cursor we just read from,
      // success/failure will be checked later
      nextChangeForInsertDBCursor.next();
    }
  }

  private void removeCursors(DN baseDN)
  {
    if (nextChangeForInsertDBCursor != null)
    {
      nextChangeForInsertDBCursor.close();
      nextChangeForInsertDBCursor = null;
    }
    if (DN.NULL_DN.equals(baseDN))
    {
      // close all cursors
      for (Map<Integer, DBCursor<UpdateMsg>> map : allCursors.values())
      {
        StaticUtils.close(map.values());
      }
      allCursors.clear();
      newCursors.clear();
    }
    else
    {
      // close cursors for this DN
      final Map<Integer, DBCursor<UpdateMsg>> map = allCursors.remove(baseDN);
      if (map != null)
      {
        StaticUtils.close(map.values());
      }
      newCursors.remove(baseDN);
    }
  }

  private Pair<DBCursor<UpdateMsg>, Iterator<Entry<Integer, DBCursor<UpdateMsg>>>>
      getCursor(final DN baseDN, final int serverId) throws ChangelogException
  {
    for (Entry<DN, Map<Integer, DBCursor<UpdateMsg>>> entry1
        : allCursors.entrySet())
    {
      if (baseDN.equals(entry1.getKey()))
      {
        for (Iterator<Entry<Integer, DBCursor<UpdateMsg>>> iter =
            entry1.getValue().entrySet().iterator(); iter.hasNext();)
        {
          final Entry<Integer, DBCursor<UpdateMsg>> entry2 = iter.next();
          if (serverId == entry2.getKey())
          {
            return Pair.of(entry2.getValue(), iter);
          }
        }
      }
    }
    return Pair.empty();
  }

  private boolean recycleExhaustedCursors() throws ChangelogException
  {
    boolean succesfullyRecycled = false;
    for (Map<Integer, DBCursor<UpdateMsg>> map : allCursors.values())
    {
      for (DBCursor<UpdateMsg> cursor : map.values())
      {
        // try to recycle it by calling next()
        if (cursor.getRecord() == null && cursor.next())
        {
          succesfullyRecycled = true;
        }
      }
    }
    return succesfullyRecycled;
  }

  private boolean createNewCursors() throws ChangelogException
  {
    if (!newCursors.isEmpty())
    {
      boolean newCursorAdded = false;
      for (Iterator<Entry<Pair<DN, Integer>, CSN>> iter =
          newCursors.entrySet().iterator(); iter.hasNext();)
      {
        final Entry<Pair<DN, Integer>, CSN> entry = iter.next();
        final DN baseDN = entry.getKey().getFirst();
        final CSN csn = entry.getValue();
        // start after preceding CSN so the first CSN read will exactly be the
        // current one
        final CSN startFromCSN = getPrecedingCSN(csn);
        if (!ensureCursorExists(baseDN, csn.getServerId(), startFromCSN))
        {
          newCursorAdded = true;
        }
        iter.remove();
      }
      return newCursorAdded;
    }
    return false;
  }

  /**
   * Asks the current thread to clear its state for the specified domain.
   * <p>
   * Note: This method blocks the current thread until state is cleared.
   *
   * @param baseDN the baseDN to be cleared from this thread's state.
   *               {@code null} and {@link DN#NULL_DN} mean "clear all domains".
   */
  public void clear(DN baseDN)
  {
    // Use DN.NULL_DN to say "clear all domains"
    final DN baseDNToClear = baseDN != null ? baseDN : DN.NULL_DN;
    domainsToClear.add(baseDNToClear);
    while (domainsToClear.contains(baseDNToClear)
        && !State.TERMINATED.equals(getState()))
    {
      // wait until clear() has been done by thread, always waking it up
      synchronized (this)
      {
        notify();
      }
      // ensures thread wait that this thread's state is cleaned up
      Thread.yield();
    }
  }

}

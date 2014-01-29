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

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizableMessageBuilder;
import org.opends.server.admin.std.server.ReplicationServerCfg;
import org.opends.server.config.ConfigException;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.opends.server.replication.common.CSN;
import org.opends.server.replication.common.ServerState;
import org.opends.server.replication.protocol.UpdateMsg;
import org.opends.server.replication.server.ChangelogState;
import org.opends.server.replication.server.ReplicationServer;
import org.opends.server.replication.server.changelog.api.*;
import org.opends.server.types.DN;
import org.opends.server.util.StaticUtils;

import com.forgerock.opendj.util.Pair;

import static org.opends.messages.ReplicationMessages.*;
import static org.opends.server.util.StaticUtils.*;

/**
 * JE implementation of the ChangelogDB interface.
 */
public class JEChangelogDB implements ChangelogDB, ReplicationDomainDB
{
  /** The tracer object for the debug logger. */
  protected static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /**
   * This map contains the List of updates received from each LDAP server.
   * <p>
   * When removing a domainMap, code:
   * <ol>
   * <li>first get the domainMap</li>
   * <li>synchronized on the domainMap</li>
   * <li>remove the domainMap</li>
   * <li>then check it's not null</li>
   * <li>then close all inside</li>
   * </ol>
   * When creating a JEReplicaDB, synchronize on the domainMap to avoid
   * concurrent shutdown.
   */
  private final ConcurrentMap<DN, ConcurrentMap<Integer, JEReplicaDB>>
      domainToReplicaDBs =
          new ConcurrentHashMap<DN, ConcurrentMap<Integer, JEReplicaDB>>();
  private ReplicationDbEnv dbEnv;
  private ReplicationServerCfg config;
  private final File dbDirectory;

  /**
   * The handler of the changelog database, the database stores the relation
   * between a change number and the associated cookie.
   * <p>
   * Guarded by cnIndexDBLock
   */
  private JEChangeNumberIndexDB cnIndexDB;
  private final AtomicReference<ChangeNumberIndexer> cnIndexer =
      new AtomicReference<ChangeNumberIndexer>();

  /** Used for protecting {@link ChangeNumberIndexDB} related state. */
  private final Object cnIndexDBLock = new Object();

  /** The local replication server. */
  private final ReplicationServer replicationServer;
  private AtomicBoolean shutdown = new AtomicBoolean();

  private static final DBCursor<UpdateMsg> EMPTY_CURSOR =
      new DBCursor<UpdateMsg>()
  {

    @Override
    public boolean next()
    {
      return false;
    }

    @Override
    public UpdateMsg getRecord()
    {
      return null;
    }

    @Override
    public void close()
    {
      // empty
    }

    @Override
    public String toString()
    {
      return "EmptyDBCursor<UpdateMsg>";
    }
  };

  /**
   * Builds an instance of this class.
   *
   * @param replicationServer
   *          the local replication server.
   * @param config
   *          the replication server configuration
   * @throws ConfigException
   *           if a problem occurs opening the supplied directory
   */
  public JEChangelogDB(ReplicationServer replicationServer,
      ReplicationServerCfg config) throws ConfigException
  {
    this.config = config;
    this.replicationServer = replicationServer;
    this.dbDirectory = makeDir(config.getReplicationDBDirectory());
  }

  private File makeDir(String dbDirName) throws ConfigException
  {
    // Check that this path exists or create it.
    File dbDirectory = getFileForPath(dbDirName);
    try
    {
      if (!dbDirectory.exists())
      {
        dbDirectory.mkdir();
      }
      return dbDirectory;
    }
    catch (Exception e)
    {
      if (logger.isTraceEnabled())
        logger.traceException(e);

      final LocalizableMessageBuilder mb = new LocalizableMessageBuilder();
      mb.append(e.getLocalizedMessage());
      mb.append(" ");
      mb.append(String.valueOf(dbDirectory));
      LocalizableMessage msg = ERR_FILE_CHECK_CREATE_FAILED.get(mb.toString());
      throw new ConfigException(msg, e);
    }
  }

  private Map<Integer, JEReplicaDB> getDomainMap(DN baseDN)
  {
    final Map<Integer, JEReplicaDB> domainMap = domainToReplicaDBs.get(baseDN);
    if (domainMap != null)
    {
      return domainMap;
    }
    return Collections.emptyMap();
  }

  private JEReplicaDB getReplicaDB(DN baseDN, int serverId)
  {
    return getDomainMap(baseDN).get(serverId);
  }

  /**
   * Provision resources for the specified serverId in the specified replication
   * domain.
   *
   * @param baseDN
   *          the replication domain where to add the serverId
   * @param serverId
   *          the server Id to add to the replication domain
   * @throws ChangelogException
   *           If a database error happened.
   */
  private void commission(DN baseDN, int serverId, ReplicationServer rs)
      throws ChangelogException
  {
    getOrCreateReplicaDB(baseDN, serverId, rs);
  }

  /**
   * Returns a {@link JEReplicaDB}, possibly creating it.
   *
   * @param baseDN
   *          the baseDN for which to create a ReplicaDB
   * @param serverId
   *          the serverId for which to create a ReplicaDB
   * @param rs
   *          the ReplicationServer
   * @return a Pair with the JEReplicaDB and a boolean indicating whether it had
   *         to be created
   * @throws ChangelogException
   *           if a problem occurred with the database
   */
  Pair<JEReplicaDB, Boolean> getOrCreateReplicaDB(DN baseDN,
      int serverId, ReplicationServer rs) throws ChangelogException
  {
    while (!shutdown.get())
    {
      final ConcurrentMap<Integer, JEReplicaDB> domainMap =
          getExistingOrNewDomainMap(baseDN);
      final Pair<JEReplicaDB, Boolean> result =
          getExistingOrNewReplicaDB(domainMap, serverId, baseDN, rs);
      if (result != null)
      {
        return result;
      }
    }
    throw new ChangelogException(
        ERR_CANNOT_CREATE_REPLICA_DB_BECAUSE_CHANGELOG_DB_SHUTDOWN.get());
  }

  private ConcurrentMap<Integer, JEReplicaDB> getExistingOrNewDomainMap(
      DN baseDN)
  {
    // happy path: the domainMap already exists
    final ConcurrentMap<Integer, JEReplicaDB> currentValue =
        domainToReplicaDBs.get(baseDN);
    if (currentValue != null)
    {
      return currentValue;
    }

    // unlucky, the domainMap does not exist: take the hit and create the
    // newValue, even though the same could be done concurrently by another
    // thread
    final ConcurrentMap<Integer, JEReplicaDB> newValue =
        new ConcurrentHashMap<Integer, JEReplicaDB>();
    final ConcurrentMap<Integer, JEReplicaDB> previousValue =
        domainToReplicaDBs.putIfAbsent(baseDN, newValue);
    if (previousValue != null)
    {
      // there was already a value associated to the key, let's use it
      return previousValue;
    }
    return newValue;
  }

  private Pair<JEReplicaDB, Boolean> getExistingOrNewReplicaDB(
      final ConcurrentMap<Integer, JEReplicaDB> domainMap, int serverId,
      DN baseDN, ReplicationServer rs) throws ChangelogException
  {
    // happy path: the JEReplicaDB already exists
    JEReplicaDB currentValue = domainMap.get(serverId);
    if (currentValue != null)
    {
      return Pair.of(currentValue, false);
    }

    // unlucky, the JEReplicaDB does not exist: take the hit and synchronize
    // on the domainMap to create a new ReplicaDB
    synchronized (domainMap)
    {
      // double-check
      currentValue = domainMap.get(serverId);
      if (currentValue != null)
      {
        return Pair.of(currentValue, false);
      }

      if (domainToReplicaDBs.get(baseDN) != domainMap)
      {
        // the domainMap could have been concurrently removed because
        // 1) a shutdown was initiated or 2) an initialize was called.
        // Return will allow the code to:
        // 1) shutdown properly or 2) lazily recreate the JEReplicaDB
        return null;
      }

      final JEReplicaDB newValue = new JEReplicaDB(serverId, baseDN, rs, dbEnv);
      domainMap.put(serverId, newValue);
      return Pair.of(newValue, true);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void initializeDB()
  {
    try
    {
      final File dbDir = getFileForPath(config.getReplicationDBDirectory());
      dbEnv = new ReplicationDbEnv(dbDir.getAbsolutePath(), replicationServer);
      final ChangelogState changelogState = dbEnv.readChangelogState();
      initializeChangelogState(changelogState);
      if (config.isComputeChangeNumber())
      {
        final ChangeNumberIndexer indexer =
            new ChangeNumberIndexer(this, changelogState);
        if (cnIndexer.compareAndSet(null, indexer))
        {
          indexer.start();
        }
      }
    }
    catch (ChangelogException e)
    {
      if (logger.isTraceEnabled())
        logger.traceException(e);

      logger.error(ERR_COULD_NOT_READ_DB.get(this.dbDirectory.getAbsolutePath(),
          e.getLocalizedMessage()));
    }
  }

  private void initializeChangelogState(final ChangelogState changelogState)
      throws ChangelogException
  {
    for (Map.Entry<DN, Long> entry :
      changelogState.getDomainToGenerationId().entrySet())
    {
      replicationServer.getReplicationServerDomain(entry.getKey(), true)
          .initGenerationID(entry.getValue());
    }
    for (Map.Entry<DN, List<Integer>> entry :
      changelogState.getDomainToServerIds().entrySet())
    {
      for (int serverId : entry.getValue())
      {
        commission(entry.getKey(), serverId, replicationServer);
      }
    }
  }

  private void shutdownCNIndexDB() throws ChangelogException
  {
    synchronized (cnIndexDBLock)
    {
      if (cnIndexDB != null)
      {
        cnIndexDB.shutdown();
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public void shutdownDB() throws ChangelogException
  {
    if (!this.shutdown.compareAndSet(false, true))
    { // shutdown has already been initiated
      return;
    }

    // Remember the first exception because :
    // - we want to try to remove everything we want to remove
    // - then throw the first encountered exception
    ChangelogException firstException = null;

    final ChangeNumberIndexer indexer = cnIndexer.get();
    if (indexer != null)
    {
      indexer.initiateShutdown();
      cnIndexer.compareAndSet(indexer, null);
    }
    try
    {
      shutdownCNIndexDB();
    }
    catch (ChangelogException e)
    {
      firstException = e;
    }

    for (Iterator<ConcurrentMap<Integer, JEReplicaDB>> it =
        this.domainToReplicaDBs.values().iterator(); it.hasNext();)
    {
      final ConcurrentMap<Integer, JEReplicaDB> domainMap = it.next();
      synchronized (domainMap)
      {
        it.remove();
        for (JEReplicaDB replicaDB : domainMap.values())
        {
          replicaDB.shutdown();
        }
      }
    }

    if (dbEnv != null)
    {
      dbEnv.shutdown();
    }

    if (firstException != null)
    {
      throw firstException;
    }
  }

  /**
   * Clears all content from the changelog database, but leaves its directory on
   * the filesystem.
   *
   * @throws ChangelogException
   *           If a database problem happened
   */
  public void clearDB() throws ChangelogException
  {
    if (!dbDirectory.exists())
    {
      return;
    }

    // Remember the first exception because :
    // - we want to try to remove everything we want to remove
    // - then throw the first encountered exception
    ChangelogException firstException = null;

    final ChangeNumberIndexer indexer = cnIndexer.get();
    if (indexer != null)
    {
      indexer.clear();
    }

    for (DN baseDN : this.domainToReplicaDBs.keySet())
    {
      removeDomain(baseDN);
    }

    synchronized (cnIndexDBLock)
    {
      if (cnIndexDB != null)
      {
        try
        {
          cnIndexDB.clear();
        }
        catch (ChangelogException e)
        {
          firstException = e;
        }

        try
        {
          shutdownCNIndexDB();
        }
        catch (ChangelogException e)
        {
          if (firstException == null)
          {
            firstException = e;
          }
          else if (logger.isTraceEnabled())
            logger.traceException(e);
        }

        cnIndexDB = null;
      }
    }

    if (firstException != null)
    {
      throw firstException;
    }
  }

  /** {@inheritDoc} */
  @Override
  public void removeDB() throws ChangelogException
  {
    shutdownDB();
    StaticUtils.recursiveDelete(dbDirectory);
  }


  /** {@inheritDoc} */
  @Override
  public long getDomainChangesCount(DN baseDN)
  {
    long entryCount = 0;
    for (JEReplicaDB replicaDB : getDomainMap(baseDN).values())
    {
      entryCount += replicaDB.getChangesCount();
    }
    return entryCount;
  }

  /** {@inheritDoc} */
  @Override
  public ServerState getDomainOldestCSNs(DN baseDN)
  {
    final ServerState result = new ServerState();
    for (JEReplicaDB replicaDB : getDomainMap(baseDN).values())
    {
      result.update(replicaDB.getOldestCSN());
    }
    return result;
  }

  /** {@inheritDoc} */
  @Override
  public ServerState getDomainNewestCSNs(DN baseDN)
  {
    final ServerState result = new ServerState();
    for (JEReplicaDB replicaDB : getDomainMap(baseDN).values())
    {
      result.update(replicaDB.getNewestCSN());
    }
    return result;
  }

  /** {@inheritDoc} */
  @Override
  public ServerState getDomainLastAliveCSNs(DN baseDN)
  {
    final ChangeNumberIndexer indexer = this.cnIndexer.get();
    if (indexer != null)
    {
      final ServerState results = indexer.getDomainLastAliveCSNs(baseDN);
      if (results != null)
      {
        // return a copy to protect against concurrent modifications
        return results.duplicate();
      }
    }
    return new ServerState();
  }

  /** {@inheritDoc} */
  @Override
  public void removeDomain(DN baseDN) throws ChangelogException
  {
    // Remember the first exception because :
    // - we want to try to remove everything we want to remove
    // - then throw the first encountered exception
    ChangelogException firstException = null;

    // 1- clear the replica DBs
    Map<Integer, JEReplicaDB> domainMap = domainToReplicaDBs.get(baseDN);
    if (domainMap != null)
    {
      synchronized (domainMap)
      {
        domainMap = domainToReplicaDBs.remove(baseDN);
        for (JEReplicaDB replicaDB : domainMap.values())
        {
          try
          {
            replicaDB.clear();
          }
          catch (ChangelogException e)
          {
            firstException = e;
          }
          replicaDB.shutdown();
        }
      }
    }

    // 2- clear the ChangeNumber index DB
    synchronized (cnIndexDBLock)
    {
      if (cnIndexDB != null)
      {
        try
        {
          cnIndexDB.clear(baseDN);
        }
        catch (ChangelogException e)
        {
          if (firstException == null)
          {
            firstException = e;
          }
          else if (logger.isTraceEnabled())
            logger.traceException(e);
        }
      }
    }

    // 3- clear the changelogstate DB
    try
    {
      dbEnv.clearGenerationId(baseDN);
    }
    catch (ChangelogException e)
    {
      if (firstException == null)
      {
        firstException = e;
      }
      else if (logger.isTraceEnabled())
        logger.traceException(e);
    }

    if (firstException != null)
    {
      throw firstException;
    }
  }

  /** {@inheritDoc} */
  @Override
  public void setPurgeDelay(long delay)
  {
    final JEChangeNumberIndexDB cnIndexDB = this.cnIndexDB;
    if (cnIndexDB != null)
    {
      cnIndexDB.setPurgeDelay(delay);
    }
    for (Map<Integer, JEReplicaDB> domainMap : domainToReplicaDBs.values())
    {
      for (JEReplicaDB replicaDB : domainMap.values())
      {
        replicaDB.setPurgeDelay(delay);
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public void setComputeChangeNumber(boolean computeChangeNumber)
      throws ChangelogException
  {
    final ChangeNumberIndexer indexer;
    if (computeChangeNumber)
    {
      final ChangelogState changelogState = dbEnv.readChangelogState();
      indexer = new ChangeNumberIndexer(this, changelogState);
      if (cnIndexer.compareAndSet(null, indexer))
      {
        indexer.start();
      }
    }
    else
    {
      indexer = cnIndexer.getAndSet(null);
      if (indexer != null)
      {
        indexer.initiateShutdown();
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public long getDomainLatestTrimDate(DN baseDN)
  {
    long latest = 0;
    for (JEReplicaDB replicaDB : getDomainMap(baseDN).values())
    {
      if (latest == 0 || latest < replicaDB.getLatestTrimDate())
      {
        latest = replicaDB.getLatestTrimDate();
      }
    }
    return latest;
  }

  /** {@inheritDoc} */
  @Override
  public ChangeNumberIndexDB getChangeNumberIndexDB()
  {
    return getChangeNumberIndexDB(true);
  }

  /**
   * Returns the {@link ChangeNumberIndexDB} object.
   *
   * @param startTrimmingThread
   *          whether the trimming thread should be started
   * @return the {@link ChangeNumberIndexDB} object
   */
  ChangeNumberIndexDB getChangeNumberIndexDB(boolean startTrimmingThread)
  {
    synchronized (cnIndexDBLock)
    {
      if (cnIndexDB == null)
      {
        try
        {
          cnIndexDB = new JEChangeNumberIndexDB(replicationServer, this.dbEnv);
          if (startTrimmingThread)
          {
            cnIndexDB.startTrimmingThread();
          }
        }
        catch (Exception e)
        {
          if (logger.isTraceEnabled())
            logger.traceException(e);
          logger.error(ERR_CHANGENUMBER_DATABASE.get(e.getLocalizedMessage()));
        }
      }
      return cnIndexDB;
    }
  }

  /** {@inheritDoc} */
  @Override
  public ReplicationDomainDB getReplicationDomainDB()
  {
    return this;
  }

  /** {@inheritDoc} */
  @Override
  public DBCursor<UpdateMsg> getCursorFrom(DN baseDN, CSN startAfterCSN)
      throws ChangelogException
  {
    // Builds a new serverState for all the serverIds in the replication domain
    // to ensure we get cursors starting after the provided CSN.
    return getCursorFrom(baseDN, buildServerState(baseDN, startAfterCSN));
  }

  /** {@inheritDoc} */
  @Override
  public DBCursor<UpdateMsg> getCursorFrom(DN baseDN,
      ServerState startAfterServerState) throws ChangelogException
  {
    final Set<Integer> serverIds = getDomainMap(baseDN).keySet();
    final Map<DBCursor<UpdateMsg>, Void> cursors =
        new HashMap<DBCursor<UpdateMsg>, Void>(serverIds.size());
    for (int serverId : serverIds)
    {
      // get the last already sent CSN from that server to get a cursor
      final CSN lastCSN = startAfterServerState != null ?
          startAfterServerState.getCSN(serverId) : null;
      cursors.put(getCursorFrom(baseDN, serverId, lastCSN), null);
    }
    return new CompositeDBCursor<Void>(cursors);
  }

  /** {@inheritDoc} */
  @Override
  public DBCursor<UpdateMsg> getCursorFrom(DN baseDN, int serverId,
      CSN startAfterCSN) throws ChangelogException
  {
    JEReplicaDB replicaDB = getReplicaDB(baseDN, serverId);
    if (replicaDB != null)
    {
      DBCursor<UpdateMsg> cursor = replicaDB.generateCursorFrom(startAfterCSN);
      cursor.next();
      return cursor;
    }
    return EMPTY_CURSOR;
  }

  private ServerState buildServerState(DN baseDN, CSN startAfterCSN)
  {
    final ServerState result = new ServerState();
    if (startAfterCSN == null)
    {
      return result;
    }

    for (int serverId : getDomainMap(baseDN).keySet())
    {
      if (serverId == startAfterCSN.getServerId())
      {
        // reuse the provided CSN one as it is the most accurate
        result.update(startAfterCSN);
      }
      else
      {
        // build a new CSN, ignoring the seqNum since it is irrelevant for
        // a different serverId
        final CSN csn = startAfterCSN; // only used for increased readability
        result.update(new CSN(csn.getTime(), 0, serverId));
      }
    }
    return result;
  }

  /** {@inheritDoc} */
  @Override
  public boolean publishUpdateMsg(DN baseDN, UpdateMsg updateMsg)
      throws ChangelogException
  {
    final Pair<JEReplicaDB, Boolean> pair = getOrCreateReplicaDB(baseDN,
        updateMsg.getCSN().getServerId(), replicationServer);
    final JEReplicaDB replicaDB = pair.getFirst();
    final boolean wasCreated = pair.getSecond();

    replicaDB.add(updateMsg);
    final ChangeNumberIndexer indexer = cnIndexer.get();
    if (indexer != null)
    {
      indexer.publishUpdateMsg(baseDN, updateMsg);
    }
    return wasCreated;
  }

  /** {@inheritDoc} */
  @Override
  public void replicaHeartbeat(DN baseDN, CSN heartbeatCSN)
  {
    final ChangeNumberIndexer indexer = cnIndexer.get();
    if (indexer != null)
    {
      indexer.publishHeartbeat(baseDN, heartbeatCSN);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void replicaOffline(DN baseDN, CSN offlineCSN)
  {
    final ChangeNumberIndexer indexer = cnIndexer.get();
    if (indexer != null)
    {
      indexer.replicaOffline(baseDN, offlineCSN);
    }
    // TODO save this state in the changelogStateDB?
  }
}

/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE
 * or https://OpenDS.dev.java.net/OpenDS.LICENSE.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE.  If applicable,
 * add the following below this CDDL HEADER, with the fields enclosed
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.extensions;

import com.sleepycat.bind.EntryBinding;
import com.sleepycat.bind.serial.SerialBinding;
import com.sleepycat.bind.serial.StoredClassCatalog;
import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.locks.Lock;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.EnvironmentMutableConfig;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.OperationStatus;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.opends.server.api.Backend;
import org.opends.server.api.ConfigurableComponent;
import org.opends.server.api.EntryCache;
import org.opends.server.config.ConfigAttribute;
import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.config.IntegerConfigAttribute;
import org.opends.server.config.IntegerWithUnitConfigAttribute;
import org.opends.server.config.StringConfigAttribute;
import org.opends.server.core.DirectoryServer;
import org.opends.server.protocols.asn1.ASN1Element;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.ErrorLogCategory;
import org.opends.server.types.ErrorLogSeverity;
import org.opends.server.types.InitializationException;
import org.opends.server.types.LockType;
import org.opends.server.types.ResultCode;
import org.opends.server.types.SearchFilter;
import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.loggers.debug.DebugLogger.debugCaught;
import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import org.opends.server.types.DebugLogLevel;
import org.opends.server.types.LockManager;
import org.opends.server.types.ObjectClass;
import static org.opends.server.loggers.Error.*;
import static org.opends.server.messages.ExtensionsMessages.*;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;

/**
 * This class defines a Directory Server entry cache that uses a FIFO to keep
 * track of the entries.  Entries that have been in the cache the longest are
 * the most likely candidates for purging if space is needed.  In contrast to
 * other cache structures, the selection of entries to purge is not based on
 * how frequently or recently the entries have been accessed.  This requires
 * significantly less locking (it will only be required when an entry is added
 * or removed from the cache, rather than each time an entry is accessed).
 * <BR><BR>
 * Cache sizing is based on the percentage of free memory within the JVM, such
 * that if enough memory is free, then adding an entry to the cache will not
 * require purging, but if more than a specified percentage of the available
 * memory within the JVM is already consumed, then one or more entries will need
 * to be removed in order to make room for a new entry.  It is also possible to
 * configure a maximum number of entries for the cache.  If this is specified,
 * then the number of entries will not be allowed to exceed this value, but it
 * may not be possible to hold this many entries if the available memory fills
 * up first.
 * <BR><BR>
 * Other configurable parameters for this cache include the maximum length of
 * time to block while waiting to acquire a lock, and a set of filters that may
 * be used to define criteria for determining which entries are stored in the
 * cache.  If a filter list is provided, then only entries matching at least one
 * of the given filters will be stored in the cache.
 */
public class FileSystemEntryCache
        extends EntryCache
        implements ConfigurableComponent {
  /**
   * The set of time units that will be used for expressing the task retention
   * time.
   */
  private static final LinkedHashMap<String,Double> timeUnits =
          new LinkedHashMap<String,Double>();
  
  // The DN of the configuration entry for this entry cache.
  private DN configEntryDN;
  
  // The set of filters that define the entries that should be excluded from the
  // cache.
  private HashSet<SearchFilter> excludeFilters;
  
  // The set of filters that define the entries that should be included in the
  // cache.
  private HashSet<SearchFilter> includeFilters;
  
  // The maximum percentage of JVM memory that should be used by the cache.
  private int maxMemoryPercent;
  
  // The maximum amount of memory in bytes that the JVM will be allowed to use
  // before we need to start purging entries.
  private long maxAllowedMemory;
  
  // The maximum number of entries that may be held in the cache.
  private long maxEntries;
  
  // The reference to the Java runtime to use to determine the amount of memory
  // currently in use.
  private Runtime runtime;
  
  // The lock used to provide threadsafe access when changing the contents of
  // the cache.
  private ReentrantReadWriteLock cacheLock;
  private Lock cacheReadLock;
  private Lock cacheWriteLock;

  // The maximum length of time to try to obtain a lock before giving up.
  private long lockTimeout;
  
  // The mapping between entry backends/IDs and DNs.
  private LinkedHashMap<Backend,LinkedHashMap<Long,DN>> backendMap;
  
  // The mapping between DNs and IDs.
  private LinkedHashMapRotator<DN,Long> dnMap;
  
  // BDB JE environment and database related fields for this cache.
  private Environment entryCacheEnv;
  private EnvironmentConfig entryCacheEnvConfig;
  private EnvironmentMutableConfig entryCacheEnvMutableConfig;
  private DatabaseConfig entryCacheDBConfig;
  private Database entryCacheDB;
  private Database entryCacheClassDB;
  private StoredClassCatalog classCatalog;
  private EntryBinding entryCacheDataBinding;
  
  private static final String ENTRYCACHEDBNAME = "EntryCacheDB";
  private static final String INDEXCLASSDBNAME = "IndexClassDB";
  private static final String INDEXKEY = "EntryCacheIndex";
  
  static
  {
    timeUnits.put(TIME_UNIT_MILLISECONDS_ABBR, 1D);
    timeUnits.put(TIME_UNIT_MILLISECONDS_FULL, 1D);
    timeUnits.put(TIME_UNIT_SECONDS_ABBR, 1000D);
    timeUnits.put(TIME_UNIT_SECONDS_FULL, 1000D);
  }
  
  
  
  /**
   * Creates a new instance of this FIFO entry cache.
   */
  public FileSystemEntryCache() {
    super();
    
    
    // All initialization should be performed in the initializeEntryCache.
  }
  
  
  
  /**
   * Initializes this entry cache implementation so that it will be available
   * for storing and retrieving entries.
   *
   * @param  configEntry  The configuration entry containing the settings to use
   *                      for this entry cache.
   *
   * @throws  ConfigException  If there is a problem with the provided
   *                           configuration entry that would prevent this
   *                           entry cache from being used.
   *
   * @throws  InitializationException  If a problem occurs during the
   *                                   initialization process that is not
   *                                   related to the configuration.
   */
  public void initializeEntryCache(ConfigEntry configEntry)
          throws ConfigException, InitializationException {
    configEntryDN = configEntry.getDN();
    
    // Initialize the cache structures.
    backendMap = new LinkedHashMap<Backend,LinkedHashMap<Long,DN>>();
    // TODO: FIFO by default, add cfg and args to allow for LRU.
    dnMap = new LinkedHashMapRotator<DN,Long>();
    cacheLock = new ReentrantReadWriteLock();
    cacheReadLock = cacheLock.readLock();
    cacheWriteLock = cacheLock.writeLock();
    runtime = Runtime.getRuntime();
    
    // Open JE environment and primary database.
    
    try {
      entryCacheEnvConfig = new EnvironmentConfig();
      entryCacheEnvConfig.setConfigParam("je.log.fileMax", "10485760");
      entryCacheEnvConfig.setConfigParam("je.cleaner.minUtilization", "90");
      entryCacheEnvConfig.setConfigParam("je.cleaner.maxBatchFiles", "1");
      entryCacheEnvConfig.setConfigParam("je.cleaner.minAge", "1");
      entryCacheEnvConfig.setConfigParam("je.cleaner.minFileUtilization", "50");
      entryCacheEnvConfig.setConfigParam("je.checkpointer.bytesInterval", 
                                         "10485760");
      // TODO: add cfg and logic stuff for persistent cache.
      entryCacheEnvConfig.setAllowCreate(true);
      entryCacheEnv =
              // TODO: add cfg property for this.
              //new Environment(new File("/Volumes/RamDisk"),
              new Environment(new File("/tmp"),
              entryCacheEnvConfig);
      entryCacheEnvMutableConfig = new EnvironmentMutableConfig();
      // set JE cache size.
      // entryCacheEnvMutableConfig.setCacheSize(8388608);
      // entryCacheEnv.setMutableConfig(entryCacheEnvMutableConfig);
      entryCacheDBConfig = new DatabaseConfig();
      entryCacheDBConfig.setAllowCreate(true);
      entryCacheDB = entryCacheEnv.openDatabase(null,
              ENTRYCACHEDBNAME, entryCacheDBConfig);
      entryCacheClassDB = 
        entryCacheEnv.openDatabase(null, INDEXCLASSDBNAME, entryCacheDBConfig);
      // Instantiate the class catalog
      classCatalog = new StoredClassCatalog(entryCacheClassDB);
      entryCacheDataBinding = 
          new SerialBinding(classCatalog, FileSystemEntryCacheIndex.class);
      
      // Retrieve index.
      FileSystemEntryCacheIndex entryCacheIndex = 
          new FileSystemEntryCacheIndex();
      DatabaseEntry indexData = new DatabaseEntry();
      DatabaseEntry indexKey = new DatabaseEntry(INDEXKEY.getBytes());
      
      OperationStatus jdbStatus = null;
      jdbStatus = entryCacheDB.get(null, indexKey, indexData, LockMode.DEFAULT);
      
      if (jdbStatus == OperationStatus.SUCCESS) {
        entryCacheIndex =
          (FileSystemEntryCacheIndex) 
            entryCacheDataBinding.entryToObject(indexData);
      }
      // Check index state.
      if ((entryCacheIndex.dnMap.isEmpty()) ||
          (entryCacheIndex.backendMap.isEmpty()) ||
          (entryCacheIndex.offlineState.isEmpty())) {
        // Truncate entry cache db.
        clear();
      } else {
        // Restore entry cache maps.
        maxEntries = DEFAULT_FIFOCACHE_MAX_ENTRIES;
        // Convert cache index maps to entry cache maps.
        Set backendSet = entryCacheIndex.backendMap.keySet();
        Iterator backendIterator = backendSet.iterator();
        while (backendIterator.hasNext()) {
          String backend = (String) backendIterator.next();
          LinkedHashMap<Long,String> entriesMap = 
              entryCacheIndex.backendMap.get(backend);
          Set entriesSet = entriesMap.keySet();
          Iterator entriesIterator = entriesSet.iterator();
          LinkedHashMap<Long,DN> entryMap = new LinkedHashMap<Long,DN>();
          while (entriesIterator.hasNext()) {
            Long entryID = (Long) entriesIterator.next();
            String entryStringDN = entriesMap.get(entryID);
            DN entryDN = DN.decode(entryStringDN);
            dnMap.put(entryDN, entryID);
            entryMap.put(entryID, entryDN);
          }
          backendMap.put(DirectoryServer.getBackend(backend), entryMap);
        }

        // Compare last known offline states to offline states on startup.
        ConcurrentHashMap currentBackendsState = 
            DirectoryServer.getOfflineBackendsStateIDs();      
        Set offlineBackendSet = entryCacheIndex.offlineState.keySet();
        Iterator offlineBackendIterator = offlineBackendSet.iterator();
        while (offlineBackendIterator.hasNext()) {
          String backend = (String) offlineBackendIterator.next();
          Long offlineId = entryCacheIndex.offlineState.get(backend);
          Long currentId = (Long) currentBackendsState.get(backend);
          if ( !(offlineId.equals(currentId)) ) {
            // Remove cache entries specific to this backend.
            clearBackend(DirectoryServer.getBackend(backend));
          }
        }
      }
      
      System.out.printf("<<<DEBUG>>> initializeEntryCache: index get: %s\n",
          jdbStatus.toString());
      
    } catch (Exception e) {
      e.printStackTrace();
      // TODO: disable this cache.
      if (debugEnabled()) {
        debugCaught(DebugLogLevel.ERROR, e);
      }
      
      // Log an error message.
      logError(ErrorLogCategory.EXTENSIONS, ErrorLogSeverity.SEVERE_ERROR,
              /* TODO: */ 0, "Failed to initialize FS entry cache",
              stackTraceToSingleLineString(e));
    }
    
    // Determine the maximum memory usage as a percentage of the total JVM
    // memory.
    maxMemoryPercent = DEFAULT_FIFOCACHE_MAX_MEMORY_PCT;
    int msgID = MSGID_FIFOCACHE_DESCRIPTION_MAX_MEMORY_PCT;
    IntegerConfigAttribute maxMemoryPctStub =
            new IntegerConfigAttribute(ATTR_FIFOCACHE_MAX_MEMORY_PCT,
            getMessage(msgID), true, false, false, true,
            1, true, 100);
    try {
      IntegerConfigAttribute maxMemoryPctAttr =
              (IntegerConfigAttribute)
              configEntry.getConfigAttribute(maxMemoryPctStub);
      if (maxMemoryPctAttr == null) {
        // This is fine -- we'll just use the default.
      } else {
        maxMemoryPercent = maxMemoryPctAttr.activeIntValue();
      }
    } catch (Exception e) {
      if (debugEnabled()) {
        debugCaught(DebugLogLevel.ERROR, e);
      }
      
      // Log an error message.
      logError(ErrorLogCategory.CONFIGURATION, ErrorLogSeverity.SEVERE_ERROR,
              MSGID_FIFOCACHE_CANNOT_DETERMINE_MAX_MEMORY_PCT,
              String.valueOf(configEntryDN), stackTraceToSingleLineString(e),
              maxMemoryPercent);
    }
    
    maxAllowedMemory = runtime.maxMemory() / 100 * maxMemoryPercent;
    
    
    // Determine the maximum number of entries that we will allow in the cache.
    maxEntries = DEFAULT_FIFOCACHE_MAX_ENTRIES;
    msgID = MSGID_FIFOCACHE_DESCRIPTION_MAX_ENTRIES;
    IntegerConfigAttribute maxEntriesStub =
            new IntegerConfigAttribute(ATTR_FIFOCACHE_MAX_ENTRIES,
            getMessage(msgID), true, false, false,
            true, 0, false, 0);
    try {
      IntegerConfigAttribute maxEntriesAttr =
              (IntegerConfigAttribute)
              configEntry.getConfigAttribute(maxEntriesStub);
      if (maxEntriesAttr == null) {
        // This is fine -- we'll just use the default.
      } else {
        maxEntries = maxEntriesAttr.activeValue();
      }
    } catch (Exception e) {
      if (debugEnabled()) {
        debugCaught(DebugLogLevel.ERROR, e);
      }
      
      // Log an error message.
      logError(ErrorLogCategory.CONFIGURATION, ErrorLogSeverity.SEVERE_ERROR,
              MSGID_FIFOCACHE_CANNOT_DETERMINE_MAX_ENTRIES,
              String.valueOf(configEntryDN), stackTraceToSingleLineString(e));
    }

    // Determine the lock timeout to use when interacting with the lock manager.
    lockTimeout = DEFAULT_FIFOCACHE_LOCK_TIMEOUT;
    msgID = MSGID_FIFOCACHE_DESCRIPTION_LOCK_TIMEOUT;
    IntegerWithUnitConfigAttribute lockTimeoutStub =
         new IntegerWithUnitConfigAttribute(ATTR_FIFOCACHE_LOCK_TIMEOUT,
                                            getMessage(msgID), false, timeUnits,
                                            true, 0, false, 0);
    try
    {
      IntegerWithUnitConfigAttribute lockTimeoutAttr =
             (IntegerWithUnitConfigAttribute)
             configEntry.getConfigAttribute(lockTimeoutStub);
      if (lockTimeoutAttr == null)
      {
        // This is fine -- we'll just use the default.
      }
      else
      {
        lockTimeout = lockTimeoutAttr.activeCalculatedValue();
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }

      // Log an error message.
      logError(ErrorLogCategory.CONFIGURATION, ErrorLogSeverity.SEVERE_ERROR,
               MSGID_FIFOCACHE_CANNOT_DETERMINE_LOCK_TIMEOUT,
               String.valueOf(configEntryDN), stackTraceToSingleLineString(e),
               lockTimeout);
    }
    
    // Determine the set of cache filters that can be used to control the
    // entries that should be included in the cache.
    includeFilters = new HashSet<SearchFilter>();
    msgID = MSGID_FIFOCACHE_DESCRIPTION_INCLUDE_FILTERS;
    StringConfigAttribute includeStub =
            new StringConfigAttribute(ATTR_FIFOCACHE_INCLUDE_FILTER,
            getMessage(msgID), false, true, false);
    try {
      StringConfigAttribute includeAttr =
              (StringConfigAttribute) configEntry.getConfigAttribute(includeStub);
      if (includeAttr == null) {
        // This is fine -- we'll just use the default.
      } else {
        List<String> filterStrings = includeAttr.activeValues();
        if ((filterStrings == null) || filterStrings.isEmpty()) {
          // There are no include filters, so we'll allow anything by default.
        } else {
          for (String filterString : filterStrings) {
            try {
              includeFilters.add(
                      SearchFilter.createFilterFromString(filterString));
            } catch (Exception e) {
              if (debugEnabled()) {
                debugCaught(DebugLogLevel.ERROR, e);
              }
              
              // We couldn't decode this filter.  Log a warning and continue.
              logError(ErrorLogCategory.CONFIGURATION,
                      ErrorLogSeverity.SEVERE_WARNING,
                      MSGID_FIFOCACHE_CANNOT_DECODE_INCLUDE_FILTER,
                      String.valueOf(configEntryDN), filterString,
                      stackTraceToSingleLineString(e));
            }
          }
          
          if (includeFilters.isEmpty()) {
            logError(ErrorLogCategory.CONFIGURATION,
                    ErrorLogSeverity.SEVERE_ERROR,
                    MSGID_FIFOCACHE_CANNOT_DECODE_ANY_INCLUDE_FILTERS,
                    String.valueOf(configEntryDN));
          }
        }
      }
    } catch (Exception e) {
      if (debugEnabled()) {
        debugCaught(DebugLogLevel.ERROR, e);
      }
      
      // Log an error message.
      logError(ErrorLogCategory.CONFIGURATION, ErrorLogSeverity.SEVERE_ERROR,
              MSGID_FIFOCACHE_CANNOT_DETERMINE_INCLUDE_FILTERS,
              String.valueOf(configEntryDN), stackTraceToSingleLineString(e));
    }
    
    
    // Determine the set of cache filters that can be used to control the
    // entries that should be excluded from the cache.
    excludeFilters = new HashSet<SearchFilter>();
    msgID = MSGID_FIFOCACHE_DESCRIPTION_EXCLUDE_FILTERS;
    StringConfigAttribute excludeStub =
            new StringConfigAttribute(ATTR_FIFOCACHE_EXCLUDE_FILTER,
            getMessage(msgID), false, true, false);
    try {
      StringConfigAttribute excludeAttr =
              (StringConfigAttribute) configEntry.getConfigAttribute(excludeStub);
      if (excludeAttr == null) {
        // This is fine -- we'll just use the default.
      } else {
        List<String> filterStrings = excludeAttr.activeValues();
        if ((filterStrings == null) || filterStrings.isEmpty()) {
          // There are no exclude filters, so we'll allow anything by default.
        } else {
          for (String filterString : filterStrings) {
            try {
              excludeFilters.add(
                      SearchFilter.createFilterFromString(filterString));
            } catch (Exception e) {
              if (debugEnabled()) {
                debugCaught(DebugLogLevel.ERROR, e);
              }
              
              // We couldn't decode this filter.  Log a warning and continue.
              logError(ErrorLogCategory.CONFIGURATION,
                      ErrorLogSeverity.SEVERE_WARNING,
                      MSGID_FIFOCACHE_CANNOT_DECODE_EXCLUDE_FILTER,
                      String.valueOf(configEntryDN), filterString,
                      stackTraceToSingleLineString(e));
            }
          }
          
          if (excludeFilters.isEmpty()) {
            logError(ErrorLogCategory.CONFIGURATION,
                    ErrorLogSeverity.SEVERE_ERROR,
                    MSGID_FIFOCACHE_CANNOT_DECODE_ANY_EXCLUDE_FILTERS,
                    String.valueOf(configEntryDN));
          }
        }
      }
    } catch (Exception e) {
      if (debugEnabled()) {
        debugCaught(DebugLogLevel.ERROR, e);
      }
      
      // Log an error message.
      logError(ErrorLogCategory.CONFIGURATION, ErrorLogSeverity.SEVERE_ERROR,
              MSGID_FIFOCACHE_CANNOT_DETERMINE_EXCLUDE_FILTERS,
              String.valueOf(configEntryDN), stackTraceToSingleLineString(e));
    }
  }
  
  
  
  /**
   * Performs any necessary cleanup work (e.g., flushing all cached entries and
   * releasing any other held resources) that should be performed when the
   * server is to be shut down or the entry cache destroyed or replaced.
   */
  public void finalizeEntryCache() {
    // Release all memory currently in use by this cache.
    
    cacheWriteLock.lock();
    
    // Check if the main Directory database environment is closed.
    // If not do not persist this cache, just clean up everything.
    
    FileSystemEntryCacheIndex entryCacheIndex = new FileSystemEntryCacheIndex();
    // There must be at least one backend at this stage.
    entryCacheIndex.offlineState = 
        DirectoryServer.getOfflineBackendsStateIDs();
    
    // Convert entry cache maps to serializable maps for cache index. 
    Set backendSet = backendMap.keySet();
    Iterator backendIterator = backendSet.iterator();
    while (backendIterator.hasNext()) {
      Backend backend = (Backend) backendIterator.next();
      LinkedHashMap<Long,DN> entriesMap = backendMap.get(backend);
      Set entriesSet = entriesMap.keySet();
      Iterator entriesIterator = entriesSet.iterator();
      LinkedHashMap<Long,String> entryMap = new LinkedHashMap<Long,String>();
      while (entriesIterator.hasNext()) {
        Long entryID = (Long) entriesIterator.next();
        DN entryDN = entriesMap.get(entryID);
        entryCacheIndex.dnMap.put(entryDN.toString(), entryID);
        entryMap.put(entryID, entryDN.toString());
      }
      entryCacheIndex.backendMap.put(backend.getBackendID(), entryMap);
    }
        
    OperationStatus jdbStatus = null;
    // Store index.
    try {
      DatabaseEntry indexData = new DatabaseEntry();
      entryCacheDataBinding.objectToEntry(entryCacheIndex, indexData);
      DatabaseEntry indexKey = new DatabaseEntry(INDEXKEY.getBytes());
      jdbStatus = entryCacheDB.put(null, indexKey, indexData);
    } catch (Exception e) {
      e.printStackTrace();
      if (debugEnabled()) {
        debugCaught(DebugLogLevel.ERROR, e);
      }   
      // Log an error message.
      logError(ErrorLogCategory.EXTENSIONS, ErrorLogSeverity.SEVERE_ERROR,
              /* TODO: */ 0, "Failed to store FS entry cache index",
              stackTraceToSingleLineString(e));
    }
    
    System.out.printf("<<<DEBUG>>> finalizeEntryCache: index put status: %s\n", 
        jdbStatus.toString());
    
    // Close JE database and environment.
    // TODO: check for persistent cache cfg option, 
    // close & remove if not set, simple close otherwise.
    try {
      backendMap.clear();
      dnMap.clear();

      if (entryCacheDB != null) {
        entryCacheDB.close();
      }
      if (entryCacheEnv != null) {
        entryCacheEnv.cleanLog();
        entryCacheEnv.close();
      }
    } catch (Exception e) {
      if (debugEnabled()) {
        debugCaught(DebugLogLevel.ERROR, e);
      }
      
      // Log an error message.
      logError(ErrorLogCategory.EXTENSIONS, ErrorLogSeverity.SEVERE_ERROR,
              /* TODO: */ 0, "Failed to close FS entry cache",
              stackTraceToSingleLineString(e));
    } finally {
      cacheWriteLock.unlock();
    }
  }
  
  
  
  /**
   * Indicates whether the entry cache currently contains the entry with the
   * specified DN.  This method may be called without holding any locks if a
   * point-in-time check is all that is required.
   *
   * @param  entryDN  The DN for which to make the determination.
   *
   * @return  <CODE>true</CODE> if the entry cache currently contains the entry
   *          with the specified DN, or <CODE>false</CODE> if not.
   */
  public boolean containsEntry(DN entryDN) 
  {
    // Indicate whether the DN map contains the specified DN.
    boolean containsEntry = false;
    cacheReadLock.lock();
    try {
      containsEntry = dnMap.containsKey(entryDN);
    } finally {
      cacheReadLock.unlock();
    }
    return containsEntry;
  }
  
  
  
  /**
   * Retrieves the entry with the specified DN from the cache.  The caller
   * should have already acquired a read or write lock for the entry if such
   * protection is needed.
   *
   * @param  entryDN  The DN of the entry to retrieve.
   *
   * @return  The requested entry if it is present in the cache, or
   *          <CODE>null</CODE> if it is not present.
   */
  public Entry getEntry(DN entryDN) {
    // Get the entry from the DN map if it is present.  If not, then return
    // null.
    Entry entry = null;
    cacheReadLock.lock();
    try {
      if (dnMap.containsKey(entryDN)) {
        //System.out.printf("<<<DEBUG>>> getEntry: cache hit for: %s\n", entryDN);
        entry = getEntryFromDB(entryDN);
      }
    } finally {
      cacheReadLock.unlock();
    }
    return entry;
  }
  
  
  
  /**
   * Retrieves the entry ID for the entry with the specified DN from the cache.
   * The caller should have already acquired a read or write lock for the entry
   * if such protection is needed.
   *
   * @param  entryDN  The DN of the entry for which to retrieve the entry ID.
   *
   * @return  The entry ID for the requested entry, or -1 if it is not present
   *          in the cache.
   */
  public long getEntryID(DN entryDN) {
    long entryID = -1;
    cacheReadLock.lock();
    try {
      entryID = dnMap.get(entryDN);
    } finally {
      cacheReadLock.unlock();
    }
    return entryID;
  }
  
  
  
  /**
   * Retrieves the entry with the specified DN from the cache, obtaining a lock
   * on the entry before it is returned.  If the entry is present in the cache,
   * then a lock will be obtained for that entry and appended to the provided
   * list before the entry is returned.  If the entry is not present, then no
   * lock will be obtained.
   *
   * @param  entryDN   The DN of the entry to retrieve.
   * @param  lockType  The type of lock to obtain (it may be <CODE>NONE</CODE>).
   * @param  lockList  The list to which the obtained lock will be added (note
   *                   that no lock will be added if the lock type was
   *                   <CODE>NONE</CODE>).
   *
   * @return  The requested entry if it is present in the cache, or
   *          <CODE>null</CODE> if it is not present.
   */
  public Entry getEntry(DN entryDN, LockType lockType, List<Lock> lockList) {
    
    Entry entry = getEntry(entryDN);
    if (entry == null)
    {
      return null;
    }
    
    // Obtain a lock for the entry as appropriate.  If an error occurs, then
    // make sure no lock is held and return null.  Otherwise, return the entry.
    switch (lockType)
    {
      case READ:
        // Try to obtain a read lock for the entry.
        Lock readLock = LockManager.lockRead(entryDN, lockTimeout);
        if (readLock == null)
        {
          // We couldn't get the lock, so we have to return null.
          return null;
        }
        else
        {
          try
          {
            lockList.add(readLock);
            return entry;
          }
          catch (Exception e)
          {
            if (debugEnabled())
            {
              debugCaught(DebugLogLevel.ERROR, e);
            }

            // The attempt to add the lock to the list failed, so we need to
            // release the lock and return null.
            try
            {
              LockManager.unlock(entryDN, readLock);
            }
            catch (Exception e2)
            {
              if (debugEnabled())
              {
                debugCaught(DebugLogLevel.ERROR, e2);
              }
            }

            return null;
          }
        }

      case WRITE:
        // Try to obtain a write lock for the entry.
        Lock writeLock = LockManager.lockWrite(entryDN, lockTimeout);
        if (writeLock == null)
        {
          // We couldn't get the lock, so we have to return null.
          return null;
        }
        else
        {
          try
          {
            lockList.add(writeLock);
            return entry;
          }
          catch (Exception e)
          {
            if (debugEnabled())
            {
              debugCaught(DebugLogLevel.ERROR, e);
            }

            // The attempt to add the lock to the list failed, so we need to
            // release the lock and return null.
            try
            {
              LockManager.unlock(entryDN, writeLock);
            }
            catch (Exception e2)
            {
              if (debugEnabled())
              {
                debugCaught(DebugLogLevel.ERROR, e2);
              }
            }

            return null;
          }
        }

      case NONE:
        // We don't need to obtain a lock, so just return the entry.
        return entry;

      default:
        // This is an unknown type of lock, so we'll return null.
        return null;
    }
  }
  
  /**
   * Retrieves the requested entry if it is present in the cache.
   *
   * @param  backend   The backend associated with the entry to retrieve.
   * @param  entryID   The entry ID within the provided backend for the
   *                   specified entry.
   *
   * @return  The requested entry if it is present in the cache, or
   *          <CODE>null</CODE> if it is not present.
   */
  public Entry getEntry(Backend backend, long entryID) {
    
    Entry entry = null;
    cacheReadLock.lock();
    try {
      // Get the hash map for the provided backend.  If it isn't present, then
      // return null.
      LinkedHashMap map = backendMap.get(backend);
      if ( !(map == null) ) {
        // Get the entry from the map by its ID.  If it isn't present, then 
        // return null.
        DN dn = (DN) map.get(entryID);
        if ( !(dn == null) ) {
          if (dnMap.containsKey(dn)) {
            //System.out.printf("<<<DEBUG>>> getEntry: cache hit for: %d\n", entryID);
            entry = getEntryFromDB(dn);
          }
        }
      }
    } finally {
      cacheReadLock.unlock();
    }
    return entry;
  }

  /**
   * Retrieves the requested entry if it is present in the cache, obtaining a
   * lock on the entry before it is returned.  If the entry is present in the
   * cache, then a lock  will be obtained for that entry and appended to the
   * provided list before the entry is returned.  If the entry is not present,
   * then no lock will be obtained.
   *
   * @param  backend   The backend associated with the entry to retrieve.
   * @param  entryID   The entry ID within the provided backend for the
   *                   specified entry.
   * @param  lockType  The type of lock to obtain (it may be <CODE>NONE</CODE>).
   * @param  lockList  The list to which the obtained lock will be added (note
   *                   that no lock will be added if the lock type was
   *                   <CODE>NONE</CODE>).
   *
   * @return  The requested entry if it is present in the cache, or
   *          <CODE>null</CODE> if it is not present.
   */
  public Entry getEntry(Backend backend, long entryID, LockType lockType,
          List<Lock> lockList) {
    
    Entry entry = getEntry(backend, entryID);
    if (entry == null)
    {
      return null;
    }
    
    // Obtain a lock for the entry as appropriate.  If an error occurs, then
    // make sure no lock is held and return null.  Otherwise, return the entry.
    switch (lockType)
    {
      case READ:
        // Try to obtain a read lock for the entry.
        Lock readLock = LockManager.lockRead(entry.getDN(), lockTimeout);
        if (readLock == null)
        {
          // We couldn't get the lock, so we have to return null.
          return null;
        }
        else
        {
          try
          {
            lockList.add(readLock);
            return entry;
          }
          catch (Exception e)
          {
            if (debugEnabled())
            {
              debugCaught(DebugLogLevel.ERROR, e);
            }

            // The attempt to add the lock to the list failed, so we need to
            // release the lock and return null.
            try
            {
              LockManager.unlock(entry.getDN(), readLock);
            }
            catch (Exception e2)
            {
              if (debugEnabled())
              {
                debugCaught(DebugLogLevel.ERROR, e2);
              }
            }

            return null;
          }
        }

      case WRITE:
        // Try to obtain a write lock for the entry.
        Lock writeLock = LockManager.lockWrite(entry.getDN(), lockTimeout);
        if (writeLock == null)
        {
          // We couldn't get the lock, so we have to return null.
          return null;
        }
        else
        {
          try
          {
            lockList.add(writeLock);
            return entry;
          }
          catch (Exception e)
          {
            if (debugEnabled())
            {
              debugCaught(DebugLogLevel.ERROR, e);
            }

            // The attempt to add the lock to the list failed, so we need to
            // release the lock and return null.
            try
            {
              LockManager.unlock(entry.getDN(), writeLock);
            }
            catch (Exception e2)
            {
              if (debugEnabled())
              {
                debugCaught(DebugLogLevel.ERROR, e2);
              }
            }

            return null;
          }
        }

      case NONE:
        // We don't need to obtain a lock, so just return the entry.
        return entry;

      default:
        // This is an unknown type of lock, so we'll return null.
        return null;
    }
  }
  
  
  
  /**
   * Stores the provided entry in the cache.  Note that the mechanism that it
   * uses to achieve this is implementation-dependent, and it is acceptable for
   * the entry to not actually be stored in any cache.
   *
   * @param  entry    The entry to store in the cache.
   * @param  backend  The backend with which the entry is associated.
   * @param  entryID  The entry ID within the provided backend that uniquely
   *                  identifies the specified entry.
   */
  public void putEntry(Entry entry, Backend backend, long entryID) {
    
    // If there is a set of exclude filters, then make sure that the provided
    // entry doesn't match any of them.
    if (! excludeFilters.isEmpty()) {
      for (SearchFilter f : excludeFilters) {
        try {
          if (f.matchesEntry(entry)) {
            return;
          }
        } catch (Exception e) {
          if (debugEnabled()) {
            debugCaught(DebugLogLevel.ERROR, e);
          }
          
          // This shouldn't happen, but if it does then we can't be sure whether
          // the entry should be excluded, so we will by default.
          return;
        }
      }
    }
      
    // If there is a set of include filters, then make sure that the provided
    // entry matches at least one of them.
    if (! includeFilters.isEmpty()) {
      boolean matchFound = false;
      for (SearchFilter f : includeFilters) {
        try {
          if (f.matchesEntry(entry)) {
            matchFound = true;
            break;
          }
        } catch (Exception e) {
          if (debugEnabled()) {
            debugCaught(DebugLogLevel.ERROR, e);
          }
          
          // This shouldn't happen, but if it does, then just ignore it.
        }
      }
      
      if (! matchFound) {
        return;
      }
    }
    
    // Obtain a lock on the cache.  If this fails, then don't do anything.
    try {
      if (! cacheWriteLock.tryLock(lockTimeout, TimeUnit.MILLISECONDS)) {
        // We can't rule out the possibility of a conflict, so return false.
        return;
      }
      putEntryToDB(entry, backend, entryID);
    } catch (Exception e) {
      if (debugEnabled()) {
        debugCaught(DebugLogLevel.ERROR, e);
      }
      
      // We can't rule out the possibility of a conflict, so return false.
      return;
    } finally {
      cacheWriteLock.unlock();
    }
  }
  
  
  
  /**
   * Stores the provided entry in the cache only if it does not conflict with an
   * entry that already exists.  Note that the mechanism that it uses to achieve
   * this is implementation-dependent, and it is acceptable for the entry to not
   * actually be stored in any cache.  However, this method must not overwrite
   * an existing version of the entry.
   *
   * @param  entry    The entry to store in the cache.
   * @param  backend  The backend with which the entry is associated.
   * @param  entryID  The entry ID within the provided backend that uniquely
   *                  identifies the specified entry.
   *
   * @return  <CODE>false</CODE> if an existing entry or some other problem
   *          prevented the method from completing successfully, or
   *          <CODE>true</CODE> if there was no conflict and the entry was
   *          either stored or the cache determined that this entry should never
   *          be cached for some reason.
   */
  public boolean putEntryIfAbsent(Entry entry, Backend backend, long entryID) 
  {
    // If there is a set of exclude filters, then make sure that the provided
    // entry doesn't match any of them.
    if (! excludeFilters.isEmpty()) {
      for (SearchFilter f : excludeFilters) {
        try {
          if (f.matchesEntry(entry)) {
            return true;
          }
        } catch (Exception e) {
          if (debugEnabled()) {
            debugCaught(DebugLogLevel.ERROR, e);
          }
          
          // This shouldn't happen, but if it does then we can't be sure whether
          // the entry should be excluded, so we will by default.
          return false;
        }
      }
    }
    
    // If there is a set of include filters, then make sure that the provided
    // entry matches at least one of them.
    if (! includeFilters.isEmpty()) {
      boolean matchFound = false;
      for (SearchFilter f : includeFilters) {
        try {
          if (f.matchesEntry(entry)) {
            matchFound = true;
            break;
          }
        } catch (Exception e) {
          if (debugEnabled()) {
            debugCaught(DebugLogLevel.ERROR, e);
          }
          
          // This shouldn't happen, but if it does, then just ignore it.
        }
      }
      
      if (! matchFound) {
        return true;
      }
    }

    try {
      // Obtain a lock on the cache.  If this fails, then don't do anything.
      if (! cacheWriteLock.tryLock(lockTimeout, TimeUnit.MILLISECONDS)) {
        // We can't rule out the possibility of a conflict, so return false.
        return false;
      }
      // See if the entry already exists in the cache.  If it does, then we will
      // fail and not actually store the entry.
      if (dnMap.containsKey(entry.getDN())) {
        return false;
      }
      return putEntryToDB(entry, backend, entryID);
    } catch (Exception e) {
      if (debugEnabled()) {
        debugCaught(DebugLogLevel.ERROR, e);
      }  
      // We can't rule out the possibility of a conflict, so return false.
      return false;
    } finally {
      cacheWriteLock.unlock();
    }
  }
  
  
  
  /**
   * Removes the specified entry from the cache.
   *
   * @param  entryDN  The DN of the entry to remove from the cache.
   */
  public void removeEntry(DN entryDN) {
    cacheWriteLock.lock();
    
    try {
      long entryID = dnMap.get(entryDN);
      Set backendSet = backendMap.keySet();
      
      Iterator backendIterator = backendSet.iterator();
      while (backendIterator.hasNext()) {
        LinkedHashMap map = backendMap.get(backendIterator.next());
        map.remove(entryID);
      }
      
      dnMap.remove(entryDN);
      entryCacheDB.delete(null, 
        new DatabaseEntry(entryDN.toString().getBytes()));
    } catch (Exception e) {
      e.printStackTrace();
      if (debugEnabled()) {
        debugCaught(DebugLogLevel.ERROR, e);
      }
    } finally {
      cacheWriteLock.unlock();
    }

  }
  
  
  
  /**
   * Removes all entries from the cache.  The cache should still be available
   * for future use.
   */
  public void clear() {
    cacheWriteLock.lock();
    
    dnMap.clear();
    backendMap.clear();

    try {
      if ((entryCacheDB != null) && (entryCacheEnv != null) && 
          (entryCacheDBConfig != null)) {
        entryCacheDB.close();
        entryCacheEnv.truncateDatabase(null, ENTRYCACHEDBNAME, false);
        entryCacheEnv.cleanLog();
        entryCacheDB = entryCacheEnv.openDatabase(null,
              ENTRYCACHEDBNAME, entryCacheDBConfig);
      }
    } catch (Exception e) {
      if (debugEnabled()) {
        debugCaught(DebugLogLevel.ERROR, e);
      }
      
      // Log an error message.
      logError(ErrorLogCategory.EXTENSIONS, ErrorLogSeverity.SEVERE_ERROR,
              /* TODO: */ 0, "Failed to clear FS entry cache",
              stackTraceToSingleLineString(e));
    } finally {
      cacheWriteLock.unlock();
    }
  }
  
  
  
  /**
   * Removes all entries from the cache that are associated with the provided
   * backend.
   *
   * @param  backend  The backend for which to flush the associated entries.
   */
  public void clearBackend(Backend backend) {
    // Might take awhile to complete on a relatively large cache.
    
    cacheWriteLock.lock();
    
    LinkedHashMap backendEntriesMap = backendMap.get(backend);
    
    try {
      Set entriesSet = backendEntriesMap.keySet();
      
      Iterator backendEntriesIterator = entriesSet.iterator();
      while (backendEntriesIterator.hasNext()) {
        long entryID = (Long) backendEntriesIterator.next();
        DN entryDN = (DN) backendEntriesMap.get(entryID);
        entryCacheDB.delete(null,
            new DatabaseEntry(entryDN.toString().getBytes()));
        dnMap.remove(entryDN);
      }
      
    } catch (Exception e) {
      if (debugEnabled()) {
        debugCaught(DebugLogLevel.ERROR, e);
      }
      
      // Log an error message.
      logError(ErrorLogCategory.EXTENSIONS, ErrorLogSeverity.SEVERE_ERROR,
          /* TODO: */ 0, "FS entry cache: failed to clear backend",
          stackTraceToSingleLineString(e));
    } finally {
      backendMap.remove(backend);
      cacheWriteLock.unlock();
    }
  }
  
  
  
  /**
   * Removes all entries from the cache that are below the provided DN.
   *
   * @param  baseDN  The base DN below which all entries should be flushed.
   */
  public void clearSubtree(DN baseDN) {
    // Determine which backend should be used for the provided base DN.  If
    // there is none, then we don't need to do anything.
    Backend backend = DirectoryServer.getBackend(baseDN);
    if (backend == null)
    {
      return;
    }
    
    // Acquire a lock on the cache.  We should not return until the cache has
    // been cleared, so we will block until we can obtain the lock.
    cacheWriteLock.lock();

    // At this point, it is absolutely critical that we always release the lock
    // before leaving this method, so do so in a finally block.
    try
    {
      clearSubtree(baseDN, backend);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }

      // This shouldn't happen, but there's not much that we can do if it does.
    }
    finally
    {
      cacheWriteLock.unlock();
    }
  }
  
  
  
  /**
   * Clears all entries at or below the specified base DN that are associated
   * with the given backend.  The caller must already hold the cache lock.
   *
   * @param  baseDN   The base DN below which all entries should be flushed.
   * @param  backend  The backend for which to remove the appropriate entries.
   */
  private void clearSubtree(DN baseDN, Backend backend) {
    // See if there are any entries for the provided backend in the cache.  If
    // not, then return.
    LinkedHashMap<Long,DN> map = backendMap.get(backend);
    if (map == null)
    {
      // No entries were in the cache for this backend, so we can return without
      // doing anything.
      return;
    }

    // Since the provided base DN could hold a subset of the information in the
    // specified backend, we will have to do this by iterating through all the
    // entries for that backend.  Since this could take a while, we'll
    // periodically release and re-acquire the lock in case anyone else is
    // waiting on it so this doesn't become a stop-the-world event as far as the
    // cache is concerned.
    int entriesExamined = 0;
    Iterator<DN> iterator = map.values().iterator();
    while (iterator.hasNext())
    {
      DN entryDN = iterator.next();
      if (entryDN.isDescendantOf(baseDN))
      {
        iterator.remove();
        dnMap.remove(entryDN);
      }

      entriesExamined++;
      if ((entriesExamined % 1000) == 0)
      {
        cacheWriteLock.unlock();
        Thread.currentThread().yield();
        cacheWriteLock.lock();
      }
    }

    // See if the backend has any subordinate backends.  If so, then process
    // them recursively.
    for (Backend subBackend : backend.getSubordinateBackends())
    {
      boolean isAppropriate = false;
      for (DN subBase : subBackend.getBaseDNs())
      {
        if (subBase.isDescendantOf(baseDN))
        {
          isAppropriate = true;
          break;
        }
      }

      if (isAppropriate)
      {
        clearSubtree(baseDN, subBackend);
      }
    }
  }
  
  
  
  /**
   * Attempts to react to a scenario in which it is determined that the system
   * is running low on available memory.  In this case, the entry cache should
   * attempt to free some memory if possible to try to avoid out of memory
   * errors.
   */
  public void handleLowMemory() {
    // If this cache db is tmpfs based and tmpfs is not explicitly limited 
    // [ which it isnt by default ] truncate all to prevent swapping that
    // is already taking place at this point. removing oldest or lru items
    // one by one or in chunks will not have desired effect. it is better
    // to loose this cache than be in constantly swapping state, perf wise.
    // TODO: revisit this and maybe introduce cfg parameter for non tmpfs
    // based storage or tmpfs that is explicitly limited by administrator.
    clear();
  }
  
  
  
  /**
   * Retrieves the DN of the configuration entry with which this component is
   * associated.
   *
   * @return  The DN of the configuration entry with which this component is
   *          associated.
   */
  public DN getConfigurableComponentEntryDN() {
    return configEntryDN;
  }
  
  
  
  /**
   * Retrieves the set of configuration attributes that are associated with this
   * configurable component.
   *
   * @return  The set of configuration attributes that are associated with this
   *          configurable component.
   */
  public List<ConfigAttribute> getConfigurationAttributes() {
    LinkedList<ConfigAttribute> attrList = new LinkedList<ConfigAttribute>();
    
    
    int msgID = MSGID_FIFOCACHE_DESCRIPTION_MAX_MEMORY_PCT;
    IntegerConfigAttribute maxMemoryPctAttr =
            new IntegerConfigAttribute(ATTR_FIFOCACHE_MAX_MEMORY_PCT,
            getMessage(msgID), true, false, false, true,
            1, true, 100, maxMemoryPercent);
    attrList.add(maxMemoryPctAttr);
    
    
    msgID = MSGID_FIFOCACHE_DESCRIPTION_MAX_ENTRIES;
    IntegerConfigAttribute maxEntriesAttr =
            new IntegerConfigAttribute(ATTR_FIFOCACHE_MAX_ENTRIES,
            getMessage(msgID), true, false, false,
            true, 0, false, 0, maxEntries);
    attrList.add(maxEntriesAttr);

    msgID = MSGID_FIFOCACHE_DESCRIPTION_LOCK_TIMEOUT;
    IntegerWithUnitConfigAttribute lockTimeoutAttr =
         new IntegerWithUnitConfigAttribute(ATTR_FIFOCACHE_LOCK_TIMEOUT,
                                            getMessage(msgID), false, timeUnits,
                                            true, 0, false, 0, lockTimeout,
                                            TIME_UNIT_MILLISECONDS_FULL);
    attrList.add(lockTimeoutAttr);
    
    msgID = MSGID_FIFOCACHE_DESCRIPTION_INCLUDE_FILTERS;
    ArrayList<String> includeStrings =
            new ArrayList<String>(includeFilters.size());
    for (SearchFilter f : includeFilters) {
      includeStrings.add(f.toString());
    }
    StringConfigAttribute includeAttr =
            new StringConfigAttribute(ATTR_FIFOCACHE_INCLUDE_FILTER,
            getMessage(msgID), false, true, false,
            includeStrings);
    attrList.add(includeAttr);
    
    
    msgID = MSGID_FIFOCACHE_DESCRIPTION_EXCLUDE_FILTERS;
    ArrayList<String> excludeStrings =
            new ArrayList<String>(excludeFilters.size());
    for (SearchFilter f : excludeFilters) {
      excludeStrings.add(f.toString());
    }
    StringConfigAttribute excludeAttr =
            new StringConfigAttribute(ATTR_FIFOCACHE_EXCLUDE_FILTER,
            getMessage(msgID), false, true, false,
            excludeStrings);
    attrList.add(excludeAttr);
    
    
    return attrList;
  }
  
  
  
  /**
   * Indicates whether the provided configuration entry has an acceptable
   * configuration for this component.  If it does not, then detailed
   * information about the problem(s) should be added to the provided list.
   *
   * @param  configEntry          The configuration entry for which to make the
   *                              determination.
   * @param  unacceptableReasons  A list that can be used to hold messages about
   *                              why the provided entry does not have an
   *                              acceptable configuration.
   *
   * @return  <CODE>true</CODE> if the provided entry has an acceptable
   *          configuration for this component, or <CODE>false</CODE> if not.
   */
  public boolean hasAcceptableConfiguration(ConfigEntry configEntry,
          List<String> unacceptableReasons) {
    // Start out assuming that the configuration is valid.
    boolean configIsAcceptable = true;
    
    
    // Determine the maximum memory usage as a percentage of the total JVM
    // memory.
    int msgID = MSGID_FIFOCACHE_DESCRIPTION_MAX_MEMORY_PCT;
    IntegerConfigAttribute maxMemoryPctStub =
            new IntegerConfigAttribute(ATTR_FIFOCACHE_MAX_MEMORY_PCT,
            getMessage(msgID), true, false, false, true,
            1, true, 100);
    try {
      IntegerConfigAttribute maxMemoryPctAttr =
              (IntegerConfigAttribute)
              configEntry.getConfigAttribute(maxMemoryPctStub);
    } catch (Exception e) {
      if (debugEnabled()) {
        debugCaught(DebugLogLevel.ERROR, e);
      }
      
      // An error occurred, so the provided value must not be valid.
      msgID = MSGID_FIFOCACHE_INVALID_MAX_MEMORY_PCT;
      unacceptableReasons.add(getMessage(msgID, String.valueOf(configEntryDN),
              stackTraceToSingleLineString(e)));
      configIsAcceptable = false;
    }
    
    
    // Determine the maximum number of entries that we will allow in the cache.
    msgID = MSGID_FIFOCACHE_DESCRIPTION_MAX_ENTRIES;
    IntegerConfigAttribute maxEntriesStub =
            new IntegerConfigAttribute(ATTR_FIFOCACHE_MAX_ENTRIES,
            getMessage(msgID), true, false, false,
            true, 0, false, 0);
    try {
      IntegerConfigAttribute maxEntriesAttr =
              (IntegerConfigAttribute)
              configEntry.getConfigAttribute(maxEntriesStub);
    } catch (Exception e) {
      if (debugEnabled()) {
        debugCaught(DebugLogLevel.ERROR, e);
      }
      
      // An error occurred, so the provided value must not be valid.
      msgID = MSGID_FIFOCACHE_INVALID_MAX_ENTRIES;
      unacceptableReasons.add(getMessage(msgID, String.valueOf(configEntryDN),
              stackTraceToSingleLineString(e)));
      configIsAcceptable = false;
    }

    // Determine the lock timeout to use when interacting with the lock manager.
    msgID = MSGID_FIFOCACHE_DESCRIPTION_LOCK_TIMEOUT;
    IntegerWithUnitConfigAttribute lockTimeoutStub =
         new IntegerWithUnitConfigAttribute(ATTR_FIFOCACHE_LOCK_TIMEOUT,
                                            getMessage(msgID), false, timeUnits,
                                            true, 0, false, 0);
    try
    {
      IntegerWithUnitConfigAttribute lockTimeoutAttr =
             (IntegerWithUnitConfigAttribute)
             configEntry.getConfigAttribute(lockTimeoutStub);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }

      // An error occurred, so the provided value must not be valid.
      msgID = MSGID_FIFOCACHE_INVALID_LOCK_TIMEOUT;
      unacceptableReasons.add(getMessage(msgID, String.valueOf(configEntryDN),
                                         stackTraceToSingleLineString(e)));
      configIsAcceptable = false;
    }

    // Determine the set of cache filters that can be used to control the
    // entries that should be included in the cache.
    msgID = MSGID_FIFOCACHE_DESCRIPTION_INCLUDE_FILTERS;
    StringConfigAttribute includeStub =
            new StringConfigAttribute(ATTR_FIFOCACHE_INCLUDE_FILTER,
            getMessage(msgID), false, true, false);
    try {
      StringConfigAttribute includeAttr =
              (StringConfigAttribute) configEntry.getConfigAttribute(includeStub);
      if (includeAttr == null) {
        // This is fine -- we'll just use the default.
      } else {
        List<String> filterStrings = includeAttr.activeValues();
        if ((filterStrings == null) || filterStrings.isEmpty()) {
          // There are no include filters, so we'll allow anything by default.
        } else {
          for (String filterString : filterStrings) {
            try {
              SearchFilter.createFilterFromString(filterString);
            } catch (Exception e) {
              if (debugEnabled()) {
                debugCaught(DebugLogLevel.ERROR, e);
              }
              
              // We couldn't decode this filter, so it isn't valid.
              msgID = MSGID_FIFOCACHE_INVALID_INCLUDE_FILTER;
              unacceptableReasons.add(getMessage(msgID,
                      String.valueOf(configEntryDN),
                      filterString,
                      stackTraceToSingleLineString(e)));
              configIsAcceptable = false;
            }
          }
        }
      }
    } catch (Exception e) {
      if (debugEnabled()) {
        debugCaught(DebugLogLevel.ERROR, e);
      }
      
      // An error occurred, so the provided value must not be valid.
      msgID = MSGID_FIFOCACHE_INVALID_INCLUDE_FILTERS;
      unacceptableReasons.add(getMessage(msgID, String.valueOf(configEntryDN),
              stackTraceToSingleLineString(e)));
      configIsAcceptable = false;
    }
    
    
    // Determine the set of cache filters that can be used to control the
    // entries that should be excluded from the cache.
    msgID = MSGID_FIFOCACHE_DESCRIPTION_EXCLUDE_FILTERS;
    StringConfigAttribute excludeStub =
            new StringConfigAttribute(ATTR_FIFOCACHE_EXCLUDE_FILTER,
            getMessage(msgID), false, true, false);
    try {
      StringConfigAttribute excludeAttr =
              (StringConfigAttribute) configEntry.getConfigAttribute(excludeStub);
      if (excludeAttr == null) {
        // This is fine -- we'll just use the default.
      } else {
        List<String> filterStrings = excludeAttr.activeValues();
        if ((filterStrings == null) || filterStrings.isEmpty()) {
          // There are no exclude filters, so we'll allow anything by default.
        } else {
          for (String filterString : filterStrings) {
            try {
              SearchFilter.createFilterFromString(filterString);
            } catch (Exception e) {
              if (debugEnabled()) {
                debugCaught(DebugLogLevel.ERROR, e);
              }
              
              // We couldn't decode this filter, so it isn't valid.
              msgID = MSGID_FIFOCACHE_INVALID_EXCLUDE_FILTER;
              unacceptableReasons.add(getMessage(msgID,
                      String.valueOf(configEntryDN),
                      filterString,
                      stackTraceToSingleLineString(e)));
              configIsAcceptable = false;
            }
          }
        }
      }
    } catch (Exception e) {
      if (debugEnabled()) {
        debugCaught(DebugLogLevel.ERROR, e);
      }
      
      // An error occurred, so the provided value must not be valid.
      msgID = MSGID_FIFOCACHE_INVALID_EXCLUDE_FILTERS;
      unacceptableReasons.add(getMessage(msgID, String.valueOf(configEntryDN),
              stackTraceToSingleLineString(e)));
      configIsAcceptable = false;
    }
    
    
    return configIsAcceptable;
  }
  
  
  
  /**
   * Makes a best-effort attempt to apply the configuration contained in the
   * provided entry.  Information about the result of this processing should be
   * added to the provided message list.  Information should always be added to
   * this list if a configuration change could not be applied.  If detailed
   * results are requested, then information about the changes applied
   * successfully (and optionally about parameters that were not changed) should
   * also be included.
   *
   * @param  configEntry      The entry containing the new configuration to
   *                          apply for this component.
   * @param  detailedResults  Indicates whether detailed information about the
   *                          processing should be added to the list.
   *
   * @return  Information about the result of the configuration update.
   */
  public ConfigChangeResult applyNewConfiguration(ConfigEntry configEntry,
          boolean detailedResults) {
    // Create a set of variables to use for the result.
    ResultCode        resultCode          = ResultCode.SUCCESS;
    boolean           adminActionRequired = false;
    ArrayList<String> messages            = new ArrayList<String>();
    boolean           configIsAcceptable  = true;
    
    
    // Determine the maximum memory usage as a percentage of the total JVM
    // memory.
    int newMaxMemoryPercent = DEFAULT_FIFOCACHE_MAX_MEMORY_PCT;
    int msgID = MSGID_FIFOCACHE_DESCRIPTION_MAX_MEMORY_PCT;
    IntegerConfigAttribute maxMemoryPctStub =
            new IntegerConfigAttribute(ATTR_FIFOCACHE_MAX_MEMORY_PCT,
            getMessage(msgID), true, false, false, true,
            1, true, 100);
    try {
      IntegerConfigAttribute maxMemoryPctAttr =
              (IntegerConfigAttribute)
              configEntry.getConfigAttribute(maxMemoryPctStub);
      if (maxMemoryPctAttr != null) {
        newMaxMemoryPercent = maxMemoryPctAttr.pendingIntValue();
      }
    } catch (Exception e) {
      if (debugEnabled()) {
        debugCaught(DebugLogLevel.ERROR, e);
      }
      
      // An error occurred, so the provided value must not be valid.
      msgID = MSGID_FIFOCACHE_INVALID_MAX_MEMORY_PCT;
      messages.add(getMessage(msgID, String.valueOf(configEntryDN),
              stackTraceToSingleLineString(e)));
      resultCode = ResultCode.CONSTRAINT_VIOLATION;
      configIsAcceptable = false;
    }
    
    
    // Determine the maximum number of entries that we will allow in the cache.
    long newMaxEntries = DEFAULT_FIFOCACHE_MAX_ENTRIES;
    msgID = MSGID_FIFOCACHE_DESCRIPTION_MAX_ENTRIES;
    IntegerConfigAttribute maxEntriesStub =
            new IntegerConfigAttribute(ATTR_FIFOCACHE_MAX_ENTRIES,
            getMessage(msgID), true, false, false,
            true, 0, false, 0);
    try {
      IntegerConfigAttribute maxEntriesAttr =
              (IntegerConfigAttribute)
              configEntry.getConfigAttribute(maxEntriesStub);
      if (maxEntriesAttr != null) {
        newMaxEntries = maxEntriesAttr.pendingValue();
      }
    } catch (Exception e) {
      if (debugEnabled()) {
        debugCaught(DebugLogLevel.ERROR, e);
      }
      
      // An error occurred, so the provided value must not be valid.
      msgID = MSGID_FIFOCACHE_INVALID_MAX_ENTRIES;
      messages.add(getMessage(msgID, String.valueOf(configEntryDN),
              stackTraceToSingleLineString(e)));
      
      if (resultCode == ResultCode.SUCCESS) {
        resultCode = ResultCode.CONSTRAINT_VIOLATION;
      }
      
      configIsAcceptable = false;
    }

    // Determine the lock timeout to use when interacting with the lock manager.
    long newLockTimeout = DEFAULT_FIFOCACHE_LOCK_TIMEOUT;
    msgID = MSGID_FIFOCACHE_DESCRIPTION_LOCK_TIMEOUT;
    IntegerWithUnitConfigAttribute lockTimeoutStub =
         new IntegerWithUnitConfigAttribute(ATTR_FIFOCACHE_LOCK_TIMEOUT,
                                            getMessage(msgID), false, timeUnits,
                                            true, 0, false, 0);
    try
    {
      IntegerWithUnitConfigAttribute lockTimeoutAttr =
             (IntegerWithUnitConfigAttribute)
             configEntry.getConfigAttribute(lockTimeoutStub);
      if (lockTimeoutAttr != null)
      {
        newLockTimeout = lockTimeoutAttr.pendingCalculatedValue();
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }

      // An error occurred, so the provided value must not be valid.
      msgID = MSGID_FIFOCACHE_INVALID_LOCK_TIMEOUT;
      messages.add(getMessage(msgID, String.valueOf(configEntryDN),
                              stackTraceToSingleLineString(e)));

      if (resultCode == ResultCode.SUCCESS)
      {
        resultCode = ResultCode.CONSTRAINT_VIOLATION;
      }

      configIsAcceptable = false;
    }

    // Determine the set of cache filters that can be used to control the
    // entries that should be included in the cache.
    HashSet<SearchFilter> newIncludeFilters = new HashSet<SearchFilter>();
    msgID = MSGID_FIFOCACHE_DESCRIPTION_INCLUDE_FILTERS;
    StringConfigAttribute includeStub =
            new StringConfigAttribute(ATTR_FIFOCACHE_INCLUDE_FILTER,
            getMessage(msgID), false, true, false);
    try {
      StringConfigAttribute includeAttr =
           (StringConfigAttribute) configEntry.getConfigAttribute(includeStub);
      if (includeAttr != null) {
        List<String> filterStrings = includeAttr.activeValues();
        if ((filterStrings == null) || filterStrings.isEmpty()) {
          // There are no include filters, so we'll allow anything by default.
        } else {
          for (String filterString : filterStrings) {
            try {
              newIncludeFilters.add(
                      SearchFilter.createFilterFromString(filterString));
            } catch (Exception e) {
              if (debugEnabled()) {
                debugCaught(DebugLogLevel.ERROR, e);
              }
              
              // We couldn't decode this filter, so it isn't valid.
              msgID = MSGID_FIFOCACHE_INVALID_INCLUDE_FILTER;
              messages.add(getMessage(msgID, String.valueOf(configEntryDN),
                      filterString,
                      stackTraceToSingleLineString(e)));
              
              if (resultCode == ResultCode.SUCCESS) {
                resultCode = ResultCode.INVALID_ATTRIBUTE_SYNTAX;
              }
              
              configIsAcceptable = false;
            }
          }
        }
      }
    } catch (Exception e) {
      if (debugEnabled()) {
        debugCaught(DebugLogLevel.ERROR, e);
      }
      
      // An error occurred, so the provided value must not be valid.
      msgID = MSGID_FIFOCACHE_INVALID_INCLUDE_FILTERS;
      messages.add(getMessage(msgID, String.valueOf(configEntryDN),
              stackTraceToSingleLineString(e)));
      
      if (resultCode == ResultCode.SUCCESS) {
        resultCode = ResultCode.CONSTRAINT_VIOLATION;
      }
      
      configIsAcceptable = false;
    }
    
    
    // Determine the set of cache filters that can be used to control the
    // entries that should be exclude from the cache.
    HashSet<SearchFilter> newExcludeFilters = new HashSet<SearchFilter>();
    msgID = MSGID_FIFOCACHE_DESCRIPTION_EXCLUDE_FILTERS;
    StringConfigAttribute excludeStub =
            new StringConfigAttribute(ATTR_FIFOCACHE_EXCLUDE_FILTER,
            getMessage(msgID), false, true, false);
    try {
      StringConfigAttribute excludeAttr =
              (StringConfigAttribute) configEntry.getConfigAttribute(excludeStub);
      if (excludeAttr != null) {
        List<String> filterStrings = excludeAttr.activeValues();
        if ((filterStrings == null) || filterStrings.isEmpty()) {
          // There are no exclude filters, so we'll allow anything by default.
        } else {
          for (String filterString : filterStrings) {
            try {
              newExcludeFilters.add(
                      SearchFilter.createFilterFromString(filterString));
            } catch (Exception e) {
              if (debugEnabled()) {
                debugCaught(DebugLogLevel.ERROR, e);
              }
              
              // We couldn't decode this filter, so it isn't valid.
              msgID = MSGID_FIFOCACHE_INVALID_EXCLUDE_FILTER;
              messages.add(getMessage(msgID, String.valueOf(configEntryDN),
                      filterString,
                      stackTraceToSingleLineString(e)));
              
              if (resultCode == ResultCode.SUCCESS) {
                resultCode = ResultCode.INVALID_ATTRIBUTE_SYNTAX;
              }
              
              configIsAcceptable = false;
            }
          }
        }
      }
    } catch (Exception e) {
      if (debugEnabled()) {
        debugCaught(DebugLogLevel.ERROR, e);
      }
      
      // An error occurred, so the provided value must not be valid.
      msgID = MSGID_FIFOCACHE_INVALID_EXCLUDE_FILTERS;
      messages.add(getMessage(msgID, String.valueOf(configEntryDN),
              stackTraceToSingleLineString(e)));
      
      if (resultCode == ResultCode.SUCCESS) {
        resultCode = ResultCode.CONSTRAINT_VIOLATION;
      }
      
      configIsAcceptable = false;
    }
    
    
    if (configIsAcceptable) {
      if (maxMemoryPercent != newMaxMemoryPercent) {
        maxMemoryPercent = newMaxMemoryPercent;
        maxAllowedMemory = runtime.maxMemory() / 100 * maxMemoryPercent;
        
        if (detailedResults) {
          messages.add(getMessage(MSGID_FIFOCACHE_UPDATED_MAX_MEMORY_PCT,
                  maxMemoryPercent, maxAllowedMemory));
        }
      }
      
      if (maxEntries != newMaxEntries) {
        maxEntries = newMaxEntries;
        
        if (detailedResults) {
          messages.add(getMessage(MSGID_FIFOCACHE_UPDATED_MAX_ENTRIES,
                  maxEntries));
        }
      }
      
      if (lockTimeout != newLockTimeout)
      {
        lockTimeout = newLockTimeout;

        if (detailedResults)
        {
          messages.add(getMessage(MSGID_FIFOCACHE_UPDATED_LOCK_TIMEOUT,
                                  lockTimeout));
        }
      }

      if (!includeFilters.equals(newIncludeFilters)) {
        includeFilters = newIncludeFilters;
        
        if (detailedResults) {
          messages.add(getMessage(MSGID_FIFOCACHE_UPDATED_INCLUDE_FILTERS));
        }
      }
      
      if (!excludeFilters.equals(newExcludeFilters)) {
        excludeFilters = newExcludeFilters;
        
        if (detailedResults) {
          messages.add(getMessage(MSGID_FIFOCACHE_UPDATED_EXCLUDE_FILTERS));
        }
      }
    }
    
    
    return new ConfigChangeResult(resultCode, adminActionRequired, messages);
  }
  
  private Entry getEntryFromDB(DN entryDN)
  {
    DatabaseEntry cacheEntryKey = new DatabaseEntry();
    cacheEntryKey.setData(entryDN.toString().getBytes());
    DatabaseEntry primaryData = new DatabaseEntry();
    
    try {
      // Get the primary key and data.
      if (entryCacheDB.get(null, cacheEntryKey,
              primaryData,
              LockMode.DEFAULT) == OperationStatus.SUCCESS) {
        
        // Decode cache entry.
        byte[] entryBytes = primaryData.getData();
        
        int pos = 0;
        // The length of the object classes.  It may be a single
        // byte or multiple bytes.
        int ocLength = entryBytes[pos] & 0x7F;
        if (entryBytes[pos++] != ocLength) {
          int numLengthBytes = ocLength;
          ocLength = 0;
          for (int i=0; i < numLengthBytes; i++, pos++) {
            ocLength = (ocLength << 8) | (entryBytes[pos] & 0xFF);
          }
        }
              
        // Next is the encoded set of object classes.  It will be a
        // single string with the object class names separated by zeros.
        LinkedHashMap<ObjectClass,String> objectClasses =
            new LinkedHashMap<ObjectClass,String>();
        int startPos = pos;
        for (int i=0; i < ocLength; i++,pos++) {
          if (entryBytes[pos] == 0x00) {
            String name = new String(entryBytes, startPos, pos-startPos,
                "UTF-8");
            String lowerName = toLowerCase(name);
            ObjectClass oc =
                DirectoryServer.getObjectClass(lowerName, true);
            objectClasses.put(oc, name);
            startPos = pos+1;
          }
        }
        String name = new String(entryBytes, startPos, pos-startPos,
            "UTF-8");
        String lowerName = toLowerCase(name);
        ObjectClass oc =
            DirectoryServer.getObjectClass(lowerName, true);
        objectClasses.put(oc, name);
              
        // Next is the total number of user attributes.  It may be a
        // single byte or multiple bytes.
        int numUserAttrs = entryBytes[pos] & 0x7F;
        if (entryBytes[pos++] != numUserAttrs) {
          int numLengthBytes = numUserAttrs;
          numUserAttrs = 0;
          for (int i=0; i < numLengthBytes; i++, pos++) {
            numUserAttrs = (numUserAttrs << 8) |
                (entryBytes[pos] & 0xFF);
          }
        }
               
        // Now, we should iterate through the user attributes and decode
        // each one.
        LinkedHashMap<AttributeType,List<Attribute>> userAttributes =
            new LinkedHashMap<AttributeType,List<Attribute>>();
        for (int i=0; i < numUserAttrs; i++) {
          // First, we have the zero-terminated attribute name.
          startPos = pos;
          while (entryBytes[pos] != 0x00) {
            pos++;
          }
          name = new String(entryBytes, startPos, pos-startPos,
              "UTF-8");
          LinkedHashSet<String> options;
          int semicolonPos = name.indexOf(';');
          if (semicolonPos > 0) {
            String baseName = name.substring(0, semicolonPos);
            lowerName = toLowerCase(baseName);
            options   = new LinkedHashSet<String>();
            
            int nextPos = name.indexOf(';', semicolonPos+1);
            while (nextPos > 0) {
              String option = name.substring(semicolonPos+1, nextPos);
              if (option.length() > 0) {
                options.add(option);
              }
              
              semicolonPos = nextPos;
              nextPos = name.indexOf(';', semicolonPos+1);
            }
            
            String option = name.substring(semicolonPos+1);
            if (option.length() > 0) {
              options.add(option);
            }
            
            name = baseName;
          } else {
            lowerName = toLowerCase(name);
            options   = new LinkedHashSet<String>(0);
          }
          AttributeType attributeType =
              DirectoryServer.getAttributeType(lowerName, true);
              
          // Next, we have the number of values.
          int numValues = entryBytes[++pos] & 0x7F;
          if (entryBytes[pos++] != numValues) {
            int numLengthBytes = numValues;
            numValues = 0;
            for (int j=0; j < numLengthBytes; j++, pos++) {
              numValues = (numValues << 8) | (entryBytes[pos] & 0xFF);
            }
          }
          
          // Next, we have the sequence of length-value pairs.
          LinkedHashSet<AttributeValue> values =
              new LinkedHashSet<AttributeValue>(numValues);
          for (int j=0; j < numValues; j++) {
            int valueLength = entryBytes[pos] & 0x7F;
            if (entryBytes[pos++] != valueLength) {
              int numLengthBytes = valueLength;
              valueLength = 0;
              for (int k=0; k < numLengthBytes; k++, pos++) {
                valueLength = (valueLength << 8) |
                    (entryBytes[pos] & 0xFF);
              }
            }
            
            byte[] valueBytes = new byte[valueLength];
            System.arraycopy(entryBytes, pos, valueBytes, 0,
                valueLength);
            values.add(new AttributeValue(attributeType,
                new ASN1OctetString(valueBytes)));
            pos += valueLength;
          }
                  
          // Create the attribute and add it to the set of user
          // attributes.
          Attribute a = new Attribute(attributeType, name, options,
              values);
          List<Attribute> attrList = userAttributes.get(attributeType);
          if (attrList == null) {
            attrList = new ArrayList<Attribute>(1);
            attrList.add(a);
            userAttributes.put(attributeType, attrList);
          } else {
            attrList.add(a);
          }
        }
               
        // Next is the total number of operational attributes.  It may
        // be a single byte or multiple bytes.
        int numOperationalAttrs = entryBytes[pos] & 0x7F;
        if (entryBytes[pos++] != numOperationalAttrs) {
          int numLengthBytes = numOperationalAttrs;
          numOperationalAttrs = 0;
          for (int i=0; i < numLengthBytes; i++, pos++) {
            numOperationalAttrs =
                (numOperationalAttrs << 8) | (entryBytes[pos] & 0xFF);
          }
        }
              
        // Now, we should iterate through the operational attributes and
        // decode each one.
        LinkedHashMap<AttributeType,List<Attribute>>
            operationalAttributes =
            new LinkedHashMap<AttributeType,List<Attribute>>();
        for (int i=0; i < numOperationalAttrs; i++) {
          // First, we have the zero-terminated attribute name.
          startPos = pos;
          while (entryBytes[pos] != 0x00) {
            pos++;
          }
          name = new String(entryBytes, startPos, pos-startPos,
              "UTF-8");
          LinkedHashSet<String> options;
          int semicolonPos = name.indexOf(';');
          if (semicolonPos > 0) {
            String baseName = name.substring(0, semicolonPos);
            lowerName = toLowerCase(baseName);
            options   = new LinkedHashSet<String>();
            
            int nextPos = name.indexOf(';', semicolonPos+1);
            while (nextPos > 0) {
              String option = name.substring(semicolonPos+1, nextPos);
              if (option.length() > 0) {
                options.add(option);
              }
              
              semicolonPos = nextPos;
              nextPos = name.indexOf(';', semicolonPos+1);
            }
            
            String option = name.substring(semicolonPos+1);
            if (option.length() > 0) {
              options.add(option);
            }
            
            name = baseName;
          } else {
            lowerName = toLowerCase(name);
            options   = new LinkedHashSet<String>(0);
          }
          AttributeType attributeType =
              DirectoryServer.getAttributeType(lowerName, true);
                  
          // Next, we have the number of values.
          int numValues = entryBytes[++pos] & 0x7F;
          if (entryBytes[pos++] != numValues) {
            int numLengthBytes = numValues;
            numValues = 0;
            for (int j=0; j < numLengthBytes; j++, pos++) {
              numValues = (numValues << 8) | (entryBytes[pos] & 0xFF);
            }
          }
          
          // Next, we have the sequence of length-value pairs.
          LinkedHashSet<AttributeValue> values =
              new LinkedHashSet<AttributeValue>(numValues);
          for (int j=0; j < numValues; j++) {
            int valueLength = entryBytes[pos] & 0x7F;
            if (entryBytes[pos++] != valueLength) {
              int numLengthBytes = valueLength;
              valueLength = 0;
              for (int k=0; k < numLengthBytes; k++, pos++) {
                valueLength = (valueLength << 8) |
                    (entryBytes[pos] & 0xFF);
              }
            }
            
            byte[] valueBytes = new byte[valueLength];
            System.arraycopy(entryBytes, pos, valueBytes, 0,
                valueLength);
            values.add(new AttributeValue(attributeType,
                new ASN1OctetString(valueBytes)));
            pos += valueLength;
          }
                
          // Create the attribute and add it to the set of operational
          // attributes.
          Attribute a = new Attribute(attributeType, name, options,
              values);
          List<Attribute> attrList =
              operationalAttributes.get(attributeType);
          if (attrList == null) {
            attrList = new ArrayList<Attribute>(1);
            attrList.add(a);
            operationalAttributes.put(attributeType, attrList);
          } else {
            attrList.add(a);
          }
        }
        
        // We've got everything that we need, so create and return the
        // entry.
        return new
          Entry(entryDN, objectClasses, userAttributes, operationalAttributes);
      }
    } catch (Exception e) {
      if (debugEnabled()) {
        debugCaught(DebugLogLevel.ERROR, e);
      }
      
      // Log an error message.
      logError(ErrorLogCategory.EXTENSIONS, ErrorLogSeverity.SEVERE_ERROR,
              /* TODO: */ 0, "getEntry by ID",
              stackTraceToSingleLineString(e));
    }
    return null;
  }
  
  private boolean putEntryToDB(Entry entry, Backend backend, long entryID)
  {
    try {
      // See if the current memory usage is within acceptable constraints.  If
      // so, then add the entry to the cache (or replace it if it is already
      // present).  If not, then remove an existing entry and don't add the new
      // entry.
      long usedMemory = runtime.totalMemory() - runtime.freeMemory();
      if (usedMemory > maxAllowedMemory) {
        // TODO:
      } else {
        // Add the entry to the cache.
        dnMap.put(entry.getDN(), entryID);
        
        LinkedHashMap<Long,DN> map = backendMap.get(backend);
        if (map == null) {
          map = new LinkedHashMap<Long,DN>();
          map.put(entryID, entry.getDN());
          backendMap.put(backend, map);
        } else {
          map.put(entryID, entry.getDN());
        }
        
        // Create key.
        DatabaseEntry cacheEntryKey = new DatabaseEntry();
        cacheEntryKey.setData(entry.getDN().toString().getBytes());
        
        // Create data.
        int totalBytes = 0;
        
        // The object classes will be encoded as one-to-five byte length
        // followed by a zero-delimited UTF-8 byte representation of the
        // names (e.g., top\0person\0organizationalPerson\0inetOrgPerson).
        int i=0;
        int totalOCBytes = entry.getObjectClasses().size() - 1;
        byte[][] ocBytes = new byte[entry.getObjectClasses().size()][];
        for (String ocName : entry.getObjectClasses().values()) {
          ocBytes[i] = getBytes(ocName);
          totalOCBytes += ocBytes[i++].length;
        }
        byte[] ocLength = ASN1Element.encodeLength(totalOCBytes);
        totalBytes += totalOCBytes + ocLength.length;
        
        
        // The user attributes will be encoded as a one-to-five byte
        // number of attributes followed by a sequence of:
        // - A UTF-8 byte representation of the attribute name.
        // - A zero delimiter
        // - A one-to-five byte number of values for the attribute
        // - A sequence of:
        //   - A one-to-five byte length for the value
        //   - A UTF-8 byte representation for the value
        i=0;
        int numUserAttributes = 0;
        int totalUserAttrBytes = 0;
        LinkedList<byte[]> userAttrBytes = new LinkedList<byte[]>();
        for (List<Attribute> attrList : entry.getUserAttributes().values()) {
          for (Attribute a : attrList) {
            if (a.isVirtual() || (! a.hasValue())) {
              continue;
            }
            
            numUserAttributes++;
            
            byte[] nameBytes = getBytes(a.getNameWithOptions());
            
            int numValues = 0;
            int totalValueBytes = 0;
            LinkedList<byte[]> valueBytes = new LinkedList<byte[]>();
            for (AttributeValue v : a.getValues()) {
              numValues++;
              byte[] vBytes = v.getValueBytes();
              byte[] vLength = ASN1Element.encodeLength(vBytes.length);
              valueBytes.add(vLength);
              valueBytes.add(vBytes);
              totalValueBytes += vLength.length + vBytes.length;
            }
            byte[] numValuesBytes = ASN1Element.encodeLength(numValues);
            
            byte[] attrBytes = new byte[nameBytes.length +
                numValuesBytes.length +
                totalValueBytes + 1];
            System.arraycopy(nameBytes, 0, attrBytes, 0,
                nameBytes.length);
            
            int pos = nameBytes.length+1;
            System.arraycopy(numValuesBytes, 0, attrBytes, pos,
                numValuesBytes.length);
            pos += numValuesBytes.length;
            for (byte[] b : valueBytes) {
              System.arraycopy(b, 0, attrBytes, pos, b.length);
              pos += b.length;
            }
            
            userAttrBytes.add(attrBytes);
            totalUserAttrBytes += attrBytes.length;
          }
        }
        byte[] userAttrCount =
            ASN1OctetString.encodeLength(numUserAttributes);
        totalBytes += totalUserAttrBytes + userAttrCount.length;
        
        // The operational attributes will be encoded in the same way as
        // the user attributes.
        i=0;
        int numOperationalAttributes = 0;
        int totalOperationalAttrBytes = 0;
        LinkedList<byte[]> operationalAttrBytes =
            new LinkedList<byte[]>();
        for (List<Attribute> attrList : 
          entry.getOperationalAttributes().values()) {
          for (Attribute a : attrList) {
            if (a.isVirtual() || (! a.hasValue())) {
              continue;
            }
            
            numOperationalAttributes++;
            
            byte[] nameBytes = getBytes(a.getNameWithOptions());
            
            int numValues = 0;
            int totalValueBytes = 0;
            LinkedList<byte[]> valueBytes = new LinkedList<byte[]>();
            for (AttributeValue v : a.getValues()) {
              numValues++;
              byte[] vBytes = v.getValueBytes();
              byte[] vLength = ASN1Element.encodeLength(vBytes.length);
              valueBytes.add(vLength);
              valueBytes.add(vBytes);
              totalValueBytes += vLength.length + vBytes.length;
            }
            byte[] numValuesBytes = ASN1Element.encodeLength(numValues);
            
            byte[] attrBytes = new byte[nameBytes.length +
                numValuesBytes.length +
                totalValueBytes + 1];
            System.arraycopy(nameBytes, 0, attrBytes, 0,
                nameBytes.length);
            
            int pos = nameBytes.length+1;
            System.arraycopy(numValuesBytes, 0, attrBytes, pos,
                numValuesBytes.length);
            pos += numValuesBytes.length;
            for (byte[] b : valueBytes) {
              System.arraycopy(b, 0, attrBytes, pos, b.length);
              pos += b.length;
            }
            
            operationalAttrBytes.add(attrBytes);
            totalOperationalAttrBytes += attrBytes.length;
          }
        }
        byte[] operationalAttrCount =
            ASN1OctetString.encodeLength(numOperationalAttributes);
        totalBytes += totalOperationalAttrBytes +
            operationalAttrCount.length;
        
   
        // Now we've got all the data that we need.  Create a big byte
        // array to hold it all and pack it in.
        byte[] entryBytes = new byte[totalBytes];
        
        int pos = 0;
        
        // Add the object classes length and values.
        System.arraycopy(ocLength, 0, entryBytes, pos, ocLength.length);
        pos += ocLength.length;
        for (byte[] b : ocBytes) {
          System.arraycopy(b, 0, entryBytes, pos, b.length);
          pos += b.length + 1;
        }
        
        // We need to back up one because there's no zero-teriminator
        // after the last object class name.
        pos--;
        
        // Next, add the user attribute count and the user attribute
        // data.
        System.arraycopy(userAttrCount, 0, entryBytes, pos,
            userAttrCount.length);
        pos += userAttrCount.length;
        for (byte[] b : userAttrBytes) {
          System.arraycopy(b, 0, entryBytes, pos, b.length);
          pos += b.length;
        }
        
        // Finally, add the operational attribute count and the
        // operational attribute data.
        System.arraycopy(operationalAttrCount, 0, entryBytes, pos,
            operationalAttrCount.length);
        pos += operationalAttrCount.length;
        for (byte[] b : operationalAttrBytes) {
          System.arraycopy(b, 0, entryBytes, pos, b.length);
          pos += b.length;
        }
        
        // Put this cache entry into JE.
        entryCacheDB.put(null, cacheEntryKey, new DatabaseEntry(entryBytes));
        //long count = entryCacheDB.count();
        //System.out.printf("<<<DEBUG>>> Entry: %s put to FSCACHE Entry Count: %d, Used Memory: %d\n",
          //      entry.getDN().toString(), count, usedMemory);
      }
      // We'll always return true in this case, even if we didn't actually add
      // the entry due to memory constraints.
      return true;
    } catch (Exception e) {
      // e.printStackTrace();
      if (debugEnabled()) {
        debugCaught(DebugLogLevel.ERROR, e);
      }
      
      // Log an error message.
      logError(ErrorLogCategory.EXTENSIONS, ErrorLogSeverity.SEVERE_ERROR,
              /* TODO: */ 0, "putEntryIfAbsent",
              stackTraceToSingleLineString(e));
      // We can't be sure there wasn't a conflict, so return false.
      return false;
    }
  }

  private class LinkedHashMapRotator<K,V> extends LinkedHashMap<K,V> {
    
    static final long serialVersionUID = 5271482121415968435L;
    
    public LinkedHashMapRotator() {
      super();
    }
    @Override protected boolean removeEldestEntry(Map.Entry eldest) {
      if (size() > maxEntries) {
        DatabaseEntry cacheEntryKey = new DatabaseEntry();
        cacheWriteLock.lock();
        cacheEntryKey.setData(eldest.getKey().toString().getBytes());
        //System.out.printf("<<<DEBUG>>> removeEldestEntry Rotating DN: %s, ID: %d,",
          //      eldest.getKey().toString(), eldest.getValue());
        try {
          long entryID = (long) ((Long) eldest.getValue()).longValue();
          // DN entryDN = idMap.get(entryID);
          Set backendSet = backendMap.keySet();
          
          Iterator backendIterator = backendSet.iterator();
          while (backendIterator.hasNext()) {
            LinkedHashMap map = backendMap.get(backendIterator.next());
            map.remove(entryID);
          }
          
          entryCacheDB.delete(null, cacheEntryKey);
          //System.out.printf(" removed: %s\n", entryDN.toString());
        } catch (Exception e) {
          e.printStackTrace();
          if (debugEnabled()) {
            debugCaught(DebugLogLevel.ERROR, e);
          }
        } finally {
          cacheWriteLock.unlock();
        }
        return true;
      } else {
        return false;
      }
    }
  }

}

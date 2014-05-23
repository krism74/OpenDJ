package org.opendj.scratch.be;

import static com.sleepycat.je.EnvironmentConfig.CHECKPOINTER_WAKEUP_INTERVAL;
import static com.sleepycat.je.EnvironmentConfig.CLEANER_LOOK_AHEAD_CACHE_SIZE;
import static com.sleepycat.je.EnvironmentConfig.LOG_FAULT_READ_SIZE;
import static com.sleepycat.je.EnvironmentConfig.LOG_ITERATOR_READ_SIZE;
import static org.forgerock.opendj.ldap.ErrorResultException.newErrorResult;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.forgerock.opendj.io.ASN1;
import org.forgerock.opendj.io.ASN1Reader;
import org.forgerock.opendj.io.ASN1Writer;
import org.forgerock.opendj.io.LDAP;
import org.forgerock.opendj.ldap.Attribute;
import org.forgerock.opendj.ldap.AttributeDescription;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.DecodeOptions;
import org.forgerock.opendj.ldap.Entries;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldap.ErrorResultException;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.requests.ModifyRequest;
import org.forgerock.opendj.ldap.schema.CoreSchema;
import org.forgerock.opendj.ldap.schema.MatchingRule;
import org.forgerock.opendj.ldif.EntryReader;

import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.Durability;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.Transaction;
import com.sleepycat.je.TransactionConfig;

public final class JEBackend implements Backend {
    private static final class WriteBuffer {
        private final ByteStringBuilder builder = new ByteStringBuilder();
        private final ASN1Writer asn1Writer = ASN1.getWriter(builder, 2048);
    }

    private final DecodeOptions decodeOptions = new DecodeOptions();
    private final AttributeDescription employeeNumberAD = AttributeDescription
            .valueOf("employeeNumber");
    private final ThreadLocal<WriteBuffer> threadLocalBuffer = new ThreadLocal<WriteBuffer>() {
        @Override
        protected WriteBuffer initialValue() {
            return new WriteBuffer();
        };
    };

    private Database dn2id = null;
    private Database employeeNumber2id = null;
    private Environment env = null;
    private Database id2entry = null;

    @Override
    public void close() {
        if (employeeNumber2id != null) {
            employeeNumber2id.close();
            employeeNumber2id = null;
        }
        if (dn2id != null) {
            dn2id.close();
            dn2id = null;
        }
        if (id2entry != null) {
            id2entry.close();
            id2entry = null;
        }
        if (env != null) {
            env.close();
            env = null;
        }
    }

    @Override
    public void importEntries(final EntryReader reader, final Map<String, String> options)
            throws Exception {
        initialize(options, true);
        try {
            long nextEntryId = 0;
            while (reader.hasNext()) {
                final Entry entry = reader.readEntry();
                final DatabaseEntry dbId = encodeEntryId(nextEntryId++);
                dn2id.put(null, encodeDn(entry.getName()), dbId);
                final ByteString encodedEmployeeNumber = encodeEmployeeNumber(entry);
                if (encodedEmployeeNumber != null) {
                    final DatabaseEntry key =
                            new DatabaseEntry(encodedEmployeeNumber.toByteArray());
                    employeeNumber2id.put(null, key, dbId);
                }
                id2entry.put(null, dbId, encodeEntry(entry));
            }
        } finally {
            close();
        }
    }

    @Override
    public void initialize(final Map<String, String> options) throws Exception {
        initialize(options, false);
    }

    @Override
    public void modifyEntry(final ModifyRequest request) throws ErrorResultException {
        final TransactionConfig config = new TransactionConfig();
        final Transaction txn = env.beginTransaction(null, config);
        try {
            // Read entry and apply updates.
            final DatabaseEntry dbId = readDn2Id(txn, request.getName());
            final Entry entry = readId2Entry(txn, dbId, true);
            final ByteString oldEmployeeNumberKey = encodeEmployeeNumber(entry);
            Entries.modifyEntry(entry, request);
            final ByteString newEmployeeNumberKey = encodeEmployeeNumber(entry);
            // Update employeeNumber index.
            final int comparison = oldEmployeeNumberKey.compareTo(newEmployeeNumberKey);
            if (comparison != 0) {
                final DatabaseEntry oldKey = new DatabaseEntry(oldEmployeeNumberKey.toByteArray());
                final DatabaseEntry newKey = new DatabaseEntry(newEmployeeNumberKey.toByteArray());
                if (comparison < 0) {
                    employeeNumber2id.delete(txn, oldKey);
                    employeeNumber2id.put(txn, newKey, dbId);
                } else {
                    employeeNumber2id.put(txn, newKey, dbId);
                    employeeNumber2id.delete(txn, oldKey);
                }
            }
            // Update id2entry index.
            id2entry.put(txn, dbId, encodeEntry(entry));
            txn.commit();
        } catch (final Exception e) {
            throw internalError(e);
        } finally {
            txn.abort();
        }
    }

    @Override
    public Entry readEntry(final DN name) throws ErrorResultException {
        try {
            return readId2Entry(null, readDn2Id(null, name), false);
        } catch (final Exception e) {
            throw internalError(e);
        }
    }

    private DatabaseEntry encodeDn(final DN dn) throws ErrorResultException {
        final ByteString dnKey = ByteString.valueOf(dn.toNormalizedString());
        return new DatabaseEntry(dnKey.toByteArray());
    }

    private ByteString encodeEmployeeNumber(final Entry entry) throws DecodeException {
        final Attribute employeeNumberAttribute = entry.getAttribute(employeeNumberAD);
        if (employeeNumberAttribute == null) {
            return null;
        }
        final ByteString employeeNumberValue = employeeNumberAttribute.firstValue();
        return employeeNumberAD.getAttributeType().getEqualityMatchingRule()
                .normalizeAttributeValue(employeeNumberValue);
    }

    private DatabaseEntry encodeEntry(final Entry entry) throws IOException {
        final WriteBuffer buffer = threadLocalBuffer.get();
        buffer.builder.clearAndTruncate(2048, 2048);
        LDAP.writeEntry(buffer.asn1Writer, entry);
        return new DatabaseEntry(buffer.builder.toByteArray());
    }

    private DatabaseEntry encodeEntryId(final long entryId) {
        return new DatabaseEntry(ByteString.valueOf(entryId).toByteArray());
    }

    private void initialize(final Map<String, String> options, final boolean isImport)
            throws Exception {
        final File dbDir = new File("db");
        dbDir.mkdirs();

        final EnvironmentConfig envConfig = new EnvironmentConfig();
        envConfig.setTransactional(!isImport);
        envConfig.setAllowCreate(true);
        envConfig.setLockTimeout(0, TimeUnit.MICROSECONDS);
        envConfig.setDurability(Durability.COMMIT_WRITE_NO_SYNC);
        envConfig.setCachePercent(60);
        envConfig.setConfigParam(CHECKPOINTER_WAKEUP_INTERVAL, "30 s");
        if (Runtime.getRuntime().maxMemory() > 256 * 1024 * 1024) {
            envConfig
                    .setConfigParam(CLEANER_LOOK_AHEAD_CACHE_SIZE, String.valueOf(2 * 1024 * 1024));
            envConfig.setConfigParam(LOG_ITERATOR_READ_SIZE, String.valueOf(2 * 1024 * 1024));
            envConfig.setConfigParam(LOG_FAULT_READ_SIZE, String.valueOf(4 * 1024));
        }
        env = new Environment(new File("db"), envConfig);

        final DatabaseConfig dbConfig =
                new DatabaseConfig().setAllowCreate(true).setKeyPrefixing(true).setTransactional(
                        !isImport).setDeferredWrite(isImport);
        id2entry = env.openDatabase(null, "id2entry", dbConfig);
        dn2id = env.openDatabase(null, "dn2id", dbConfig);
        employeeNumber2id = env.openDatabase(null, "employeeNumber2id", dbConfig);
    }

    private ErrorResultException internalError(final Exception e) {
        if (e instanceof ErrorResultException) {
            return (ErrorResultException) e;
        }
        return newErrorResult(ResultCode.OTHER, e);
    }

    private DatabaseEntry readDn2Id(final Transaction txn, final DN name)
            throws ErrorResultException {
        final DatabaseEntry dbKey = encodeDn(name);
        final DatabaseEntry dbId = new DatabaseEntry();
        if (dn2id.get(txn, dbKey, dbId, LockMode.READ_COMMITTED) != OperationStatus.SUCCESS) {
            throw newErrorResult(ResultCode.NO_SUCH_OBJECT);
        }
        return dbId;
    }

    private Entry readId2Entry(final Transaction txn, final DatabaseEntry dbId, final boolean isRMW)
            throws ErrorResultException {
        final LockMode lockMode = isRMW ? LockMode.RMW : LockMode.READ_COMMITTED;
        final DatabaseEntry dbEntry = new DatabaseEntry();
        if (id2entry.get(txn, dbId, dbEntry, lockMode) != OperationStatus.SUCCESS) {
            throw newErrorResult(ResultCode.NO_SUCH_OBJECT);
        }
        final ASN1Reader asn1Reader = ASN1.getReader(dbEntry.getData());
        try {
            return LDAP.readEntry(asn1Reader, decodeOptions);
        } catch (final IOException e) {
            throw internalError(e);
        }
    }

}

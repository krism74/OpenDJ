package org.opendj.scratch.be;

import static java.util.concurrent.Executors.newFixedThreadPool;
import static org.forgerock.opendj.ldap.LdapException.newLdapException;
import static org.forgerock.opendj.ldap.responses.Responses.newSearchResultEntry;
import static org.forgerock.util.Utils.closeSilently;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.forgerock.opendj.ldap.AbstractFilterVisitor;
import org.forgerock.opendj.ldap.AttributeFilter;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.Connections;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldap.IntermediateResponseHandler;
import org.forgerock.opendj.ldap.LDAPClientContext;
import org.forgerock.opendj.ldap.LDAPListener;
import org.forgerock.opendj.ldap.LDAPListenerOptions;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.RequestContext;
import org.forgerock.opendj.ldap.RequestHandler;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.ResultHandler;
import org.forgerock.opendj.ldap.SearchResultHandler;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.opendj.ldap.ServerConnectionFactory;
import org.forgerock.opendj.ldap.requests.AddRequest;
import org.forgerock.opendj.ldap.requests.BindRequest;
import org.forgerock.opendj.ldap.requests.CompareRequest;
import org.forgerock.opendj.ldap.requests.DeleteRequest;
import org.forgerock.opendj.ldap.requests.ExtendedRequest;
import org.forgerock.opendj.ldap.requests.ModifyDNRequest;
import org.forgerock.opendj.ldap.requests.ModifyRequest;
import org.forgerock.opendj.ldap.requests.SearchRequest;
import org.forgerock.opendj.ldap.responses.BindResult;
import org.forgerock.opendj.ldap.responses.CompareResult;
import org.forgerock.opendj.ldap.responses.ExtendedResult;
import org.forgerock.opendj.ldap.responses.Responses;
import org.forgerock.opendj.ldap.responses.Result;
import org.forgerock.opendj.ldif.EntryGenerator;
import org.forgerock.opendj.ldif.EntryReader;
import org.opendj.scratch.be.impl.H2MVStorage;
import org.opendj.scratch.be.impl.JeStorage;
import org.opendj.scratch.be.impl.MapDbStorage;
import org.opendj.scratch.be.impl.MapDbMemStorage;
import org.opendj.scratch.be.impl.OrientStorage;
import org.opendj.scratch.be.impl.PersistItStorage;
import org.opendj.scratch.be.impl.RocksDbStorage;
import org.opendj.scratch.be.impl.XodusStorage;
import org.opendj.scratch.be.spi.Storage;

@SuppressWarnings("javadoc")
public final class Server {
    private static class BackendHandler implements RequestHandler<RequestContext> {
        private final Executor threadPool;
        private final Backend backend;

        private BackendHandler(final Backend impl) {
            this.backend = impl;
            final String strategy =
                    System.getProperty("org.forgerock.opendj.transport.useWorkerThreads");
            if (strategy == null || Boolean.valueOf(strategy)) {
                this.threadPool = new Executor() {
                    @Override
                    public void execute(Runnable command) {
                        command.run();
                    }
                };
            } else {
                this.threadPool =
                        newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 2);
            }
        }

        @Override
        public void handleAdd(final RequestContext requestContext, final AddRequest request,
                final IntermediateResponseHandler intermediateResponseHandler,
                final ResultHandler<Result> resultHandler) {
            unsupported(resultHandler);
        }

        @Override
        public void handleBind(final RequestContext requestContext, final int version,
                final BindRequest request,
                final IntermediateResponseHandler intermediateResponseHandler,
                final ResultHandler<BindResult> resultHandler) {
            unsupported(resultHandler);
        }

        @Override
        public void handleCompare(final RequestContext requestContext,
                final CompareRequest request,
                final IntermediateResponseHandler intermediateResponseHandler,
                final ResultHandler<CompareResult> resultHandler) {
            unsupported(resultHandler);
        }

        @Override
        public void handleDelete(final RequestContext requestContext, final DeleteRequest request,
                final IntermediateResponseHandler intermediateResponseHandler,
                final ResultHandler<Result> resultHandler) {
            unsupported(resultHandler);
        }

        @Override
        public <R extends ExtendedResult> void handleExtendedRequest(
                final RequestContext requestContext, final ExtendedRequest<R> request,
                final IntermediateResponseHandler intermediateResponseHandler,
                final ResultHandler<R> resultHandler) {
            unsupported(resultHandler);
        }

        @Override
        public void handleModify(final RequestContext requestContext, final ModifyRequest request,
                final IntermediateResponseHandler intermediateResponseHandler,
                final ResultHandler<Result> resultHandler) {
            threadPool.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        backend.modifyEntry(request);
                        success(resultHandler);
                    } catch (final LdapException e) {
                        resultHandler.handleError(e);
                    }
                }
            });
        }

        @Override
        public void handleModifyDN(final RequestContext requestContext,
                final ModifyDNRequest request,
                final IntermediateResponseHandler intermediateResponseHandler,
                final ResultHandler<Result> resultHandler) {
            unsupported(resultHandler);
        }

        @Override
        public void handleSearch(final RequestContext requestContext, final SearchRequest request,
                final IntermediateResponseHandler intermediateResponseHandler,
                final SearchResultHandler entryHandler, final ResultHandler<Result> resultHandler) {
            final AttributeFilter attributeFilter =
                    new AttributeFilter(request.getAttributes()).typesOnly(request.isTypesOnly());
            threadPool.execute(new Runnable() {
                @Override
                public void run() {
                    if (request.getScope() == SearchScope.BASE_OBJECT) {
                        try {
                            final Entry entry = backend.readEntryByDN(request.getName());
                            final Entry filteredEntry = attributeFilter.filteredViewOf(entry);
                            entryHandler.handleEntry(newSearchResultEntry(filteredEntry));
                            success(resultHandler);
                        } catch (final LdapException e) {
                            resultHandler.handleError(e);
                        }
                    } else {
                        try {
                            final ByteString description =
                                    request.getFilter().accept(
                                            new AbstractFilterVisitor<ByteString, Void>() {
                                                @Override
                                                public ByteString visitDefaultFilter(final Void p) {
                                                    throw new UnsupportedOperationException();
                                                }

                                                @Override
                                                public ByteString visitEqualityMatchFilter(
                                                        final Void p,
                                                        final String attributeDescription,
                                                        final ByteString assertionValue) {
                                                    if (attributeDescription
                                                            .equalsIgnoreCase("description")) {
                                                        return assertionValue;
                                                    }
                                                    return visitDefaultFilter(p);
                                                }
                                            }, null);
                            try {
                                final Entry entry = backend.readEntryByDescription(description);
                                final Entry filteredEntry = attributeFilter.filteredViewOf(entry);
                                entryHandler.handleEntry(newSearchResultEntry(filteredEntry));
                                success(resultHandler);
                            } catch (final LdapException e) {
                                resultHandler.handleError(e);
                            }
                        } catch (final UnsupportedOperationException e) {
                            unsupported(resultHandler);
                        }
                    }
                }
            });
        }

        private void success(final ResultHandler<Result> resultHandler) {
            resultHandler.handleResult(Responses.newResult(ResultCode.SUCCESS));
        }

        private void unsupported(final ResultHandler<? extends Result> resultHandler) {
            resultHandler.handleError(newLdapException(ResultCode.UNWILLING_TO_PERFORM));
        }

    }

    private static enum BackendType {
        // @formatter:off
        JE(JeStorage.class),
        MEM(MapDbMemStorage.class),
        MAP(MapDbStorage.class),
        H2(H2MVStorage.class),
        ORIENT(OrientStorage.class),
        ROCKS(RocksDbStorage.class),
        PERSISTIT(PersistItStorage.class),
        XODUS(XodusStorage.class);
        // @formatter:on

        private final Class<? extends Storage> storageClass;

        private BackendType(final Class<? extends Storage> storageClass) {
            this.storageClass = storageClass;
        }

        public Backend createBackend(Map<String, String> storageOptions) throws Exception {
            Storage storage = storageClass.newInstance();
            Backend backend = new Backend(storage);
            backend.initialize(storageOptions);
            return backend;
        }
    }

    public static void main(final String[] args) {
        System.exit(new Server().run(args));
    }

    private int run(final String[] args) {
        if (args.length < 3) {
            System.err.println("Usage: ldapPort backendType numberOfEntries [backendOptions...]");
            return 1;
        }

        // Parse command line arguments.
        final int localPort = Integer.parseInt(args[0]);
        final BackendType backendType = BackendType.valueOf(args[1]);
        final int numberOfEntries = Integer.parseInt(args[2]);
        final Map<String, String> backendOptions = new LinkedHashMap<String, String>();
        for (int i = 3; i < args.length; i++) {
            final String arg = args[i];
            final int eq = arg.indexOf('=');
            if (eq > 0) {
                backendOptions.put(arg.substring(0, eq), arg.substring(eq + 1));
            } else {
                backendOptions.put(arg, arg); // option with no value
            }
        }

        // Create the backend.
        System.out.print("Initializing " + backendType + " backend...");
        final Backend backend;
        try {
            backend = backendType.createBackend(backendOptions);
        } catch (final Exception e) {
            System.out.println("failed");
            e.printStackTrace();
            return 1;
        }
        try {
            if (numberOfEntries > 0) {
                // Import data.
                System.out.println("Importing " + numberOfEntries + " entries...");
                final EntryGenerator ldif =
                        new EntryGenerator(Server.class.getResourceAsStream("test.template"))
                                .setConstant("numusers", numberOfEntries);
                final AtomicLong entryCount = new AtomicLong();
                final long startTime = System.currentTimeMillis();
                final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
                scheduler.scheduleAtFixedRate(new Runnable() {
                    @Override
                    public void run() {
                        dumpImportStats(entryCount, startTime);
                    }
                }, 5, 5, TimeUnit.SECONDS);
                final EntryReader countedLdif = new EntryReader() {
                    @Override
                    public void close() throws IOException {
                        ldif.close();
                        scheduler.shutdown();
                        dumpImportStats(entryCount, startTime);
                    }

                    @Override
                    public boolean hasNext() throws IOException {
                        return ldif.hasNext();
                    }

                    @Override
                    public Entry readEntry() throws IOException {
                        entryCount.getAndIncrement();
                        return ldif.readEntry();
                    }
                };
                try {
                    backend.importEntries(countedLdif);
                } catch (final Exception e) {
                    System.out.println("Error importing entries");
                    e.printStackTrace();
                    return 1;
                } finally {
                    closeSilently(countedLdif);
                }
            }

            // Create server.
            final ServerConnectionFactory<LDAPClientContext, Integer> connectionHandler =
                    Connections.newServerConnectionFactory(new BackendHandler(backend));
            final LDAPListenerOptions options = new LDAPListenerOptions().setBacklog(4096);
            LDAPListener listener = null;
            try {
                backend.open();
                listener = new LDAPListener(localPort, connectionHandler, options);
                System.out.println("Press any key to stop the server...");
                System.in.read();
            } catch (final Exception e) {
                System.out.println("Error listening on " + localPort);
                e.printStackTrace();
                return 1;
            } finally {
                if (listener != null) {
                    listener.close();
                }
            }
        } finally {
            backend.close();
        }
        return 0;
    }

    private static void dumpImportStats(final AtomicLong entryCount, final long startTime) {
        final long currentTime = System.currentTimeMillis();
        final long offsetTime = (currentTime - startTime) / 1000l;
        final long count = entryCount.get();
        final long rate = offsetTime > 0 ? count / offsetTime : 0l;
        System.out.println("Imported " + count + " entries in " + offsetTime
                + " seconds at a rate of " + rate + "/s");
    }
}

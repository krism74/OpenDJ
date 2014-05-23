package org.opendj.scratch.be;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.forgerock.opendj.ldap.Connections;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldap.ErrorResultException;
import org.forgerock.opendj.ldap.IntermediateResponseHandler;
import org.forgerock.opendj.ldap.LDAPClientContext;
import org.forgerock.opendj.ldap.LDAPListener;
import org.forgerock.opendj.ldap.LDAPListenerOptions;
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

public final class Server {
    private static class BackendHandler implements RequestHandler<RequestContext> {
        private final Backend backend;

        private BackendHandler(final Backend impl) {
            this.backend = impl;
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
            try {
                backend.modifyEntry(request);
                success(resultHandler);
            } catch (ErrorResultException e) {
                resultHandler.handleErrorResult(e);
            }
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
                final SearchResultHandler resultHandler) {
            if (request.getScope() != SearchScope.BASE_OBJECT) {
                unsupported(resultHandler);
                return;
            }
            try {
                Entry entry = backend.readEntry(request.getName());
                resultHandler.handleEntry(Responses.newSearchResultEntry(entry));
                success(resultHandler);
            } catch (ErrorResultException e) {
                resultHandler.handleErrorResult(e);
            }
        }

        private void success(final ResultHandler<Result> resultHandler) {
            resultHandler.handleResult(Responses.newResult(ResultCode.SUCCESS));
        }

        private void unsupported(final ResultHandler<? extends Result> resultHandler) {
            resultHandler.handleErrorResult(ErrorResultException
                    .newErrorResult(ResultCode.UNWILLING_TO_PERFORM));
        }

    }

    private static enum BackendType {
        JE(JEBackend.class);

        private final Class<? extends Backend> backendClass;

        private BackendType(final Class<? extends Backend> backendClass) {
            this.backendClass = backendClass;
        }

        public Backend createBackend(final Map<String, String> options) throws Exception {
            return backendClass.newInstance();
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
            // Import data.
            System.out.println("Importing " + numberOfEntries + " entries...");
            final EntryGenerator ldif =
                    new EntryGenerator().setConstant("numusers", numberOfEntries);
            final EntryReader countedLdif = new EntryReader() {
                final AtomicLong entryCount = new AtomicLong();

                @Override
                public Entry readEntry() throws IOException {
                    final long count = entryCount.getAndIncrement();
                    if ((count % 1000) == 0 && count > 0) {
                        System.out.println("Imported " + count + " entries");
                    }
                    return ldif.readEntry();
                }

                @Override
                public boolean hasNext() throws IOException {
                    return ldif.hasNext();
                }

                @Override
                public void close() throws IOException {
                    ldif.close();
                }
            };
            try {
                backend.importEntries(countedLdif, backendOptions);
            } catch (final Exception e) {
                System.out.println("Error importing entries");
                e.printStackTrace();
                return 1;
            } finally {
                ldif.close();
            }

            // Create server.
            final ServerConnectionFactory<LDAPClientContext, Integer> connectionHandler =
                    Connections.newServerConnectionFactory(new BackendHandler(backend));
            final LDAPListenerOptions options = new LDAPListenerOptions().setBacklog(4096);
            LDAPListener listener = null;
            try {
                backend.initialize(backendOptions);
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
}

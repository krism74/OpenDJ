#!/bin/bash -e

# LDAP search
build/package/OpenDJ-2.7.0_auto/bin/ldapsearch -p 1389 -D "cn=Directory Manager" -w admin -b "dc=example,dc=com" "(uid=bjensen)"
curl "http://localhost:8080/users/bjensen?_prettyPrint=true"

build/package/OpenDJ-2.7.0_auto/bin/ldapsearch -p 1389 -D "cn=Directory Manager" -w admin -b "dc=example,dc=com" "&"
curl "http://localhost:8080/users?_queryFilter=true&_prettyPrint=true"


# LDAP modify
# create user
build/package/OpenDJ-2.7.0_auto/bin/ldapmodify -p 1389 -D "cn=Directory Manager" -w admin -a -f ~/ldif/newuser.ldif
# add description attribute
build/package/OpenDJ-2.7.0_auto/bin/ldapmodify -p 1389 -D "cn=Directory Manager" -w admin    -f ~/ldif/newdesc.ldif
# modify description 1 attribute
build/package/OpenDJ-2.7.0_auto/bin/ldapmodify -p 1389 -D "cn=Directory Manager" -w admin    -f ~/ldif/moddesc1.ldif
# modify description 2 attribute
build/package/OpenDJ-2.7.0_auto/bin/ldapmodify -p 1389 -D "cn=Directory Manager" -w admin    -f ~/ldif/moddesc2.ldif
# make description attribute multivalued
build/package/OpenDJ-2.7.0_auto/bin/ldapmodify -p 1389 -D "cn=Directory Manager" -w admin    -f ~/ldif/multivalueddesc.ldif
# delete user
build/package/OpenDJ-2.7.0_auto/bin/ldapmodify -p 1389 -D "cn=Directory Manager" -w admin    -f ~/ldif/deluser.ldif
# display the newly added user
build/package/OpenDJ-2.7.0_auto/bin/ldapsearch -p 1389 -D "cn=Directory Manager" -w admin -b "dc=example,dc=com" "(uid=newuser)"


# REST using authentication
curl --header "X-OpenIDM-Username: name" --header "X-OpenIDM-Password: pass" "http://localhost:8080/users/bjensen?_prettyPrint=true"
curl "http://bjensen:hifalutin@localhost:8080/users?_queryFilter=true&_prettyPrint=true"
curl "http://bjensen:hifalutin@localhost:8080/users/newuser?_prettyPrint=true"


# dsconfig HTTP Connection Handler
# a bidouiller tools.properties dans le home???
build/package/OpenDJ-2.7.0_auto/bin/dsconfig --hostname localhost -p 4444 -D "cn=Directory Manager" -w admin -X     --displayCommand --advanced

build/package/OpenDJ-2.7.0_auto/bin/dsconfig --hostname localhost -p 4444 -D "cn=Directory Manager" -w admin -X -n  set-connection-handler-prop --handler-name "HTTP Connection Handler"    --set enabled:true
build/package/OpenDJ-2.7.0_auto/bin/dsconfig --hostname localhost -p 4444 -D "cn=Directory Manager" -w admin -X -n  set-connection-handler-prop --handler-name "HTTP Connection Handler"    --set authentication-required:false
build/package/OpenDJ-2.7.0_auto/bin/dsconfig --hostname localhost -p 4444 -D "cn=Directory Manager" -w admin -X -n  set-log-publisher-prop      --publisher-name "File-Based HTTP Access Logger" --set enabled:true
build/package/OpenDJ-2.7.0_auto/bin/dsconfig --hostname localhost -p 4444 -D "cn=Directory Manager" -w admin -X -n  set-log-publisher-prop      --publisher-name "File-Based Access Logger" --set suppress-internal-operations:false
build/package/OpenDJ-2.7.0_auto/bin/dsconfig --hostname localhost -p 4444 -D "cn=Directory Manager" -w admin -X -n  set-log-publisher-prop      --publisher-name "File-Based Access Logger" --set log-format:"cs-host c-ip cs-username datetime cs-method cs-uri-query cs-version sc-status sc-bytes cs(User-Agent) x-connection-id" &

# enable debug logs
build/package/OpenDJ-2.7.0_auto/bin/dsconfig --hostname localhost -p 4444 -D "cn=Directory Manager" -w admin -X -n  set-log-publisher-prop      --publisher-name "File-Based Debug Logger"  --trustStorePath build/package/OpenDJ-2.7.0_auto/config/admin-truststore --set default-debug-level:all --set enabled:true
# create debug target
build/package/OpenDJ-2.7.0_auto/bin/dsconfig --hostname localhost -p 4444 -D "cn=Directory Manager" -w admin -X -n  list-debug-targets          --publisher-name "File-Based Debug Logger"  --trustStorePath build/package/OpenDJ-2.7.0_auto/config/admin-truststore
build/package/OpenDJ-2.7.0_auto/bin/dsconfig --hostname localhost -p 4444 -D "cn=Directory Manager" -w admin -X -n  create-debug-target         --publisher-name "File-Based Debug Logger"  --trustStorePath build/package/OpenDJ-2.7.0_auto/config/admin-truststore --set debug-level:all --type generic --target-name org.opends.server.api

# stats / Performance
build/package/OpenDJ-2.7.0_auto/bin/ldapsearch -p 1389 -D "cn=Directory Manager" -w admin  -b "cn=monitor" "(objectClass=ds-connectionhandler-statistics-monitor-entry)"
build/package/OpenDJ-2.7.0_auto/bin/ldapsearch -p 1389 -D "cn=Directory Manager" -w admin  -b "cn=HTTP Connection Handler 0.0.0.0 port 8080 Statistics,cn=monitor" "(objectClass=*)"
bin/modrate -p 1500 -D "cn=directory manager" -w admin -F -c 4 -t 4 -b "uid=user.%d,ou=people,dc=example,dc=com"     -g "rand(0,2000)" -g "randstr(16)" 'description:%2$s'


# status
build/package/OpenDJ-2.7.0_auto/bin/status        -w admin -X    -D "cn=Directory Manager"
# replication
build/package/OpenDJ-2.7.0_auto/bin/dsreplication -w admin -X -n -b "dc=example,dc=com" status


# Processing time test
build/package/OpenDJ-2.7.0_auto/bin/dsconfig --hostname localhost -p 4444 -D "cn=Directory Manager" -w admin -X -n  set-connection-handler-prop --handler-name "HTTP Connection Handler"    --set enabled:true
build/package/OpenDJ-2.7.0_auto/bin/dsconfig --hostname localhost -p 4444 -D "cn=Directory Manager" -w admin -X -n  set-connection-handler-prop --handler-name "HTTP Connection Handler"    --set authentication-required:false
build/package/OpenDJ-2.7.0_auto/bin/dsconfig --hostname localhost -p 4444 -D "cn=Directory Manager" -w admin -X -n  set-log-publisher-prop      --publisher-name "File-Based HTTP Access Logger" --set enabled:true
build/package/OpenDJ-2.7.0_auto/bin/dsconfig --hostname localhost -p 4444 -D "cn=Directory Manager" -w admin -X -n  set-log-publisher-prop      --publisher-name "File-Based Access Logger" --set suppress-internal-operations:false
curl "http://bjensen:hifalutin@localhost:8080/users?_queryFilter=true&_prettyPrint=true"
for i in {5..12}; do grep conn=${i} build/package/OpenDJ-2.7.0_auto/logs/access | perl -ne 'print "$1\n" if (m/etime=(\d+)/);' | paste -sd+ | bc; done

~/scripts/setup_OPENDJ-948.sh

build/package/OpenDJ-2.7.0_auto/bin/ldapmodify -p 1389 -D "cn=Directory Manager" -w admin -f ~/ldif/OPEND-948_aci.ldif
build/package/OpenDJ-2.7.0_auto/bin/ldapsearch -p 1389 -b "dc=example,dc=com" "&"
build/package/OpenDJ-2.7.0_auto/bin/ldapsearch -p 1389 -b "cn=this does not exist,ou=people,dc=example,dc=com" "objectclass=*"
build/package/OpenDJ-2.7.0_auto/bin/ldapdelete -p 1389 "uid=user.9,ou=people,dc=example,dc=com"
build/package/OpenDJ-2.7.0_auto/bin/ldapmodify -p 1389 -f ~/ldif/OPEND-948_modify_user_entry.ldif
build/package/OpenDJ-2.7.0_auto/bin/ldapsearch -p 1389 -b "ou=people,dc=example,dc=com" "objectclass=*" debugsearchindex
build/package/OpenDJ-2.7.0_auto/bin/ldapmodify -p 1389 -a -f ~/ldif/OPEND-948_existing_user_entry.ldif


bin/modrate -p 1500 -D "cn=directory manager" -w admin --noRebind --numConnections 4 --numThreads 4 --maxIterations 16  \
            -b "uid=user.%d,ou=people,dc=example,dc=com" --argument "inc(0,500000)" --argument "randstr(16)" 'description:%2$s'






OPENDJ_JAVA_ARGS="-agentlib:jdwp=transport=dt_socket,address=localhost:8000,server=y,suspend=y"
SCRIPT_ARGS="-agentlib:jdwp=transport=dt_socket,address=localhost:8001,server=y,suspend=y"





TODO JNR:
- include real processing time in HTTP etime
- prevent undesired properties in JSON config
- hook grizzly logs into OpenDJ server logs
- only enable the HTTP access log publishers when the HTTP handler is started
- add tests for BoundedWorkQueueStrategy
- Enable HTTP conn handler by default
	- Change setup to offer a port for it
	- fix running tests
- Bug SEARCH RES after DISCONNECT in HTTP conn handler log
- http://docs.oracle.com/javaee/6/api/javax/servlet/ServletRequest.html#getRemoteHost%28%29:
	"If the engine cannot or chooses not to resolve the hostname (to improve performance), this method returns the dotted-string form of the IP address."
	How to configure this with Grizzly?






    synchronized (this)
    {
      StringWriter sw = new StringWriter();
      Exception e = new Exception();
      e.fillInStackTrace();
      e.printStackTrace(new PrintWriter(sw));
      System.err.println(DateFormat.getInstance().format(new Date())
          + " DISCONNECT " + Thread.currentThread().getName() + "\\n"
          + sw.toString());
    }





      if (getOperationID() == 1)
      {
        synchronized (this)
        {
          logSearchResultDone(this);
          StringWriter sw = new StringWriter();
          Exception e = new Exception();
          e.fillInStackTrace();
          e.printStackTrace(new PrintWriter(sw));
          System.err.println(DateFormat.getInstance().format(new Date())
              + " SEARCH RES " + Thread.currentThread().getName() + "\\n"
              + sw.toString());
        }
      }






  @SuppressWarnings({ "rawtypes", "unchecked" })
  private <R> FutureResult<R> enqueueOperation(
      Operation operation, ResultHandler<? super R> resultHandler)
  {
    final AsynchronousFutureResult<R, ResultHandler<? super R>> futureResult =
        new AsynchronousFutureResult<R, ResultHandler<? super R>>(
            resultHandler, operation.getMessageID());

    try
    {
      operation.setInnerOperation(this.clientConnection.isInnerConnection());

      HTTPConnectionHandler connHandler =
          this.clientConnection.getConnectionHandler();
      if (connHandler.keepStats())
      {
        connHandler.getStatTracker().updateMessageRead(
            new LDAPMessage(operation.getMessageID(),
                toRequestProtocolOp(operation)));
      }

      // need this raw cast here to fool the compiler's generic type safety
      // Problem here is due to the generic type R on enqueueOperation()
      clientConnection.addOperationInProgress(operation,
          (AsynchronousFutureResult) futureResult);

      queueingStrategy.enqueueRequest(operation);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }
      clientConnection.removeOperationInProgress(operation.getMessageID());
      // TODO JNR add error message??
      // ResourceException rs = Rest2LDAP.asResourceException(e);
      futureResult.handleErrorResult(ErrorResultException.newErrorResult(
          ResultCode.OPERATIONS_ERROR, e));
    }
    return futureResult;
  }




OPENDJ-942 (CR-1744) HTTP Connection Handler - Running an authenticated request at "/" hangs

The problem was due to a misconfiguration of Grizzly.
Using HttpServer.createSimpleServer() to create the server means Grizzly adds unwanted network listeners and HTTP handlers that interfere with the expected behaviour of the application.
Problem was solved by doing a direct configuration of the HTTP server, removing unwanted mappings and servlets.

HTTPConnectionHandler.java:
In startHttpServer(), extracted methods createHttpServer() and createAndRegisterServlet().
In createHttpServer(), do not use HttpServer.createSimpleServer() anymore and do a direct, clutterless configuration.
In setHttpStatsProbe() and getHttpConfig(), added an HTTPServer parameter so this method can be used before or after creating the HTTPServer.




#!/bin/bash -e

# LDAP search
build/package/OpenDJ-2.7.0_auto/bin/ldapsearch -p 1389 -D "cn=Directory Manager" -w admin -T -b "dc=example,dc=com" "(uid=bjensen)"
curl "http://localhost:8080/users/bjensen?_prettyPrint=true"

build/package/OpenDJ-2.7.0_auto/bin/ldapsearch -p 1389 -D "cn=Directory Manager" -w admin -T -b "dc=example,dc=com" "&"
curl "http://localhost:8080/users?_queryFilter=true&_prettyPrint=true"


# LDAP modify
# create user
build/package/OpenDJ-2.7.0_auto/bin/ldapmodify -p 1389 -D "cn=Directory Manager" -w admin -a -f ~/ldif/newuser.ldif
# create user inline
build/package/OpenDJ-2.7.0_auto/bin/ldapmodify -p 1389 -D "cn=Directory Manager" -w admin -a <<END_OF_COMMAND_INPUT
dn: cn=A1,dc=example,dc=com
objectclass:top
objectclass:organizationalperson
objectclass:inetorgperson
objectclass:person
sn:User
cn:Test User
description:1
description:2
mail:bla@example.com
telephonenumber:+33165990803
END_OF_COMMAND_INPUT
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
build/package/OpenDJ-2.7.0_auto/bin/ldapsearch -p 1389 -D "cn=Directory Manager" -w admin -T -b "dc=example,dc=com" "(uid=newuser)"


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
build/package/OpenDJ-2.7.0_auto/bin/dsconfig --hostname localhost -p 4444 -D "cn=Directory Manager" -w admin -X -n  set-log-publisher-prop      --publisher-name "File-Based Debug Logger"  --set default-debug-level:all --set enabled:true
# create debug target
build/package/OpenDJ-2.7.0_auto/bin/dsconfig --hostname localhost -p 4444 -D "cn=Directory Manager" -w admin -X -n  create-debug-target         --publisher-name "File-Based Debug Logger"  --set debug-level:all --type generic --target-name org.opends.server.api

# stats / Performance
build/package/OpenDJ-2.7.0_auto/bin/ldapsearch -p 1389 -D "cn=Directory Manager" -w admin  -T -b "cn=monitor" "(objectClass=ds-connectionhandler-statistics-monitor-entry)"
build/package/OpenDJ-2.7.0_auto/bin/ldapsearch -p 1389 -D "cn=Directory Manager" -w admin  -T -b "cn=HTTP Connection Handler 0.0.0.0 port 8080 Statistics,cn=monitor" "(objectClass=*)"
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


build/package/OpenDJ-2.7.0_auto/bin/ldapmodify -p 1389 -D "cn=Directory Manager" -w admin -f ~/ldif/OPEND-948_aci.ldif
build/package/OpenDJ-2.7.0_auto/bin/ldapsearch -p 1389 -T -b "dc=example,dc=com" "&"
build/package/OpenDJ-2.7.0_auto/bin/ldapsearch -p 1389 -T -b "cn=this does not exist,ou=people,dc=example,dc=com" "objectclass=*"
build/package/OpenDJ-2.7.0_auto/bin/ldapdelete -p 1389 "uid=user.9,ou=people,dc=example,dc=com"
build/package/OpenDJ-2.7.0_auto/bin/ldapmodify -p 1389 -f ~/ldif/OPEND-948_modify_user_entry.ldif
build/package/OpenDJ-2.7.0_auto/bin/ldapsearch -p 1389 -T -b "ou=people,dc=example,dc=com" "objectclass=*" debugsearchindex
build/package/OpenDJ-2.7.0_auto/bin/ldapmodify -p 1389 -a -f ~/ldif/OPEND-948_existing_user_entry.ldif

# replication

bin/modrate -p 1500 -D "cn=directory manager" -w admin --noRebind --numConnections 4 --numThreads 4 --maxIterations 16  \
            -b "uid=user.%d,ou=people,dc=example,dc=com" --argument "inc(0,500000)" --argument "randstr(16)" 'description:%2$s'
# search on changelog
build/package/OpenDJ-2.7.0_auto/bin/ldapsearch -p 1501 -D "cn=Directory Manager" -w admin -T -b "cn=changelog" "&" "*" "+" | less
# persistent search on changelog
build/package/OpenDJ-2.7.0_auto/bin/ldapsearch -p 1501 -D "cn=Directory Manager" -w admin -C ps:all -T -b "cn=changelog" "&" "(objectclass=*)" | less
# search on changelog with changenumber
build/package/OpenDJ-2.7.0_auto/bin/ldapsearch -p 1501 -D "cn=Directory Manager" -w admin -T -b "cn=changelog" "changenumber>=1" "*" "+"
# search on changelog with changelogcookie
build/package/OpenDJ-2.7.0_auto/bin/ldapsearch -p 1501 -D "cn=Directory Manager" -w admin -T -b "cn=changelog" "changelogcookie=...cookie..." "*" "+"
# search on lastchangenumber virtual attribute
build/package/OpenDJ-2.7.0_auto/bin/ldapsearch -p 1501 -D "cn=Directory Manager" -w admin -T -b "" -s base "&" lastchangenumber






OPENDJ_JAVA_ARGS="-agentlib:jdwp=transport=dt_socket,address=8000,server=y,suspend=y"
SCRIPT_ARGS="-agentlib:jdwp=transport=dt_socket,address=8001,server=y,suspend=y"





TODO JNR:
- include real processing time in HTTP etime
- hook grizzly logs into OpenDJ server logs
- only enable the HTTP access log publishers when the HTTP handler is started
- Enable HTTP conn handler by default
	- Change setup to offer a port for it
	- fix running tests
- Bug SEARCH RES after DISCONNECT in HTTP conn handler log
- http://docs.oracle.com/javaee/6/api/javax/servlet/ServletRequest.html#getRemoteHost%28%29:
	"If the engine cannot or chooses not to resolve the hostname (to improve performance), this method returns the dotted-string form of the IP address."
	How to configure this with Grizzly?

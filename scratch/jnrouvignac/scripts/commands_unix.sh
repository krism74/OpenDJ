#!/bin/bash -e

# To be used when Matt updates Rest2LDAP JSON config file
svn rm resource/config/http-config.json
svn cp https://svn.forgerock.org/opendj/trunk/opendj3/opendj-rest2ldap-servlet/src/main/webapp/opendj-rest2ldap-servlet.json resource/config/http-config.json
svn st resource/config/http-config.json
svn commit resource/config/http-config.json -m 'Imported the latest sample config file from Rest2LDAP'

svn log https://svn.forgerock.org/opendj/trunk/scratch/jnrouvignac/

# svn propedit
svn propedit svn:ignore .
svn propedit svn:log --revprop -r
svn propget svn:log --revprop -r

# Run unit tests in Eclipse
cp -r build/classes/admin build/classes/messages .eclipse-build # then hit F5 in Eclipse

# Copy from Maven repo to ivy repo
cp ~/.m2/repository/org/forgerock/commons/json-fluent/2.0.0-SNAPSHOT/json-fluent-2.0.0-SNAPSHOT.jar 				~/.ivy2/cache/org.forgerock.commons/json-fluent/bundles/json-fluent-2.0.0-SNAPSHOT.jar
cp ~/.m2/repository/org/forgerock/commons/json-fluent/2.0.0-SNAPSHOT/json-fluent-2.0.0-SNAPSHOT-sources.jar 			~/.ivy2/cache/org.forgerock.commons/json-fluent/sources/json-fluent-2.0.0-SNAPSHOT-sources.jar
cp ~/.m2/repository/org/forgerock/commons/json-schema/2.0.0-SNAPSHOT/json-schema-2.0.0-SNAPSHOT.jar 				~/.ivy2/cache/org.forgerock.commons/json-schema/bundles/json-schema-2.0.0-SNAPSHOT.jar
cp ~/.m2/repository/org/forgerock/commons/json-schema/2.0.0-SNAPSHOT/json-schema-2.0.0-SNAPSHOT-sources.jar 			~/.ivy2/cache/org.forgerock.commons/json-schema/sources/json-schema-2.0.0-SNAPSHOT-sources.jar
cp ~/.m2/repository/org/forgerock/commons/json-resource-servlet/2.0.0-SNAPSHOT/json-resource-servlet-2.0.0-SNAPSHOT.jar 	~/.ivy2/cache/org.forgerock.commons/json-resource-servlet/bundles/json-resource-servlet-2.0.0-SNAPSHOT.jar
cp ~/.m2/repository/org/forgerock/commons/json-resource-servlet/2.0.0-SNAPSHOT/json-resource-servlet-2.0.0-SNAPSHOT-sources.jar ~/.ivy2/cache/org.forgerock.commons/json-resource-servlet/sources/json-resource-servlet-2.0.0-SNAPSHOT-sources.jar
cp ~/.m2/repository/org/forgerock/opendj/opendj-rest2ldap/2.5.0-SNAPSHOT/opendj-rest2ldap-2.5.0-SNAPSHOT.jar 			~/.ivy2/cache/org.forgerock.opendj/opendj-rest2ldap/bundles/opendj-rest2ldap-2.5.0-SNAPSHOT.jar






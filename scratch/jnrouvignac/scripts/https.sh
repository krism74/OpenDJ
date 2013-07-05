#!/bin/bash -e


################
# TO DEBUG SSL #
################
# start the server with   -Djavax.net.debug=all OR -Djavax.net.debug=ssl


#############################################
# Start from scratch with separate keystore #
#############################################

# generate the certificate
keytool -genkey -alias server-cert -keyalg rsa -dname "uid=server,dc=example,dc=com" -keystore keystore-file -storepass servercert -keypass servercert -storetype JKS

# configure the server to use that certificate and start the HTTPS conn handler
build/package/OpenDJ-2.7.0_auto/bin/dsconfig set-key-manager-provider-prop   -h localhost -p 4444 -D "cn=Directory Manager" -w admin -n -X --provider-name "JKS"                    --set key-store-file:/home/jnrouvignac/opendj2/trunk/opends/keystore-file --reset key-store-pin-file --set key-store-pin:servercert --set enabled:true 
build/package/OpenDJ-2.7.0_auto/bin/dsconfig set-trust-manager-provider-prop -h localhost -p 4444 -D "cn=Directory Manager" -w admin -n -X --provider-name "Blind Trust"            --set enabled:true
build/package/OpenDJ-2.7.0_auto/bin/dsconfig set-connection-handler-prop     -h localhost -p 4444 -D "cn=Directory Manager" -w admin -n -X --handler-name "HTTP Connection Handler" --set ssl-cert-nickname:server-cert --set trust-manager-provider:"Blind Trust" --set key-manager-provider:"JKS" --set enabled:true --set use-ssl:true

curl "https://bjensen:hifalutin@localhost:8080/users?_queryFilter=true&_prettyPrint=true"  --insecure # turn off certificate validation


#######################################################################################################################
# If server setup was done with --generateSelfSignedCertificate (in conjunction with --enableStartTLS or --ldapsPort) #
#######################################################################################################################

# configure the server to use the certificate generated during setup and start the HTTPS conn handler
build/package/OpenDJ-2.7.0_auto/bin/dsconfig set-trust-manager-provider-prop -h localhost -p 4444 -D "cn=Directory Manager" -w admin -n -X --provider-name "Blind Trust"            --set enabled:true
build/package/OpenDJ-2.7.0_auto/bin/dsconfig set-connection-handler-prop     -h localhost -p 4444 -D "cn=Directory Manager" -w admin -n -X --handler-name "HTTP Connection Handler" --set trust-manager-provider:"Blind Trust" --set key-manager-provider:"JKS" --set enabled:true --set use-ssl:true

# test the HTTPS conn handler
curl "https://bjensen:hifalutin@localhost:8080/users?_queryFilter=true&_prettyPrint=true"  --insecure # turn off certificate validation

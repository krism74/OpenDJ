#!/bin/bash -e

BUILD_DIR=`pwd`
PACKAGE_DIR="${BUILD_DIR}/build/package/OpenDJ-2.7.0"
DATETIME=`date +%Y%m%d_%H%M%S`
BASE_DIR="/ssddata"
SETUP_DIR="$BASE_DIR/${PACKAGE_DIR}_$DATETIME"
SERVER_PID_FILE="logs/server.pid"
HOSTNAME=jeannoel-laptop.local
BIND_DN="cn=Directory Manager"
PASSWORD=admin
BASE_DN="dc=example,dc=com"
REPLICA_DIRS=( OpenDJ-2.7.0_DS1_group1 \
               OpenDJ-2.7.0_RS1_group1 \
               OpenDJ-2.7.0_RS2_group2 \
               OpenDJ-2.7.0_RS3_group3 \
             )





for IDX in ${!REPLICA_DIRS[*]}
do
    DIR="$BASE_DIR/${REPLICA_DIRS[$IDX]}"
    if [ -e $DIR ]
    then
        cd $DIR
        echo
        echo "##################################################################################################"
        echo "# Stopping server $DIR"
        echo "##################################################################################################"
        if [ -e $SERVER_PID_FILE ]
        then
            SERVER_PID=`cat $SERVER_PID_FILE`
            # temporarily disable stop on error then reenable again
            set +e
            kill -KILL $SERVER_PID
            set -e
        fi
        bin/stop-ds
        cd ..

        echo rm -rf $DIR
        rm -rf $DIR
    fi
    echo cp -r $PACKAGE_DIR $DIR
    cp -r $PACKAGE_DIR $DIR


    cd $DIR
    echo
    echo "##################################################################################################"
    echo "# Setting up and starting server $DIR, debugging on port 800$IDX"
    echo "##################################################################################################"
    if [[ "$DIR" == *DS* ]]
    then
        if [ "$IDX" -eq "" ]
        then
            # import initial data
            bin/import-ldif \
                    --backendID userRoot \
                    --ldifFile ~/ldif/Example.ldif \
                    --clearBackend
                    # -D "cn=Directory Manager" -w admin
        else
            ./setup --cli -d 1000 -D "$BIND_DN" -w $PASSWORD -n -p 150$IDX --adminConnectorPort 450$IDX -b "$BASE_DN" -O
        fi
    else
        # so far we only consider RSs or DSRSs
        ./setup --cli             -D "$BIND_DN" -w $PASSWORD -n -p 150$IDX --adminConnectorPort 450$IDX -b "$BASE_DN" -O
    fi

    OPENDJ_JAVA_ARGS="-agentlib:jdwp=transport=dt_socket,address=localhost:800$IDX,server=y,suspend=n" bin/start-ds


    # enable combined logs
    bin/dsconfig     -h $HOSTNAME -p 450$IDX -D "$BIND_DN" -w $PASSWORD --trustAll --no-prompt \
                     set-log-publisher-prop        --publisher-name "File-Based Access Logger" --set log-format:combined
    # keep only 1 file for logs/access
    bin/dsconfig     -h $HOSTNAME -p 450$IDX -D "$BIND_DN" -w $PASSWORD --trustAll --no-prompt \
                     set-log-retention-policy-prop --policy-name "File Count Retention Policy" --set number-of-files:1



    if [[ "$DIR" == *DSRS* ]]
    then
        : # empty for now
    elif [[ "$DIR" == *DS* ]]
    then
        : # empty for now
    elif [[ "$DIR" == *RS* ]]
    then
        echo
        echo "##################################################################################################"
        echo "# Creating replication link: ${REPLICA_DIRS[0]} => ${REPLICA_DIRS[$IDX]}"
        echo "##################################################################################################"
        bin/dsreplication enable \
            --adminUID admin --adminPassword $PASSWORD --baseDN "$BASE_DN" --trustAll --no-prompt \
            --host1 $HOSTNAME     --port1 4500    --bindDN1 "$BIND_DN" --bindPassword1 $PASSWORD --noReplicationServer1 \
            --host2 $HOSTNAME     --port2 450$IDX --bindDN2 "$BIND_DN" --bindPassword2 $PASSWORD --replicationPort2 890$IDX #--onlyReplicationServer2
        echo "Done."

        echo
        echo "##################################################################################################"
        echo "# Setting replication group #$IDX for ${REPLICA_DIRS[$IDX]}"
        echo "##################################################################################################"
        bin/dsconfig -h $HOSTNAME -p 450$IDX -D "$BIND_DN" -w $PASSWORD --trustAll --no-prompt \
                     set-replication-server-prop   --provider-name "Multimaster Synchronization" --set group-id:$IDX
        echo "Done."
    fi


    cd ..
done

# let RS3 start
sleep 10

echo
echo "##################################################################################################"
echo "# Initializing replication"
echo "##################################################################################################"
IDX=0
DIR=${REPLICA_DIRS[$IDX]}
cd $DIR

bin/dsreplication    initialize-all --adminUID admin \
                     -h $HOSTNAME -p 450$IDX -b "$BASE_DN" -w $PASSWORD --trustAll --no-prompt

cd ..


#!/bin/bash -e

BUILD_DIR=`pwd`
PACKAGE_DIR="${BUILD_DIR}/build/package/OpenDJ-2.7.0"
DATETIME=`date +%Y%m%d_%H%M%S`
BASE_DIR="/ssddata"
SETUP_DIR="$BASE_DIR/${PACKAGE_DIR}_$DATETIME"
SERVER_PID_FILE="logs/server.pid"
HOSTNAME=jeannoel-laptop
BIND_DN="cn=Directory Manager"
PASSWORD=admin
BASE_DN="dc=example,dc=com"
# Naming is important here:
# DS   means: deploy a DS only node
#   RS means: deploy a RS only node
# DSRS means: deploy a node which is both DS and RS
REPLICA_DIRS=( OpenDJ-2.7.0_DS1_group1 \
               OpenDJ-2.7.0_DSRS1_group1 \
               OpenDJ-2.7.0_DSRS2_group2 \
               OpenDJ-2.7.0_DSRS3_group3 \
             )




for IDX in ${!REPLICA_DIRS[*]}
do
    DIR="$BASE_DIR/${REPLICA_DIRS[$IDX]}"
    unset IS_DSRS
    unset IS_DS_ONLY
    unset IS_RS_ONLY
    unset IS_DS
    unset IS_RS

    if [[ "$DIR" == *DSRS* ]]
    then
        IS_DSRS=1
        IS_DS=1
        IS_RS=1
    elif [[ "$DIR" == *DS* ]]
    then
        IS_DS_ONLY=1
        IS_DS=1
    elif [[ "$DIR" == *RS* ]]
    then
        IS_RS_ONLY=1
        IS_RS=1
    fi

    ###################################
    # Stop/Kill previous server
    ###################################
    if [ -e "$DIR" ]
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


    ###################################
    # Setup
    ###################################
    cd $DIR
    echo
    echo "##################################################################################################"
    echo "# Setting up and starting server $DIR, debugging on port 800$IDX"
    echo "##################################################################################################"
    SETUP_ARGS=""
    if [ -n ${IS_DS} ]
    then
        # import initial data
        # bin/import-ldif \
        #         --backendID userRoot \
        #         --ldifFile ~/ldif/Example.ldif \
        #         --clearBackend
        #         # -D "cn=Directory Manager" -w admin
        if [ -z "${DATA_INITIALIZED}" ]
        then
            SETUP_ARGS="$SETUP_ARGS -d 1000"
            DATA_INITIALIZED=1
        fi

        SETUP_ARGS="$SETUP_ARGS -b $BASE_DN"
    elif [ -n ${IS_RS} ]
    then
        : # empty for now
    fi
    ./setup --cli -D "$BIND_DN" -w $PASSWORD -n -p 150$IDX --adminConnectorPort 450$IDX  $SETUP_ARGS  -O

    OPENDJ_JAVA_ARGS="-agentlib:jdwp=transport=dt_socket,address=localhost:800$IDX,server=y,suspend=n" bin/start-ds


    # enable combined logs
    bin/dsconfig     -h $HOSTNAME -p 450$IDX -D "$BIND_DN" -w $PASSWORD --trustAll --no-prompt \
                     set-log-publisher-prop        --publisher-name "File-Based Access Logger" --set log-format:combined
    # keep only 1 file for logs/access
    bin/dsconfig     -h $HOSTNAME -p 450$IDX -D "$BIND_DN" -w $PASSWORD --trustAll --no-prompt \
                     set-log-retention-policy-prop --policy-name "File Count Retention Policy" --set number-of-files:1

    ###################################
    # Replication
    ###################################
    if [ -n ${IS_RS} -a ${IDX} -ne 0 ]
    then
        RS_ARGS=""
        if [ -n ${IS_DSRS} ]
        then
            : # empty for now
        elif [ -n ${IS_RS_ONLY} ]
        then
            RS_ARGS="--onlyReplicationServer2"
        fi

        echo
        echo "##################################################################################################"
        echo "# Creating replication link: ${REPLICA_DIRS[0]} => ${REPLICA_DIRS[$IDX]}"
        echo "##################################################################################################"
        bin/dsreplication enable \
            --adminUID admin --adminPassword $PASSWORD --baseDN "$BASE_DN" --trustAll --no-prompt \
            --host1 $HOSTNAME     --port1 4500    --bindDN1 "$BIND_DN" --bindPassword1 $PASSWORD --noReplicationServer1 \
            --host2 $HOSTNAME     --port2 450$IDX --bindDN2 "$BIND_DN" --bindPassword2 $PASSWORD --replicationPort2 890$IDX $RS_ARGS
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

# let last node finish startup
sleep 10

echo
echo "##################################################################################################"
echo "# Initializing replication"
echo "##################################################################################################"
IDX=0
DIR=${REPLICA_DIRS[$IDX]}
cd $DIR

# Next command is only useful when there is more than one DS
# TODO need validation there is more than one DS
bin/dsreplication    initialize-all --adminUID admin \
                     -h $HOSTNAME -p 450$IDX -b "$BASE_DN" -w $PASSWORD --trustAll --no-prompt

cd ..


#!/bin/bash -e

SERVER_PID_FILE="logs/server.pid"
REPLICA_DIRS=( OpenDJ-2.7.0_DS1_group1 \
               OpenDJ-2.7.0_RS1_group1 \
               OpenDJ-2.7.0_RS2_group2 \
               OpenDJ-2.7.0_RS3_group3 \
             )

for IDX in ${!REPLICA_DIRS[*]}
do
    DIR=${REPLICA_DIRS[$IDX]}
    if [ -e $DIR ]
    then
        cd $DIR
        if [ -e $SERVER_PID_FILE ]
        then
            SERVER_PID=`cat $SERVER_PID_FILE`
            kill -CONT $SERVER_PID
        fi
        bin/stop-ds && echo "Server $DIR stopped" &
        cd ..
    fi
done


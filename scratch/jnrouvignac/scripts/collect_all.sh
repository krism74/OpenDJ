#!/bin/bash -e

set -x

BASE_DIR="/ssddata"
REPLICA_DIRS=( OpenDJ-2.7.0_DS1_group1 \
               OpenDJ-2.7.0_DSRS1_group1 \
               OpenDJ-2.7.0_DSRS2_group2 \
               OpenDJ-2.7.0_DSRS3_group3 \
             )


if [ -z "$1" ]; then
    echo "Please specify a directory where to backup the logs"
    exit 1;
fi

BACKUP_DIR="$1"
if [ -d "$BACKUP_DIR" ]; then
    echo "Directory $BACKUP_DIR already exist, please choose another directory name"
    exit 1;
fi


mkdir "$BACKUP_DIR"

if [ -f "output.txt" ]; then
    cp output.txt "$BACKUP_DIR"
fi


for IDX in ${!REPLICA_DIRS[*]}
do
    DIR="$BASE_DIR/${REPLICA_DIRS[$IDX]}"
    INSTANCE_BACKUP_DIR="$BACKUP_DIR/${REPLICA_DIRS[$IDX]}"

    for SUBDIR in logs config
    do
        SUB_BACKUP_DIR="$INSTANCE_BACKUP_DIR/$SUBDIR"
        mkdir -p "$SUB_BACKUP_DIR"
        cp -r $DIR/$SUBDIR/* "$SUB_BACKUP_DIR"
    done;

    rm -f $INSTANCE_BACKUP_DIR/config/*keystore*
    rm -f $INSTANCE_BACKUP_DIR/config/*truststore*
    #TODO should we remove admin-backend too?

    #TODO JNR collect size of the different DBs
done


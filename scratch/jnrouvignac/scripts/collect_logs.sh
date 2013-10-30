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

LOG_BACKUP_DIR="$1"
if [ -d "$LOG_BACKUP_DIR" ]; then
  echo "Directory $LOG_BACKUP_DIR already exist, please choose another directory name"
  exit 1;
fi


mkdir "$LOG_BACKUP_DIR"

cp output.txt "$LOG_BACKUP_DIR"

for IDX in ${!REPLICA_DIRS[*]}
do
    DIR="$BASE_DIR/${REPLICA_DIRS[$IDX]}"
    INSTANCE_LOG_BACKUP_DIR="$LOG_BACKUP_DIR/${REPLICA_DIRS[$IDX]}"
    mkdir "$INSTANCE_LOG_BACKUP_DIR"
    cp $DIR/logs/* "$INSTANCE_LOG_BACKUP_DIR"
done


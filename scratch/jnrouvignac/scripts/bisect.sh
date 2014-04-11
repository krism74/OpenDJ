#!/bin/bash

# svn-bisect --min 9067 --max 10635 start
# svn-bisect run ./run.sh

~/scripts/killall_servers.sh;

ant clean package;
if [ $? -ne 0 ]
then
    exit 125 # skip revision
fi

cd /ssddata/svn/QA-OpenDJ/robot-framework/trunk
if [ $? -ne 0 ]
then
    exit 125 # stop bisecting
fi

./run-pybot.sh --suite testcases.replication.externalchangelog .
FINAL_RESULT=$?

cd -

exit $FINAL_RESULT

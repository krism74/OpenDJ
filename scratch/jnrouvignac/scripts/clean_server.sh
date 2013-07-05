#!/bin/bash -e


# save the debug state for CLI commands
TMP_SCRIPT_ARGS=$SCRIPT_ARGS
unset SCRIPT_ARGS



echo "###################"
echo "# stopping OpenDJ #"
echo "###################"
echo bin/stop-ds
bin/stop-ds

DEBUG_HOST=localhost
DEBUG_PORT=8000

# check during 2 seconds whether there is one process that keeps listening on our debug port
START_DATE=`date +%s`
END_DATE=`date +%s`
while [ $(( END_DATE - START_DATE )) -lt 2 ]
do
    netstat -ln | grep $DEBUG_PORT > /dev/null;
    if [ "$?" -ne 0 ]
    then
        break;
    fi
    END_DATE=`date +%s`
done

if [ $(( END_DATE - START_DATE )) -ge 2 ]
then
    echo
    echo "Port $DEBUG_PORT already in use. Please close the program listening on it".
    exit
fi


echo
echo "#############################"
echo "# cleaning the DB under db/ #"
echo "#############################"
echo rm -r db/*
rm -r db/*

echo
echo "################################################"
echo "# cleaning the backed up configs under config/ #"
echo "################################################"
CONFIG_FILES=(config/archived-configs \
              config/admin-backend.ldif.old \
              config/admin-keystore \
              config/admin-keystore.pin \
              config/admin-truststore \
              config/ads-truststore \
              config/ads-truststore.pin \
              config/ldif.startok \
              config/tasks.ldif \
              config/tasks.ldif.old)
for FILE in ${CONFIG_FILES[*]}
do
    if [ -e $FILE ]
    then
        echo rm -r $FILE
        rm -r $FILE
    fi
done

LDIF_FILE=tests/staf-tests/functional-tests/shared/data/backends/Example.ldif
echo
echo "#################################################################################"
echo "# importing $LDIF_FILE #"
echo "#################################################################################"
echo bin/import-ldif -n userRoot -l ~/workspace/OpenDS/$LDIF_FILE
bin/import-ldif -n userRoot -l ~/workspace/OpenDS/$LDIF_FILE

echo
echo "###################"
echo "# starting OpenDJ #"
echo "###################"
echo OPENDJ_JAVA_ARGS="-agentlib:jdwp=transport=dt_socket,address=$DEBUG_HOST:$DEBUG_PORT,server=y,suspend=n" bin/start-ds
OPENDJ_JAVA_ARGS="-agentlib:jdwp=transport=dt_socket,address=$DEBUG_HOST:$DEBUG_PORT,server=y,suspend=n" bin/start-ds



# reset the debug state for CLI commands
export SCRIPT_ARGS=$TMP_SCRIPT_ARGS
unset TMPSCRIPT_ARGS



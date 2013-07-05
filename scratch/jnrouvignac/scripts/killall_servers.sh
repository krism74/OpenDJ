#!/bin/bash -e

ps aux | grep java | grep .opends. | perl -ne 'print "$1\n" if (m/^\d+\s+(\d+)/);' | xargs kill -9
ps aux | grep java | grep .opends. > /dev/null
while [ $? -eq 0 ]
do
    sleep 1
    ps aux | grep java | grep .opends. > /dev/null
done


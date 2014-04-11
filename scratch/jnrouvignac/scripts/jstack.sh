#!/bin/bash -e

NB_JSTACK=9
for pid in "$@"
do
    for ((i = 0 ; i <= ${NB_JSTACK} ; i++ ))
    do
        # remove first line which contains a date
        jstack -l ${pid} | tail -n +2 > jstack_${pid}_${i}.txt
    done
done

for pid in "$@"
do
    for ((i = 0 ; i <= ${NB_JSTACK} ; i++ ))
    do
#        echo "I=${i}"
        if [ ! -f "jstack_${pid}_${i}.txt" ]
        then
            continue
        fi

        for ((j = 0 ; j <= ${NB_JSTACK} ; j++ ))
        do
#            echo "J=${j}"
            if [ "${i}" -eq "${j}" ]
            then
                continue
            fi
#            echo "COMPARE  jstack_${pid}_${i}.txt  TO  jstack_${pid}_${j}.txt"

            if [ -f "jstack_${pid}_${j}.txt" ]
            then
                set +e
                diff jstack_${pid}_${i}.txt jstack_${pid}_${j}.txt > /dev/null 2>&1
                if [ "$?" -eq "0" ]
                then
#                    echo "         EQUAL"
                    # remove duplicate files
                    rm jstack_${pid}_${j}.txt
                fi
                set -e
            fi
        done
    done
done


#!/bin/bash -e

set -x

# change the separator to \n for breaking the results by line for the evaluations in the 'for' statements down below
IFS=$'\n'

mkdir dump_db_userRoot -p
for param in `java -cp lib/bootstrap.jar com.sleepycat.je.util.DbDump -h db/userRoot/ -l`
do
    java -cp lib/bootstrap.jar com.sleepycat.je.util.DbDump -h db/userRoot/ -s "$param" -f "dump_db_userRoot/$param.dump.txt"
done


mkdir dump_changelogDb -p
for param in `java -cp lib/bootstrap.jar com.sleepycat.je.util.DbDump -h changelogDb/ -l`
do
    java -cp lib/bootstrap.jar com.sleepycat.je.util.DbDump -h changelogDb/ -s "$param" -f "dump_changelogDb/$param.dump.txt"
done




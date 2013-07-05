#!/bin/bash -e

set -x
REPEAT=0
while [ "$REPEAT" -eq "0" ]
do 
	curl "http://bjensen:hifalutin@localhost:8080/users?_queryFilter=true&_prettyPrint=true" > output.txt && \
		grep '"resultCount" : 150' output.txt
	REPEAT=$?
done


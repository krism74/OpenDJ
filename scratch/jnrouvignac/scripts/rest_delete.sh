#!/bin/bash -e
curl  --request DELETE  "http://bjensen:hifalutin@localhost:8080/users/newuser?_prettyPrint=true"
echo $?

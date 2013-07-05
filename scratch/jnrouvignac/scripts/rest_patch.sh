#!/bin/bash -e
curl
 --user kvaughan:bribery
 --request PATCH
 --header "Content-Type: application/json"
 --data '[
  {
    "operation": "replace",
    "field": "/contactInformation/emailAddress",
    "value": "babs@example.com"
  }
 ]'
 http://opendj.example.com:8080/users/bjensen?_prettyPrint=true
echo $?


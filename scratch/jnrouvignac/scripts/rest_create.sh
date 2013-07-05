#!/bin/bash -e
curl \
 --request POST \
 --header "Content-Type: application/json" \
 --data '{
  "_id": "newuser",
  "contactInformation": {
    "telephoneNumber": "+1 408 555 1212",
    "emailAddress": "newuser@example.com"
  },
  "name": {
    "familyName": "New",
    "givenName": "User"
  },
  "displayName": "New User",
  "manager": [
    {
      "_id": "kvaughan",
      "displayName": "Kirsten Vaughan"
    }
  ]
 }' \
 "http://bjensen:hifalutin@localhost:8080/users?_action=create&_prettyPrint=true"
echo $?


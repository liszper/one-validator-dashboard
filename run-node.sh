#!/bin/bash
KEY=$(find . -maxdepth 1 -type f -iname "*.key" | head -1)
./node.sh -S -c -z -I -N staking -k $KEY --passphrase-file secret.key

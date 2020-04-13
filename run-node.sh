#!/bin/bash
cd ~
KEY=$(find . -maxdepth 1 -type f -iname "*.key" | head -1)
PASS=$(find . -maxdepth 1 -type f -iname "*.pass" | head -1)
./node.sh -S -c -z -I -N staking -k $KEY -p $PASS

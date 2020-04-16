#!/bin/bash
cd ~
FULL=$(find . -maxdepth 1 -type f -iname "*.key" | head -1)
BLS="$(basename -- $FULL)"
$BLS

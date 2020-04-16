#!/bin/bash
cd ~
find . -maxdepth 1 -type f -iname "*.key" | head -1

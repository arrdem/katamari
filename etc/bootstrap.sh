#!/bin/bash

# A helper script for developing Katamari - lets kat re-uberjar itself, backs the current jar up,
# tries to reboot into the new jar, and if that fails puts the old jar back.

jar=$(./kat -j compile me.arrdem/katamari+uberjar | jq -r '.["me.arrdem/katamari+uberjar"]["paths"][0]')
mv .kat.d/bootstrap.jar kat-backup.jar
cp $jar .kat.d/bootstrap.jar
./kat restart-server || (
    cp kat-backup.jar .kat.d/bootstrap.jar
    ./kat start-server
)

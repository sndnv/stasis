#!/usr/bin/env sh

read -p "Param1:" param1
echo "Received param1=${param1}"

if [ "$1" = "fail" ]; then
  echo "Command failed"
  exit 1
fi

read -p "Reading Param 2" param2
echo "Received param2=${param2}"

read -s -p "Secret1: " secret1
read -s -p "Reading Secret 2: " secret2

echo "Command succeeded with [param1=${param1},param2=${param2},secret1=${secret1},secret2=${secret2}]"

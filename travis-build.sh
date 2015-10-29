#!/bin/bash -e

./grailsw refresh-dependencies -non-interactive -plain-output
./grailsw test-app -non-interactive -plain-output

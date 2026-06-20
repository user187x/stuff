#!/bin/bash

( sleep 1; echo "getData" > /dev/ttyACM0 ) & cat -v < /dev/ttyACM0

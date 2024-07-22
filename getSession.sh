#!/bin/bash

# Navigate to the project directory and run the tests
cd "$(dirname "$0")" && mvn clean test

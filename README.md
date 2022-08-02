![Maven Build](https://github.com/samagra-comms/orchestrator/actions/workflows/build.yml/badge.svg)
![Docker Build](https://github.com/samagra-comms/orchestrator/actions/workflows/docker-build-push.yml/badge.svg)

# Overview
ODK transformer transforms the previous xMessage from the user to one that needs to be sent next. It is a microservice that returns a new xMessage based on the previous user action, which will then be shown to the user.

# Getting Started

## Prerequisites

* java 11 or above
* docker
* kafka
* postgresql
* redis
* fusion auth
* odk
* lombok plugin for IDE
* maven

## Build
* build with tests run using command **mvn clean install -U**
* or build without tests run using command **mvn clean install -DskipTests**

# Detailed Documentation
[Click here](https://uci.sunbird.org/use/developer/uci-basics)
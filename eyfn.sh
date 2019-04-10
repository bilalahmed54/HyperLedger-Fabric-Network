#!/bin/bash
#
# Copyright IBM Corp All Rights Reserved
#
# SPDX-License-Identifier: Apache-2.0
#

# This script extends the Hyperledger Fabric By Your First Network by
# adding a third organization to the network previously setup in the
# BYFN tutorial.
#

# prepending $PWD/../bin to PATH to ensure we are picking up the correct binaries
# this may be commented out to resolve installed version of tools if desired
export PATH=${PWD}/../bin:${PWD}:$PATH
export FABRIC_CFG_PATH=${PWD}
export VERBOSE=false

# Print the usage message
function printHelp () {
  echo "Usage: "
  echo "  eyfn.sh up|down|restart|generate [-c <channel name>] [-t <timeout>] [-d <delay>] [-f <docker-compose-file>] [-s <dbtype>]"
  echo "  eyfn.sh -h|--help (print this message)"
  echo "    <mode> - one of 'up', 'down', 'restart' or 'generate'"
  echo "      - 'up' - bring up the network with docker-compose up"
  echo "      - 'down' - clear the network with docker-compose down"
  echo "      - 'restart' - restart the network"
  echo "      - 'generate' - generate required certificates and genesis block"
  echo "    -c <channel name> - channel name to use (defaults to \"mychannel\")"
  echo "    -t <timeout> - CLI timeout duration in seconds (defaults to 10)"
  echo "    -d <delay> - delay duration in seconds (defaults to 3)"
  echo "    -f <docker-compose-file> - specify which docker-compose file use (defaults to docker-compose-cli.yaml)"
  echo "    -s <dbtype> - the database backend to use: goleveldb (default) or couchdb"
  echo "    -l <language> - the chaincode language: golang (default) or node"
  echo "    -i <imagetag> - the tag to be used to launch the network (defaults to \"latest\")"
  echo "    -v - verbose mode"
  echo
  echo "Typically, one would first generate the required certificates and "
  echo "genesis block, then bring up the network. e.g.:"
  echo
  echo "	eyfn.sh generate -c mychannel"
  echo "	eyfn.sh up -c mychannel -s couchdb"
  echo "	eyfn.sh up -l node"
  echo "	eyfn.sh down -c mychannel"
  echo
  echo "Taking all defaults:"
  echo "	eyfn.sh generate"
  echo "	eyfn.sh up"
  echo "	eyfn.sh down"
}

# Obtain CONTAINER_IDS and remove them
# TODO Might want to make this optional - could clear other containers
function clearContainers () {
  CONTAINER_IDS=$(docker ps -aq)
  if [ -z "$CONTAINER_IDS" -o "$CONTAINER_IDS" == " " ]; then
    echo "---- No containers available for deletion ----"
  else
    docker rm -f $CONTAINER_IDS
  fi
}

# Delete any images that were generated as a part of this setup
# specifically the following images are often left behind:
# TODO list generated image naming patterns
function removeUnwantedImages() {
  DOCKER_IMAGE_IDS=$(docker images|awk '($1 ~ /dev-peer.*.mycc.*/) {print $3}')
  if [ -z "$DOCKER_IMAGE_IDS" -o "$DOCKER_IMAGE_IDS" == " " ]; then
    echo "---- No images available for deletion ----"
  else
    docker rmi -f $DOCKER_IMAGE_IDS
  fi
}

# Generate the needed certificates, the genesis block and start the network.
function networkUp () {

  # generate artifacts if they don't exist
  if [ ! -d "newOrganisation/crypto-config" ]; then
    generateCerts
    replacePrivateKey
    generateChannelArtifacts
    createConfigTx
  fi

  # start telco3 peers
  IMAGE_TAG=${IMAGETAG} docker-compose -f $COMPOSE_FILE_TELCO3 up -d 2>&1

  if [ $? -ne 0 ]; then
    echo "ERROR !!!! Unable to start Telco3 network"
    exit 1
  fi

  echo
  echo "###############################################################"
  echo "############### Have Telco3 peers join network ##################"
  echo "###############################################################"

  docker exec Telco3cli ./scripts/step2Telco3.sh $CHANNEL_NAME $CLI_DELAY $LANGUAGE $CLI_TIMEOUT $VERBOSE
  if [ $? -ne 0 ]; then
    echo "ERROR !!!! Unable to have Telco3 peers join network"
    exit 1
  fi

  # echo
  # echo "###############################################################"
  # echo "##### Upgrade chaincode to have Telco3 peers on the network #####"
  # echo "###############################################################"
  # docker exec cli ./scripts/step3Telco3.sh $CHANNEL_NAME $CLI_DELAY $LANGUAGE $CLI_TIMEOUT $VERBOSE
  # if [ $? -ne 0 ]; then
    # echo "ERROR !!!! Unable to add Telco3 peers on network"
    # exit 1
  # fi

  # finish by running the test
  # docker exec Telco3cli ./scripts/testTelco3.sh $CHANNEL_NAME $CLI_DELAY $LANGUAGE $CLI_TIMEOUT $VERBOSE
  # if [ $? -ne 0 ]; then
  #   echo "ERROR !!!! Unable to run test"
  #   exit 1
  # fi

  echo
  echo "========= All GOOD, EYFN test execution completed =========== "
  echo

  echo
  echo " _____   _   _   ____   "
  echo "| ____| | \ | | |  _ \  "
  echo "|  _|   |  \| | | | | | "
  echo "| |___  | |\  | | |_| | "
  echo "|_____| |_| \_| |____/  "
  echo
}

# Tear down running network
function networkDown () {
  docker-compose -f $COMPOSE_FILE_TELCO3 down --volumes
  # Don't remove containers, images, etc if restarting
  # if [ "$MODE" != "restart" ]; then
    #Cleanup the chaincode containers
    # clearContainers
    #Cleanup images
    # removeUnwantedImages
    # remove orderer block and other channel configuration transactions and certs
    # rm -rf channel-artifacts/*.block channel-artifacts/*.tx crypto-config ./newOrganisation/crypto-config/ channel-artifacts/telco3.json
    # remove the docker-compose yaml file that was customized to the example
    # rm -f docker-compose-telco3-ca.yaml
  # fi
}

# Use the CLI container to create the configuration transaction needed to add
# Telco3 to the network
function createConfigTx () {
  echo
  echo "###############################################################"
  echo "####### Generate and submit config tx to add Telco3 #############"
  echo "###############################################################"
  docker exec cli scripts/step1Telco3.sh $CHANNEL_NAME $CLI_DELAY $LANGUAGE $CLI_TIMEOUT $VERBOSE
  if [ $? -ne 0 ]; then
    echo "ERROR !!!! Unable to create config tx"
    exit 1
  fi
}

# We use the cryptogen tool to generate the cryptographic material
# (x509 certs) for the new telco.  After we run the tool, the certs will
# be parked in the BYFN folder titled ``crypto-config``.

# Generates Telco3 certs using cryptogen tool
function generateCerts (){
  which cryptogen
  if [ "$?" -ne 0 ]; then
    echo "cryptogen tool not found. exiting"
    exit 1
  fi
  echo
  echo "###############################################################"
  echo "##### Generate Telco3 certificates using cryptogen tool #########"
  echo "###############################################################"

  (cd newOrganisation
   set -x
   cryptogen generate --config=./telco3-crypto.yaml
   res=$?
   set +x
   if [ $res -ne 0 ]; then
     echo "Failed to generate certificates..."
     exit 1
   fi
  )
  echo
}

# Using docker-compose-telco3-ca-template.yaml, replace constants with private key file names
# generated by the cryptogen tool and output a docker-compose-telco3-ca.yaml specific to this
# configuration
function replacePrivateKey() {

  # sed on MacOSX does not support -i flag with a null extension. We will use
  # 't' for our back-up's extension and delete it at the end of the function
  ARCH=$(uname -s | grep Darwin)
  if [ "$ARCH" == "Darwin" ]; then
    OPTS="-it"
  else
    OPTS="-i"
  fi

  # Copy the template to the file that will be modified to add the private key
  cp docker-compose-telco3-ca-template.yaml docker-compose-telco3-ca.yaml

  # The next steps will replace the template's contents with the
  # actual values of the private key file names for the two CAs.
  CURRENT_DIR=$PWD

  cd newOrganisation/crypto-config/peerOrganizations/telco3.vodworks.com/ca/
  PRIV_KEY=$(ls *_sk)
  cd "$CURRENT_DIR"
  sed $OPTS "s/CA3_PRIVATE_KEY/${PRIV_KEY}/g" docker-compose-telco3-ca.yaml
  
  # If MacOSX, remove the temporary backup of the docker-compose file
  if [ "$ARCH" == "Darwin" ]; then
    rm docker-compose-telco3-ca.yamlt
  fi

  echo "Replaced Private Keys!!!"
}

# Generate channel configuration transaction
function generateChannelArtifacts() {
  which configtxgen
  if [ "$?" -ne 0 ]; then
    echo "configtxgen tool not found. exiting"
    exit 1
  fi
  echo "##########################################################"
  echo "#########  Generating Telco3 config material ###############"
  echo "##########################################################"
  (cd newOrganisation
   export FABRIC_CFG_PATH=$PWD
   set -x
   configtxgen -printOrg Telco3MSP > ../channel-artifacts/telco3.json
   res=$?
   set +x
   if [ $res -ne 0 ]; then
     echo "Failed to generate Telco3 config material..."
     exit 1
   fi
  )
  cp -r crypto-config/ordererOrganizations newOrganisation/crypto-config/
  echo
}


# If BYFN wasn't run abort
if [ ! -d crypto-config ]; then
  echo
  echo "ERROR: Please, run byfn.sh first."
  echo
  exit 1
fi

# Obtain the OS and Architecture string that will be used to select the correct
# native binaries for your platform
OS_ARCH=$(echo "$(uname -s|tr '[:upper:]' '[:lower:]'|sed 's/mingw64_nt.*/windows/')-$(uname -m | sed 's/x86_64/amd64/g')" | awk '{print tolower($0)}')
# timeout duration - the duration the CLI should wait for a response from
# another container before giving up
CLI_TIMEOUT=10
#default for delay
CLI_DELAY=3
# channel name defaults to "mychannel"
CHANNEL_NAME="mychannel"
# use this as the default docker-compose yaml definition
COMPOSE_FILE=docker-compose-cli.yaml
#
COMPOSE_FILE_COUCH=docker-compose-couch.yaml
# use this as the default docker-compose yaml definition
COMPOSE_FILE_TELCO3=docker-compose-telco3.yaml
#
# COMPOSE_FILE_COUCH_TELCO3=docker-compose-couch-telco3.yaml
# kafka and zookeeper compose file
COMPOSE_FILE_KAFKA=docker-compose-kafka.yaml
# use golang as the default language for chaincode
LANGUAGE=golang
# default image tag
IMAGETAG="latest"

# Parse commandline args
if [ "$1" = "-m" ];then	# supports old usage, muscle memory is powerful!
    shift
fi
MODE=$1;shift
# Determine whether starting, stopping, restarting or generating for announce
if [ "$MODE" == "up" ]; then
  EXPMODE="Starting"
elif [ "$MODE" == "down" ]; then
  EXPMODE="Stopping"
elif [ "$MODE" == "restart" ]; then
  EXPMODE="Restarting"
elif [ "$MODE" == "generate" ]; then
  EXPMODE="Generating certs and genesis block for"
else
  printHelp
  exit 1
fi
while getopts "h?c:t:d:f:s:l:i:v" opt; do
  case "$opt" in
    h|\?)
      printHelp
      exit 0
    ;;
    c)  CHANNEL_NAME=$OPTARG
    ;;
    t)  CLI_TIMEOUT=$OPTARG
    ;;
    d)  CLI_DELAY=$OPTARG
    ;;
    f)  COMPOSE_FILE=$OPTARG
    ;;
    s)  IF_COUCHDB=$OPTARG
    ;;
    l)  LANGUAGE=$OPTARG
    ;;
    i)  IMAGETAG=$OPTARG
    ;;
    v)  VERBOSE=true
    ;;
  esac
done

# Announce what was requested

  if [ "${IF_COUCHDB}" == "couchdb" ]; then
        echo
        echo "${EXPMODE} with channel '${CHANNEL_NAME}' and CLI timeout of '${CLI_TIMEOUT}' seconds and CLI delay of '${CLI_DELAY}' seconds and using database '${IF_COUCHDB}'"
  else
        echo "${EXPMODE} with channel '${CHANNEL_NAME}' and CLI timeout of '${CLI_TIMEOUT}' seconds and CLI delay of '${CLI_DELAY}' seconds"
  fi

#Create the network using docker compose
if [ "${MODE}" == "up" ]; then
  networkUp
elif [ "${MODE}" == "down" ]; then ## Clear the network
  networkDown
elif [ "${MODE}" == "generate" ]; then ## Generate Artifacts
  generateCerts
  generateChannelArtifacts
  createConfigTx
elif [ "${MODE}" == "restart" ]; then ## Restart the network
  networkDown
  networkUp
else
  printHelp
  exit 1
fi
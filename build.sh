#!/bin/bash

function check_binary_present() {
  ANY_PATH=$(whereis "$1" | cut -d ' ' -f 2)
  if [ -z "$ANY_PATH" ]; then
    echo "Required executable $1 is not installed."
    exit 1
  else
    echo "Required executable $1 is installed..."
  fi
}

check_binary_present "git"
check_binary_present "npm"
check_binary_present "node"
check_binary_present "java"

PROJECT_ROOT=${1-$PWD}
echo "Project directory is $PROJECT_ROOT"
PROJECT_NAME=$(./gradlew properties -q | grep "^name:" | awk '{print $2}')
PROJECT_VERSION=$(./gradlew properties -q | grep "^version:" | awk '{print $2}')
CLIENT_SUBMODULE_PATH="$PROJECT_ROOT/audiodragon-client"

echo "Building $PROJECT_NAME - version $PROJECT_VERSION"

echo "1. Checking out submodule"
git submodule update --recursive

echo "2. Building client"
cd "$CLIENT_SUBMODULE_PATH"
npm install
npm run build
cd -
rm package-lock.json

echo "3. Copying client"
CLIENT_DIST_PATH="$CLIENT_SUBMODULE_PATH/dist"
CLIENT_RESOURCES_PATH="$PROJECT_ROOT/src/main/resources/static"
if [ -d "$CLIENT_DIST_PATH" ]; then
  echo "Copying client distribution files from $CLIENT_DIST_PATH to $CLIENT_RESOURCES_PATH"
  cp -R "$CLIENT_DIST_PATH/." "$CLIENT_RESOURCES_PATH"
else
  echo "Distribution folder $CLIENT_DIST_PATH does not exist."
  exit 1
fi

echo "4. Building application"
cd "$PROJECT_ROOT"
./gradlew clean build


ARTIFACT_PATH="$PROJECT_ROOT/build/libs/$PROJECT_NAME-$PROJECT_VERSION.jar"
if [ -f "$ARTIFACT_PATH" ]; then
  echo "Artifact is located at $ARTIFACT_PATH"
  echo "Done"
else
  echo "Could not find artifact at expected location $ARTIFACT_PATH"
  exit 1
fi
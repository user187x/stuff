#!/bin/bash

CONTAINER="k9s_container"
IMAGE="derailed/k9s"
DESTINATION="$HOME/.local/bin"

docker create --name "${CONTAINER}" "${IMAGE}"
docker cp "${CONTAINER}":bin/k9s "${DESTINATION}"
docker rm "${CONTAINER}"

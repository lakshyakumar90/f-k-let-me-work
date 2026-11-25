#!/usr/bin/env bash
set -euo pipefail

REPO="lakshyakumar90/fuck-let-me-work"
BIN_NAME="flmw"
INSTALL_DIR="/usr/local/bin"

echo "Building ${BIN_NAME} for $(uname -s)/$(uname -m)..."
if ! command -v go >/dev/null 2>&1; then
  echo "Error: Go 1.23+ is required to build from source."
  exit 1
fi

go mod tidy
go build -ldflags="-s -w" -o "${BIN_NAME}" ./cmd/flmw

echo "Installing binary to ${INSTALL_DIR}/${BIN_NAME} (requires sudo)..."
sudo install -m 0755 "${BIN_NAME}" "${INSTALL_DIR}/${BIN_NAME}"

echo "Registering background daemon service..."
sudo "${INSTALL_DIR}/${BIN_NAME}" install

echo "Done! Run 'flmw open' or 'flmw on 25m' to start focusing."

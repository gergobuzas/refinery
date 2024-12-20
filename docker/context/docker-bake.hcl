# SPDX-FileCopyrightText: 2024 The Refinery Authors <https://refinery.tools/>
#
# SPDX-License-Identifier: EPL-2.0

variable "REFINERY_VERSION" {
  default = ""
}

variable "REFINERY_PUSH" {
  default = "false"
}

group "default" {
  targets = ["cli", "web", "generator"]
}

target "base" {
  dockerfile = "Dockerfile.base"
  platforms = ["linux/amd64", "linux/arm64"]
  output = ["type=cacheonly"]
}

target "cli" {
  dockerfile = "Dockerfile.cli"
  platforms = ["linux/amd64", "linux/arm64"]
  output = [
    "type=image,push=${REFINERY_PUSH},\"name=gergobuzas/refinery-cli:${REFINERY_VERSION},gergobuzas/refinery-cli:latest\",annotation-index.org.opencontainers.image.source=https://github.com/gergobuzas/refinery,\"annotation-index.org.opencontainers.image.description=Command line interface for Refinery, an efficient graph solver for generating well-formed models\",annotation-index.org.opencontainers.image.licenses=EPL-2.0"
  ]
  contexts = {
    base = "target:base"
  }
}

target "web" {
  dockerfile = "Dockerfile.web"
  platforms = ["linux/amd64", "linux/arm64"]
  output = [
    "type=image,push=${REFINERY_PUSH},\"name=gergobuzas/refinery-language-web:${REFINERY_VERSION},gergobuzas/refinery-language-web:latest\",annotation-index.org.opencontainers.image.source=https://github.com/gergobuzas/refinery,annotation-index.org.opencontainers.image.description=Refinery: an efficient graph solver for generating well-formed models,annotation-index.org.opencontainers.image.licenses=EPL-2.0"
  ]
  contexts = {
    base = "target:base"
  }
}

target "generator" {
  dockerfile = "Dockerfile.generator"
  platforms = ["linux/amd64", "linux/arm64"]
  output = [
    "type=image,push=${REFINERY_PUSH},\"name=gergobuzas/refinery-generator:${REFINERY_VERSION},gergobuzas/refinery-generator:latest\",annotation-index.org.opencontainers.image.source=https://github.com/gergobuzas/refinery,\"annotation-index.org.opencontainers.image.description=Refinery-generator: A Jetty WebSocket server, which generates the model based on received problem\",annotation-index.org.opencontainers.image.licenses=EPL-2.0"
  ]
  contexts = {
    base = "target:base"
  }
}

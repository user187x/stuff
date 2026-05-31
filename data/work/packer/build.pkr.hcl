packer {
  required_plugins {
    docker = {
      version = ">= 1.0.8"
      source  = "github.com/hashicorp/docker"
    }
  }
}

source "docker" "my_custom_image" {
  image  = "ubuntu:22.04"
  commit = true
}

build {
  name = "custom-image-build"
  sources = [
    "source.docker.my_custom_image"
  ]

  provisioner "shell" {
    script = "./provision.sh"
  }

  post-processor "docker-tag" {
    repository = "my-custom-image"
    tags       = ["latest"]
  }
}

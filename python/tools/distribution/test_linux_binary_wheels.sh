#!/bin/bash
# Copyright 2020 Google LLC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
################################################################################


set -euo pipefail

# The following assoicative array contains:
#   ["<Python version>"]="<python tag>-<abi tag>"
# where:
#   <Python version> = language version, e.g "3.8"
#   <python tag>, <abi tag> = as defined at
#       https://packaging.python.org/en/latest/specifications/, e.g. "cp38-cp38"
declare -A PYTHON_VERSIONS
PYTHON_VERSIONS["3.8"]="cp38-cp38"
PYTHON_VERSIONS["3.9"]="cp39-cp39"
PYTHON_VERSIONS["3.10"]="cp310-cp310"
PYTHON_VERSIONS["3.11"]="cp311-cp311"
readonly -A PYTHON_VERSIONS

readonly ARCH="$(uname -m)"

# This is a compressed tag set as specified at
# https://peps.python.org/pep-0425/#compressed-tag-sets
#
# Keep in sync with the output of the auditwheel tool.
PLATFORM_TAG_SET="manylinux_2_17_x86_64.manylinux2014_x86_64"
if [[ "${ARCH}" == "aarch64" || "${ARCH}" == "arm64" ]]; then
  PLATFORM_TAG_SET="manylinux_2_17_aarch64.manylinux2014_aarch64"
fi
readonly PLATFORM_TAG_SET

export TINK_PYTHON_ROOT_PATH="${PWD}"

# Required to fix https://github.com/pypa/manylinux/issues/357.
export LD_LIBRARY_PATH="/usr/local/lib"

# This link is required on CentOS, as curl used in the AWS SDK looks for the
# certificates in this location. Removing this line will cause the AWS KMS tests
# to fail.
ln -s /etc/ssl/certs/ca-bundle.trust.crt /etc/ssl/certs/ca-certificates.crt

TEST_IGNORE_PATHS=( -not -path "*cc/pybind*")
if [[ "${ARCH}" == "aarch64" || "${ARCH}" == "arm64" ]]; then
  # gRPC doesn't seem compatible with libstdc++ present in
  # manylinux2014_aarch64 (see https://github.com/grpc/grpc/issues/33734).
  # TODO(b/291055539): Re-enable these tests when/after this is solved.
  TEST_IGNORE_PATHS+=( -not -path "*integration/gcpkms*")
fi
readonly TEST_IGNORE_PATHS

for v in "${!PYTHON_VERSIONS[@]}"; do
  (
    # Executing in a subshell to make the PATH modification temporary.
    export PATH="${PATH}:/opt/python/${PYTHON_VERSIONS[$v]}/bin"
    python3 -m pip install --require-hashes --no-deps -r requirements_all.txt
    WHEEL="$(echo release/*-"${PYTHON_VERSIONS[$v]}"-"${PLATFORM_TAG_SET}".whl)"
    python3 -m pip install --no-deps --no-index "${WHEEL}[all]"
    find tink/ "${TEST_IGNORE_PATHS[@]}" -type f -name "*_test.py" -print0 \
      | xargs -0 -n1 python3
  )
done

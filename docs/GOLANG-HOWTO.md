# Tink for Go HOW-TO

This document contains instructions for common tasks in
[Tink](https://github.com/google/tink). Example code snippets for these tasks
and API documentation can be found on
[pkg.go.dev](https://pkg.go.dev/github.com/google/tink/go).

## Setup instructions

To install Tink locally run:

```sh
go get github.com/google/tink/go/...
```

to run all the tests locally:

```sh
cd $GOPATH/go/src/github.com/google/tink/go
go test ./...
```

Golang Tink API also supports [Bazel](https://www.bazel.build) builds. To run
the tests using bazel:

```sh
cd $GOPATH/go/src/github.com/google/tink/go
bazel build ... && bazel test ...
```

## Generating new keys and keysets

To take advantage of key rotation and other key management features, you usually
do not work with single keys, but with keysets. Keysets are just sets of keys
with some additional parameters and metadata.

Internally Tink stores keysets as Protocol Buffers, but you can work with
keysets via a wrapper called a keyset handle. You can generate a new keyset and
obtain its handle using a KeyTemplate. KeysetHandle objects enforce certain
restrictions that prevent accidental leakage of the sensitive key material.

```go
package main

import (
  "fmt"
  "log"

  "github.com/google/tink/go/aead"
  "github.com/google/tink/go/keyset"
)

func main() {
  // Other key templates can also be used.
  kh, err := keyset.NewHandle(aead.AES128GCMKeyTemplate())
  if err != nil {
    log.Fatal(err)
  }

  fmt.Println(kh.String())
}

```

Key templates are available for MAC, digital signatures, AEAD encryption, DAEAD
encryption and hybrid encryption.

Key Template Type | Key Template
----------------- | ------------------------------------------------
AEAD              | aead.AES128CTRHMACSHA256KeyTemplate()
AEAD              | aead.AES128GCMKeyTemplate()
AEAD              | aead.AES256CTRHMACSHA256KeyTemplate()
AEAD              | aead.AES256GCMKeyTemplate()
AEAD              | aead.ChaCha20Poly1305KeyTemplate()
AEAD              | aead.XChaCha20Poly1305KeyTemplate()
DAEAD             | daead.AESSIVKeyTemplate()
MAC               | mac.HMACSHA256Tag128KeyTemplate()
MAC               | mac.HMACSHA256Tag256KeyTemplate()
MAC               | mac.HMACSHA512Tag256KeyTemplate()
MAC               | mac.HMACSHA512Tag512KeyTemplate()
Signature         | signature.ECDSAP256KeyTemplate()
Signature         | signature.ECDSAP384KeyTemplate()
Signature         | signature.ECDSAP521KeyTemplate()
Hybrid            | hybrid.ECIESHKDFAES128GCMKeyTemplate()
Hybrid            | hybrid.ECIESHKDFAES128CTRHMACSHA256KeyTemplate()

To avoid accidental leakage of sensitive key material, you should avoid mixing
keyset generation and usage in code. To support the separation of these
activities Tink provides a command-line tool, [Tinkey](TINKEY.md), which can be
used for common key management tasks.

## Storing and loading existing keysets

After generating key material, you might want to persist it to a storage system.
Tink supports encrypting and persisting the keys to any io.Writer and io.Reader
implementations.

```go
package main

import (
  "fmt"
  "log"

  "github.com/google/tink/go/aead"
  "github.com/google/tink/go/core/registry"
  "github.com/google/tink/go/integration/gcpkms"
  "github.com/google/tink/go/keyset"
)

const (
  // Change this. AWS KMS, Google Cloud KMS and HashiCorp Vault are supported out of the box.
   keyURI = "gcp-kms://projects/tink-examples/locations/global/keyRings/foo/cryptoKeys/bar"
  credentialsPath = "credentials.json"
)

func main() {
  // Generate a new keyset handle.
  handle1, err := keyset.NewHandle(aead.AES128GCMKeyTemplate())
  if err != nil {
    log.Fatal(err)
  }

  // Get the key encryption AEAD from a KMS.
  gcpClient, err := gcpkms.NewClientWithCredentials(keyURI, credentialsPath)
  if err != nil {
    log.Fatal(err)
  }
  registry.RegisterKMSClient(gcpClient)
  keyEncryptionAEAD, err := gcpClient.GetAEAD(keyURI)
  if err != nil {
    log.Fatal(err)
  }

  // Serialize and encrypt the keyset handle using the key encryption AEAD.
  // We strongly recommend that you encrypt the keyset handle before persisting
  // it.
  buf := new(bytes.Buffer)
  writer := keyset.NewBinaryWriter(buf)
  err = handle1.Write(writer, keyEncryptionAEAD)
  if err != nil {
    log.Fatal(err)
  }
  encryptedHandle := buf.Bytes()

  // Decrypt and parse the encrypted keyset using the key encryption AEAD.
  reader := keyset.NewBinaryReader(bytes.NewReader(encryptedHandle))
  handle2, err := keyset.Read(reader, keyEncryptionAEAD)
  if err != nil {
    log.Fatal(err)
  }
}
```

## AEAD

The AEAD primitive (authenticated encryption with associated data) is the most
common primitive to ***encrypt*** data. It is symmetric, and using the same key
for encryption and decryption.

Check out the
[AEAD examples](https://pkg.go.dev/github.com/google/tink/go/aead#example-package).
The `Play` button at the corner right allows you to run them on the Go
Playground.

## Deterministic AEAD

The Deterministic AEAD primitive (authenticated encryption with associated data)
is used to ***deterministically encrypt*** data. It is symmetric, and using the
same key for encryption and decryption.

Unlike AEAD, implementations of this interface are not semantically secure,
because encrypting the same plaintext always yields the same ciphertext.

Check out the
[Deterministic AEAD examples](https://pkg.go.dev/github.com/google/tink/go/daead#example-package).
The `Play` button at the corner right allows you to run them on the Go
Playground.

## MAC

The MAC primitive allows you to ensure that nobody tampers with data you own. It
is symmetric, and using the same key for authentication and verification.

Check out the
[MAC examples](https://pkg.go.dev/github.com/google/tink/go/mac#example-package).
The `Play` button at the corner right allows you to run them on the Go
Playground.

## Digital signature

The digital signature primitives allow you to ensure that nobody tampers with
your data. It is asymmetric, and hence comes with a pair of keys (public key and
private key). The private key allows to sign messages, and the public key allows
to verify.

Check out the
[digital signature examples](https://pkg.go.dev/github.com/google/tink/go/signature#example-package).
The `Play` button at the corner right allows you to run them on the Go
Playground.

## Hybrid encryption

The hybrid encryption primitives allow you to encrypt data with a public key.
Only users with the secret key will be able to decrypt the data.

Check out the
[hybrid encryption examples](https://pkg.go.dev/github.com/google/tink/go/hybrid#example-package).
The `Play` button at the corner right allows you to run them on the Go
Playground.

## Envelope encryption

Via the AEAD interface, Tink supports
[envelope encryption](KEY-MANAGEMENT.md#envelope-encryption).

Check out the
[GCP KMS example](https://pkg.go.dev/github.com/tink-crypto/tink-go-gcpkms/v2@v2.0.0/integration/gcpkms#example-package).

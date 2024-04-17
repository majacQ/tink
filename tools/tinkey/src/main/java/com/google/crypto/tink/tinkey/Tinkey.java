// Copyright 2017 Google Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
////////////////////////////////////////////////////////////////////////////////

package com.google.crypto.tink.tinkey;

import com.google.crypto.tink.daead.DeterministicAeadConfig;
import com.google.crypto.tink.hybrid.HybridConfig;
import com.google.crypto.tink.integration.awskms.AwsKmsClient;
import com.google.crypto.tink.integration.gcpkms.GcpKmsClient;
import com.google.crypto.tink.jwt.JwtMacConfig;
import com.google.crypto.tink.jwt.JwtSignatureConfig;
import com.google.crypto.tink.keyderivation.KeyDerivationConfig;
import com.google.crypto.tink.prf.PrfConfig;
import com.google.crypto.tink.signature.SignatureConfig;
import com.google.crypto.tink.streamingaead.StreamingAeadConfig;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;

/**
 * Tinkey is a command-line tool to manage keys for Tink.
 */
public final class Tinkey {
  public static void main(String[] args) throws Exception {
    DeterministicAeadConfig.register();
    HybridConfig.register(); // includes Aead and Mac
    PrfConfig.register();
    SignatureConfig.register();
    StreamingAeadConfig.register();
    JwtSignatureConfig.register();
    JwtMacConfig.register();
    KeyDerivationConfig.register();
    // place holder for Internal Prps. DO NOT EDIT.

    KmsClientsFactory.globalInstance().addFactory(AwsKmsClient::new);
    KmsClientsFactory.globalInstance().addFactory(GcpKmsClient::new);

    TinkeyCommands commands = new TinkeyCommands();
    CmdLineParser parser = new CmdLineParser(commands);

    try {
      parser.parseArgument(args);
    } catch (CmdLineException e) {
      System.out.println("Argument wrong!");
      System.out.println(e);
      System.out.println();
      e.getParser().printUsage(System.out);
      System.exit(1);
    }
    commands.command.run();
  }

  private Tinkey() {}
}

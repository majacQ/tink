// Copyright 2021 Google LLC
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

package com.google.crypto.tink.jwt;

import com.google.crypto.tink.internal.JsonParser;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.io.IOException;

/**
 * Helper functions to parse JSON strings, and validate strings.
 * */
final class JsonUtil {

  static boolean isValidString(String s) {
    return JsonParser.isValidString(s);
  }


  static JsonObject parseJson(String jsonString) throws JwtInvalidException {
    try {
      return JsonParser.parse(jsonString).getAsJsonObject();
    } catch (IllegalStateException | JsonParseException  | IOException ex) {
      throw new JwtInvalidException("invalid JSON: " + ex);
    }
  }

  static JsonArray parseJsonArray(String jsonString) throws JwtInvalidException {
    try {
      return JsonParser.parse(jsonString).getAsJsonArray();
    } catch (IllegalStateException | JsonParseException | IOException ex) {
      throw new JwtInvalidException("invalid JSON: " + ex);
    }
  }

  private JsonUtil() {}
}

/*
 * Copyright 2011 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.crypto.tink.internal;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.io.NotSerializableException;
import java.io.ObjectInputStream;
import java.io.StringReader;
import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.Deque;
import javax.annotation.Nullable;

/**
 * A JSON Parser based on the GSON JsonReader.
 *
 * <p>The parsing is almost identical to the normal parser provided by GSON with these changes: it
 * never uses "lenient" mode, it rejects duplicated map keys and it rejects strings with invalid
 * UTF16 characters.
 *
 * <p>The implementation is adapted from almost identical to GSON's TypeAdapters.JSON_ELEMENT.
 */
public final class JsonParser {

  public static boolean isValidString(String s) {
    int length = s.length();
    int i = 0;
    while (true) {
      char ch;
      do {
        if (i == length) {
          return true;
        }
        ch = s.charAt(i);
        i++;
      } while (!Character.isSurrogate(ch));
      if (Character.isLowSurrogate(ch) || i == length || !Character.isLowSurrogate(s.charAt(i))) {
        return false;
      }
      i++;
    }
  }

  /** This is a modified copy of Gson's internal LazilyParsedNumber. */
  @SuppressWarnings("serial") // Serialization is not supported. Throws NotSerializableException
  private static final class LazilyParsedNumber extends Number {
    private final String value;

    public LazilyParsedNumber(String value) {
      this.value = value;
    }

    @Override
    public int intValue() {
      try {
        return Integer.parseInt(value);
      } catch (NumberFormatException e) {
        try {
          return (int) Long.parseLong(value);
        } catch (NumberFormatException nfe) {
          return new BigDecimal(value).intValue();
        }
      }
    }

    @Override
    public long longValue() {
      try {
        return Long.parseLong(value);
      } catch (NumberFormatException e) {
        return new BigDecimal(value).longValue();
      }
    }

    @Override
    public float floatValue() {
      return Float.parseFloat(value);
    }

    @Override
    public double doubleValue() {
      return Double.parseDouble(value);
    }

    @Override
    public String toString() {
      return value;
    }

    private Object writeReplace() throws NotSerializableException {
      throw new NotSerializableException("serialization is not supported");
    }

    private void readObject(ObjectInputStream in) throws NotSerializableException {
      throw new NotSerializableException("serialization is not supported");
    }

    @Override
    public int hashCode() {
      return value.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if (obj instanceof LazilyParsedNumber) {
        LazilyParsedNumber other = (LazilyParsedNumber) obj;
        return value.equals(other.value);
      }
      return false;
    }
  }

  private static final class JsonElementTypeAdapter extends TypeAdapter<JsonElement> {

    private static final int RECURSION_LIMIT = 100;

    /**
     * Tries to begin reading a JSON array or JSON object, returning {@code null} if the next
     * element is neither of those.
     */
    @Nullable
    private JsonElement tryBeginNesting(JsonReader in, JsonToken peeked) throws IOException {
      switch (peeked) {
        case BEGIN_ARRAY:
          in.beginArray();
          return new JsonArray();
        case BEGIN_OBJECT:
          in.beginObject();
          return new JsonObject();
        default:
          return null;
      }
    }

    /** Reads a {@link JsonElement} which cannot have any nested elements */
    private JsonElement readTerminal(JsonReader in, JsonToken peeked) throws IOException {
      switch (peeked) {
        case STRING:
          String value = in.nextString();
          if (!isValidString(value)) {
            throw new IOException("illegal characters in string");
          }
          return new JsonPrimitive(value);
        case NUMBER:
          String number = in.nextString();
          return new JsonPrimitive(new LazilyParsedNumber(number));
        case BOOLEAN:
          return new JsonPrimitive(in.nextBoolean());
        case NULL:
          in.nextNull();
          return JsonNull.INSTANCE;
        default:
          // When read(JsonReader) is called with JsonReader in invalid state
          throw new IllegalStateException("Unexpected token: " + peeked);
      }
    }

    @Override
    public JsonElement read(JsonReader in) throws IOException {
      // Either JsonArray or JsonObject
      JsonElement current;
      JsonToken peeked = in.peek();

      current = tryBeginNesting(in, peeked);
      if (current == null) {
        return readTerminal(in, peeked);
      }

      Deque<JsonElement> stack = new ArrayDeque<>();

      while (true) {
        while (in.hasNext()) {
          String name = null;
          // Name is only used for JSON object members
          if (current instanceof JsonObject) {
            name = in.nextName();
            if (!isValidString(name)) {
              throw new IOException("illegal characters in string");
            }
          }

          peeked = in.peek();
          JsonElement value = tryBeginNesting(in, peeked);
          boolean isNesting = value != null;

          if (value == null) {
            value = readTerminal(in, peeked);
          }

          if (current instanceof JsonArray) {
            ((JsonArray) current).add(value);
          } else {
            if (((JsonObject) current).has(name)) {
              throw new IOException("duplicate key: " + name);
            }
            ((JsonObject) current).add(name, value);
          }

          if (isNesting) {
            stack.addLast(current);
            if (stack.size() > RECURSION_LIMIT) {
               throw new IOException("too many recursions");
            }
            current = value;
          }
        }

        // End current element
        if (current instanceof JsonArray) {
          in.endArray();
        } else {
          in.endObject();
        }

        if (stack.isEmpty()) {
          return current;
        } else {
          // Continue with enclosing element
          current = stack.removeLast();
        }
      }
    }

    @Override
    public void write(JsonWriter out, JsonElement value) {
      throw new UnsupportedOperationException("write is not supported");
    }
  }

  private static final JsonElementTypeAdapter JSON_ELEMENT = new JsonElementTypeAdapter();

  public static JsonElement parse(String json) throws IOException {
    try {
      JsonReader jsonReader = new JsonReader(new StringReader(json));
      jsonReader.setLenient(false);
      return JSON_ELEMENT.read(jsonReader);
    } catch (NumberFormatException e) {
      throw new IOException(e);
    }
  }

  /*
   * Converts a parsed {@link JsonElement} into a long if it contains a valid long value.
   *
   * <p>Requires that {@code element} is part of a output produced by {@link #parse}.
   *
   * @throws NumberFormatException if {@code element} does not contain a valid long value.
   *
   */
  public static long getParsedNumberAsLongOrThrow(JsonElement element) {
    Number num = element.getAsNumber();
    if (!(num instanceof LazilyParsedNumber)) {
      // We restrict this function to LazilyParsedNumber because then we know that "toString" will
      // return the unparsed number. For other implementations of Number interface, it is not
      // clearly defined what toString will return.
      throw new IllegalArgumentException("does not contain a parsed number.");
    }
    return Long.parseLong(element.getAsNumber().toString());
  }

  private JsonParser() {}
}

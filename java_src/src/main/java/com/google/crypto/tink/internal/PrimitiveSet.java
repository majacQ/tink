// Copyright 2017 Google LLC
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

package com.google.crypto.tink.internal;

import com.google.crypto.tink.CryptoFormat;
import com.google.crypto.tink.Key;
import com.google.crypto.tink.Parameters;
import com.google.crypto.tink.monitoring.MonitoringAnnotations;
import com.google.crypto.tink.proto.KeyStatusType;
import com.google.crypto.tink.proto.Keyset;
import com.google.crypto.tink.proto.OutputPrefixType;
import com.google.crypto.tink.util.Bytes;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * A container class for a set of primitives -- implementations of cryptographic primitives offered
 * by Tink.
 *
 * <p>It provides also additional properties for the primitives it holds. In particular, one of the
 * primitives in the set can be distinguished as "the primary" one.
 *
 * <p>PrimitiveSet is an auxiliary class used for supporting key rotation: primitives in a set
 * correspond to keys in a keyset. Users will usually work with primitive instances, which
 * essentially wrap primitive sets. For example an instance of an Aead-primitive for a given keyset
 * holds a set of Aead-primitives corresponding to the keys in the keyset, and uses the set members
 * to do the actual crypto operations: to encrypt data the primary Aead-primitive from the set is
 * used, and upon decryption the ciphertext's prefix determines the id of the primitive from the
 * set.
 */
public final class PrimitiveSet<P> {

  /**
   * A single entry in the set. In addition to the actual primitive it holds also some extra
   * information about the primitive.
   */
  public static final class Entry<P> {
    // If set, this is a primitive of a key.
    private final P fullPrimitive;
    // Identifies the primitive within the set.
    // It is the ciphertext prefix of the corresponding key.
    private final Bytes outputPrefix;
    // The status of the key represented by the primitive. Currently always equal to "ENABLED".
    private final KeyStatusType status;
    // The output prefix type of the key represented by the primitive.
    private final OutputPrefixType outputPrefixType;
    // The id of the key.
    private final int keyId;
    private final String keyTypeUrl;
    private final Key key;

    private Entry(
        P fullPrimitive,
        final Bytes outputPrefix,
        KeyStatusType status,
        OutputPrefixType outputPrefixType,
        int keyId,
        String keyTypeUrl,
        Key key) {
      this.fullPrimitive = fullPrimitive;
      this.outputPrefix = outputPrefix;
      this.status = status;
      this.outputPrefixType = outputPrefixType;
      this.keyId = keyId;
      this.keyTypeUrl = keyTypeUrl;
      this.key = key;
    }

    /**
     * Returns the full primitive for this entry.
     *
     * <p>This is used in cases when the new Tink Key interface is used and the primitive is
     * self-sufficient by itself, meaning that all the necessary information to process the
     * primitive is contained in the primitive (most likely through the new Key interface), as
     * opposed to the {@code primitive} field (see {@link #getPrimitive} for details).
     */
    public P getFullPrimitive() {
      return this.fullPrimitive;
    }

    public KeyStatusType getStatus() {
      return status;
    }

    public OutputPrefixType getOutputPrefixType() {
      return outputPrefixType;
    }

    private final Bytes getOutputPrefix() {
      return outputPrefix;
    }

    public int getKeyId() {
      return keyId;
    }

    public String getKeyTypeUrl() {
      return keyTypeUrl;
    }

    public Key getKey() {
      return key;
    }

    @Nullable
    public Parameters getParameters() {
      if (key == null) {
        return null;
      }
      return key.getParameters();
    }
  }

  private static <P> void storeEntryInPrimitiveSet(
      Entry<P> entry, Map<Bytes, List<Entry<P>>> entries, List<Entry<P>> entriesInKeysetOrder) {
    List<Entry<P>> list = new ArrayList<>();
    list.add(entry);
    List<Entry<P>> existing =
        entries.put(entry.getOutputPrefix(), Collections.unmodifiableList(list));
    if (existing != null) {
      List<Entry<P>> newList = new ArrayList<>();
      newList.addAll(existing);
      newList.add(entry);
      entries.put(entry.getOutputPrefix(), Collections.unmodifiableList(newList));
    }
    entriesInKeysetOrder.add(entry);
  }

  /** Returns the entry with the primary primitive. */
  @Nullable
  public Entry<P> getPrimary() {
    return primary;
  }

  public boolean hasAnnotations() {
    return !annotations.toMap().isEmpty();
  }

  public MonitoringAnnotations getAnnotations() {
    return annotations;
  }

  /** Returns all primitives using RAW prefix. */
  public List<Entry<P>> getRawPrimitives() {
    return getPrimitive(CryptoFormat.RAW_PREFIX);
  }

  /** Returns the entries with primitive identifed by {@code identifier}. */
  public List<Entry<P>> getPrimitive(final byte[] identifier) {
    List<Entry<P>> found = entries.get(Bytes.copyFrom(identifier));
    return found != null ? found : Collections.<Entry<P>>emptyList();
  }

  /** Returns all primitives. */
  public Collection<List<Entry<P>>> getAll() {
    return entries.values();
  }

  /** Returns all primitives in the original keyset key order. */
  public List<Entry<P>> getAllInKeysetOrder() {
    return Collections.unmodifiableList(entriesInKeysetOrder);
  }

  /**
   * The primitives are stored in a hash map of (ciphertext prefix, list of primitives sharing the
   * prefix). This allows quickly retrieving the list of primitives sharing some particular prefix.
   * Because all RAW keys are using an empty prefix, this also quickly allows retrieving them.
   */
  private final Map<Bytes, List<Entry<P>>> entries;

  /** Stores entries in the original keyset key order. */
  private final List<Entry<P>> entriesInKeysetOrder;

  private final Entry<P> primary;
  private final Class<P> primitiveClass;
  private final MonitoringAnnotations annotations;

  /** Creates an immutable PrimitiveSet. It is used by the Builder. */
  private PrimitiveSet(
      Map<Bytes, List<Entry<P>>> entries,
      List<Entry<P>> entriesInKeysetOrder,
      Entry<P> primary,
      MonitoringAnnotations annotations,
      Class<P> primitiveClass) {
    this.entries = entries;
    this.entriesInKeysetOrder = entriesInKeysetOrder;
    this.primary = primary;
    this.primitiveClass = primitiveClass;
    this.annotations = annotations;
  }

  public Class<P> getPrimitiveClass() {
    return primitiveClass;
  }

  /** Builds an immutable PrimitiveSet. This is the prefered way to construct a PrimitiveSet. */
  public static class Builder<P> {
    private final Class<P> primitiveClass;

    // primitives == null indicates that build has been called and the builder can't be used
    // anymore.
    private Map<Bytes, List<Entry<P>>> entries = new HashMap<>();
    private final List<Entry<P>> entriesInKeysetOrder = new ArrayList<>();
    private Entry<P> primary;
    private MonitoringAnnotations annotations;

    @CanIgnoreReturnValue
    private Builder<P> addEntry(
        final P fullPrimitive, Key key, Keyset.Key protoKey, boolean asPrimary)
        throws GeneralSecurityException {
      if (entries == null) {
        throw new IllegalStateException("addEntry cannot be called after build");
      }
      if (fullPrimitive == null) {
        throw new NullPointerException("`fullPrimitive` must not be null");
      }
      if (protoKey.getStatus() != KeyStatusType.ENABLED) {
        throw new GeneralSecurityException("only ENABLED key is allowed");
      }
      Entry<P> entry =
          new Entry<P>(
              fullPrimitive,
              Bytes.copyFrom(CryptoFormat.getOutputPrefix(protoKey)),
              protoKey.getStatus(),
              protoKey.getOutputPrefixType(),
              protoKey.getKeyId(),
              protoKey.getKeyData().getTypeUrl(),
              key);
      storeEntryInPrimitiveSet(entry, entries, entriesInKeysetOrder);
      if (asPrimary) {
        if (this.primary != null) {
          throw new IllegalStateException("you cannot set two primary primitives");
        }
        this.primary = entry;
      }
      return this;
    }

    /**
     * Adds a non-primary primitive.
     *
     * <p>The caller must make sure that the {@code fullPrimitive} is a full primitive constructed
     * from key, and that {@code protoKey} contains the same key as {@code fullPrimitive}.
     */
    @CanIgnoreReturnValue
    public Builder<P> addFullPrimitive(final P fullPrimitive, Key key, Keyset.Key protoKey)
        throws GeneralSecurityException {
      return addEntry(fullPrimitive, key, protoKey, false);
    }

    /**
     * Adds the primary primitive. This should be called exactly once per PrimitiveSet.
     *
     * <p>The caller must make sure that the {@code fullPrimitive} is a full primitive constructed
     * from key, and that {@code protoKey} contains the same key as {@code fullPrimitive}.
     */
    @CanIgnoreReturnValue
    public Builder<P> addPrimaryFullPrimitive(final P fullPrimitive, Key key, Keyset.Key protoKey)
        throws GeneralSecurityException {
      return addEntry(fullPrimitive, key, protoKey, true);
    }

    @CanIgnoreReturnValue
    public Builder<P> setAnnotations(MonitoringAnnotations annotations) {
      if (entries == null) {
        throw new IllegalStateException("setAnnotations cannot be called after build");
      }
      this.annotations = annotations;
      return this;
    }

    public PrimitiveSet<P> build() throws GeneralSecurityException {
      if (entries == null) {
        throw new IllegalStateException("build cannot be called twice");
      }
      // Note that we currently don't enforce that primary must be set.
      PrimitiveSet<P> output =
          new PrimitiveSet<P>(entries, entriesInKeysetOrder, primary, annotations, primitiveClass);
      this.entries = null;
      return output;
    }

    private Builder(Class<P> primitiveClass) {
      this.primitiveClass = primitiveClass;
      this.annotations = MonitoringAnnotations.EMPTY;
    }
  }

  public static <P> Builder<P> newBuilder(Class<P> primitiveClass) {
    return new Builder<P>(primitiveClass);
  }
}

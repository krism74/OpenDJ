/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 *      Portions Copyright 2014 ForgeRock AS
 */
package org.opends.server.backends.pluggable;

import org.forgerock.util.Reject;
import org.opends.server.api.CompressedSchema;
import org.opends.server.types.EntryEncodeConfig;

/**
 * Configuration class to indicate desired compression and cryptographic options
 * for the data stored in the database.
 */
public final class DataConfig
{
  /** Indicates whether data should be compressed before writing to the database. */
  private boolean compressed;

  /** The configuration to use when encoding entries in the database. */
  private EntryEncodeConfig encodeConfig = new EntryEncodeConfig();

  /**
   * Construct a new DataConfig object with the specified settings.
   *
   * @param compressed true if data should be compressed, false if not.
   * @param compactEncoding true if data should be encoded in compact form,
   * false if not.
   * @param compressedSchema the compressed schema manager to use.  It must not
   * be {@code null} if compactEncoding is {@code true}.
   */
  public DataConfig(boolean compressed, boolean compactEncoding, CompressedSchema compressedSchema)
  {
    this.compressed = compressed;
    setCompactEncoding(compactEncoding, compressedSchema);
  }

  /**
   * Determine whether data should be compressed before writing to the database.
   * @return true if data should be compressed, false if not.
   */
  public boolean isCompressed()
  {
    return compressed;
  }

  /**
   * Determine whether entries should be encoded with the compact form before
   * writing to the database.
   * @return true if data should be encoded in the compact form.
   */
  public boolean isCompactEncoding()
  {
    return encodeConfig.compressAttributeDescriptions();
  }

  /**
   * Configure whether data should be compressed before writing to the database.
   * @param compressed true if data should be compressed, false if not.
   */
  public void setCompressed(boolean compressed)
  {
    this.compressed = compressed;
  }

  /**
   * Configure whether data should be encoded with the compact form before
   * writing to the database.
   * @param compactEncoding true if data should be encoded in compact form,
   * false if not.
   * @param compressedSchema The compressed schema manager to use.  It must not
   * be {@code null} if compactEncoding is {@code true}.
   */
  public void setCompactEncoding(boolean compactEncoding, CompressedSchema compressedSchema)
  {
    if (compressedSchema == null)
    {
      Reject.ifTrue(compactEncoding);
      this.encodeConfig = new EntryEncodeConfig(false, compactEncoding, false);
    }
    else
    {
      this.encodeConfig = new EntryEncodeConfig(false, compactEncoding, compactEncoding, compressedSchema);
    }
  }

  /**
   * Get the EntryEncodeConfig object in use by this configuration.
   * @return the EntryEncodeConfig object in use by this configuration.
   */
  public EntryEncodeConfig getEntryEncodeConfig()
  {
    return this.encodeConfig;
  }

  /**
   * Get a string representation of this object.
   * @return A string representation of this object.
   */
  @Override
  public String toString()
  {
    final StringBuilder builder = new StringBuilder();
    builder.append("DataConfig(compressed=");
    builder.append(compressed);
    builder.append(", ");
    encodeConfig.toString(builder);
    builder.append(")");
    return builder.toString();
  }
}

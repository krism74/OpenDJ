/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE
 * or https://OpenDS.dev.java.net/OpenDS.LICENSE.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE.  If applicable,
 * add the following below this CDDL HEADER, with the fields enclosed
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */
package org.opends.server.admin;



import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;



/**
 * This enumeration defines various memory size units.
 */
public enum SizeUnit {

  /**
   * A byte unit.
   */
  BYTES(1L, "b", "bytes"),

  /**
   * A kilo-byte unit.
   */
  KILO_BYTES(1000L, "kb", "kilobytes"),

  /**
   * A kibi-byte unit.
   */
  KIBI_BYTES(1024L, "kib", "kibibytes"),

  /**
   * A mega-byte unit.
   */
  MEGA_BYTES((long) 1000 * 1000, "mb", "megabytes"),

  /**
   * A mebi-byte unit.
   */
  MEBI_BYTES((long) 1024 * 1024, "mib", "mebibytes"),

  /**
   * A giga-byte unit.
   */
  GIGA_BYTES((long) 1000 * 1000 * 1000, "gb", "gigabytes"),

  /**
   * A gibi-byte unit.
   */
  GIBI_BYTES((long) 1024 * 1024 * 1024, "gib", "gibibytes"),

  /**
   * A tera-byte unit.
   */
  TERA_BYTES((long) 1000 * 1000 * 1000 * 1000, "tb", "terabytes"),

  /**
   * A tebi-byte unit.
   */
  TEBI_BYTES((long) 1024 * 1024 * 1024 * 1024, "tib", "tebibytes");

  // A lookup table for resolving a unit from its name.
  private static final Map<String, SizeUnit> nameToUnit;
  static {
    nameToUnit = new HashMap<String, SizeUnit>();

    for (SizeUnit unit : SizeUnit.values()) {
      nameToUnit.put(unit.shortName, unit);
      nameToUnit.put(unit.longName, unit);
    }
  }



  /**
   * Get the unit corresponding to the provided unit name.
   *
   * @param s
   *          The name of the unit. Can be the abbreviated or long name and can
   *          contain white space and mixed case characters.
   * @return Returns the unit corresponding to the provided unit name.
   * @throws IllegalArgumentException
   *           If the provided name did not correspond to a known memory size
   *           unit.
   */
  public static SizeUnit getUnit(String s) throws IllegalArgumentException {
    SizeUnit unit = nameToUnit.get(s.trim().toLowerCase());
    if (unit == null) {
      throw new IllegalArgumentException("Illegal memory size unit \"" + s
          + "\"");
    }
    return unit;
  }



  /**
   * Parse the provided size string and return its equivalent size in
   * bytes. The size string must specify the unit e.g. "10kb".
   *
   * @param s
   *          The size string to be parsed.
   * @return Returns the parsed duration in bytes.
   * @throws NumberFormatException
   *           If the provided size string could not be parsed.
   */
  public static long parseValue(String s) throws NumberFormatException {
    return parseValue(s, null);
  }



  /**
   * Parse the provided size string and return its equivalent size in
   * bytes.
   *
   * @param s
   *          The size string to be parsed.
   * @param defaultUnit
   *          The default unit to use if there is no unit specified in
   *          the size string, or <code>null</code> if the string
   *          must always contain a unit.
   * @return Returns the parsed size in bytes.
   * @throws NumberFormatException
   *           If the provided size string could not be parsed.
   */
  public static long parseValue(String s, SizeUnit defaultUnit)
      throws NumberFormatException {
    // Value must be a floating point number followed by a unit.
    Pattern p = Pattern.compile("^\\s*(\\d+(\\.\\d+)?)\\s*(\\w+)?\\s*$");
    Matcher m = p.matcher(s);

    if (!m.matches()) {
      throw new NumberFormatException("Invalid size value \"" + s + "\"");
    }

    // Group 1 is the float.
    double d;
    try {
      d = Double.valueOf(m.group(1));
    } catch (NumberFormatException e) {
      throw new NumberFormatException("Invalid size value \"" + s + "\"");
    }

    // Group 3 is the unit.
    String unitString = m.group(3);
    SizeUnit unit;
    if (unitString == null) {
      if (defaultUnit == null) {
        throw new NumberFormatException("Invalid size value \"" + s + "\"");
      } else {
        unit = defaultUnit;
      }
    } else {
      try {
        unit = getUnit(unitString);
      } catch (IllegalArgumentException e) {
        throw new NumberFormatException("Invalid size value \"" + s + "\"");
      }
    }

    return unit.toBytes(d);
  }

  // The size of the unit in bytes.
  private final long sz;

  // The abbreviation of the unit.
  private final String shortName;

  // The long name of the unit.
  private final String longName;



  // Private constructor.
  private SizeUnit(long sz, String shortName, String longName) {
    this.sz = sz;
    this.shortName = shortName;
    this.longName = longName;
  }



  /**
   * Converts the specified size in bytes to this unit.
   *
   * @param amount
   *          The size in bytes.
   * @return Returns size in this unit.
   */
  public double fromBytes(long amount) {
    return ((double) amount / sz);
  }



  /**
   * Get the best-fit unit for the specified amount in this unit. The returned
   * unit will be able to represent the amount using an integral value (i.e. no
   * fractional part).For example, if this unit is kilo-bytes and the amount
   * 2000 is provided, then the best fit unit is mega-bytes: 2mb. Similarly, if
   * the amount is 0.5, then the best fit unit will by bytes: 500b.
   * <p>
   * Only non-binary units are considered (i.e. kilo, mega, etc).
   *
   * @param amount
   *          The amount of this unit.
   * @return Returns the best-fit unit for the specified amount in this unit.
   */
  public SizeUnit getBestFitUnit(double amount) {
    long bytes = toBytes(amount);
    if (bytes == 0) {
      return this;
    } else if (bytes > 0) {
      // use array of SizeUnits; EnumSet iterates in natural order
      for (SizeUnit unit : new SizeUnit[]{TERA_BYTES, GIGA_BYTES, MEGA_BYTES,
              KILO_BYTES }) {
        if ((bytes % unit.sz) == 0) {
          return unit;
        }
      }
    }
    return BYTES;
  }



  /**
   * Get the long name of this unit.
   *
   * @return Returns the long name of this unit.
   */
  public String getLongName() {
    return longName;
  }



  /**
   * Get the abbreviated name of this unit.
   *
   * @return Returns the abbreviated name of this unit.
   */
  public String getShortName() {
    return shortName;
  }



  /**
   * Get the number of bytes that this unit represents.
   *
   * @return Returns the number of bytes that this unit represents.
   */
  public long getSize() {
    return sz;
  }



  /**
   * Converts the specified size in this unit to bytes.
   *
   * @param amount
   *          The size as a quantity of this unit.
   * @return Returns the number of bytes that the size represents.
   */
  public long toBytes(double amount) {
    return (long) (sz * amount);
  }



  /**
   * {@inheritDoc}
   * <p>
   * This implementation returns the abbreviated name of this size unit.
   */
  @Override
  public String toString() {
    return shortName;
  }
}

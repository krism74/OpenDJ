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
 *      Copyright 2014 ForgeRock AS
 */
package org.opends.server.replication.protocol;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.zip.DataFormatException;

import org.opends.server.DirectoryServerTestCase;
import org.opends.server.TestCaseUtils;
import org.opends.server.replication.common.CSN;
import org.opends.server.replication.common.ServerState;
import org.opends.server.types.ByteStringBuilder;
import org.opends.server.types.DN;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

/**
 * Test for {@link ByteStringBuilder} and {@link ByteArrayScanner} classes.
 */
@SuppressWarnings("javadoc")
public class ByteArrayTest extends DirectoryServerTestCase
{

  private static final class IntegerRange implements Iterator<Object[]>
  {
    private int next;
    private final int endInclusive;

    public IntegerRange(int startInclusive, int endInclusive)
    {
      this.next = startInclusive;
      this.endInclusive = endInclusive;
    }

    @Override
    public boolean hasNext()
    {
      return next <= this.endInclusive;
    }

    @Override
    public Object[] next()
    {
      return new Object[] { next++ };
    }

    @Override
    public void remove() { /* unused */ }
  }

  @BeforeClass
  public void setup() throws Exception
  {
    TestCaseUtils.startFakeServer();
  }

  @AfterClass
  public void teardown() throws Exception
  {
    TestCaseUtils.shutdownFakeServer();
  }

  private final byte[] byteArray = new byte[] { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, };

  @Test
  public void testBuilderAppendMethodsAndScannerNextMethods() throws Exception
  {
    final boolean boFalse = false;
    final boolean boTrue = true;
    final byte by = 80;
    final short sh = 42;
    final int i = sh + 1;
    final long l = i + 1;
    final String nullStr = null;
    final String str = "Yay!";
    final Collection<String> col = Arrays.asList("foo", "bar", "baz");
    final CSN csn = new CSN(42424242, 13, 42);
    final DN dn = DN.decode("dc=example,dc=com");
    final ServerState ss = new ServerState();
    ss.update(csn);

    byte[] bytes = new ByteArrayBuilder()
        .append(boTrue)
        .append(boFalse)
        .append(by)
        .append(sh)
        .append(i)
        .append(l)
        .append(nullStr)
        .append(str)
        .appendStrings(col)
        .appendUTF8(i)
        .appendUTF8(l)
        .append(csn)
        .appendUTF8(csn)
        .append(dn)
        .appendZeroTerminated(byteArray)
        .append(byteArray)
        .append(ss)
        .toByteArray();

    final ByteArrayScanner scanner = new ByteArrayScanner(bytes);
    assertFalse(scanner.isEmpty());
    assertEquals(scanner.nextBoolean(), boTrue);
    assertEquals(scanner.nextBoolean(), boFalse);
    assertEquals(scanner.nextByte(), by);
    assertEquals(scanner.nextShort(), sh);
    assertEquals(scanner.nextInt(), i);
    assertEquals(scanner.nextLong(), l);
    assertEquals(scanner.nextString(), nullStr);
    assertEquals(scanner.nextString(), str);
    assertEquals(scanner.nextStrings(new ArrayList<String>()), col);
    assertEquals(scanner.nextIntUTF8(), i);
    assertEquals(scanner.nextLongUTF8(), l);
    assertEquals(scanner.nextCSN(), csn);
    assertEquals(scanner.nextCSNUTF8(), csn);
    assertEquals(scanner.nextDN(), dn);
    assertEquals(scanner.nextByteArray(byteArray.length), byteArray);
    scanner.skipZeroSeparator();
    assertEquals(scanner.nextByteArray(byteArray.length), byteArray);
    assertEquals(scanner.nextServerState().toString(), ss.toString());
    assertTrue(scanner.isEmpty());
  }

  @Test
  public void testByteArrayScanner_remainingBytes() throws Exception
  {
    final byte[] bytes = new ByteArrayBuilder().append(byteArray).toByteArray();

    final ByteArrayScanner scanner = new ByteArrayScanner(bytes);
    assertEquals(scanner.remainingBytes(), byteArray);
    assertTrue(scanner.isEmpty());
  }

  @Test
  public void testByteArrayScanner_remainingBytesZeroTerminated() throws Exception
  {
    final byte[] bytes =
        new ByteArrayBuilder().appendZeroTerminated(byteArray).toByteArray();

    final ByteArrayScanner scanner = new ByteArrayScanner(bytes);
    assertEquals(scanner.remainingBytesZeroTerminated(), byteArray);
    assertTrue(scanner.isEmpty());
  }

  @DataProvider
  public Iterator<Object[]> testCasesForNextMethodsWithEmptyByteArray()
  {
    return new IntegerRange(0, 7);
  }

  @Test(dataProvider = "testCasesForNextMethodsWithEmptyByteArray",
      expectedExceptions = DataFormatException.class)
  public void testByteArrayScanner_nextMethods_throwsExceptionWhenNoData(int testNumber) throws Exception
  {
    delegate(testNumber);
  }

  /**
   * TestNG does not like test methods with a return type other than void,
   * so used a delegate to simplify the code down below.
   */
  private Object delegate(int testNumber) throws DataFormatException
  {
    final ByteArrayScanner scanner = new ByteArrayScanner(new byte[0]);
    switch (testNumber)
    {
    case 0:
      return scanner.nextByte();
    case 1:
      return scanner.nextBoolean();
    case 2:
      return scanner.nextShort();
    case 3:
      return scanner.nextInt();
    case 4:
      return scanner.nextIntUTF8();
    case 5:
      return scanner.nextLong();
    case 6:
      return scanner.nextLongUTF8();
    case 7:
      return scanner.nextCSN();
    default:
      return null;
    }
  }

  @Test(expectedExceptions = IndexOutOfBoundsException.class)
  public void testByteArrayScanner_skipZeroSeparator_throwsExceptionWhenNoData() throws Exception
  {
    new ByteArrayScanner(new byte[0]).skipZeroSeparator();
  }

  @Test(expectedExceptions = DataFormatException.class)
  public void testByteArrayScanner_skipZeroSeparator_throwsExceptionWhenNoZeroSeparator() throws Exception
  {
    new ByteArrayScanner(new byte[] { 1 }).skipZeroSeparator();
  }

  @Test(expectedExceptions = DataFormatException.class)
  public void testByteArrayScanner_nextCSNUTF8_throwsExceptionWhenInvalidCSN() throws Exception
  {
    new ByteArrayScanner(new byte[] { 1, 0 }).nextCSNUTF8();
  }

  @Test(expectedExceptions = DataFormatException.class)
  public void testByteArrayScanner_nextDN_throwsExceptionWhenInvalidDN() throws Exception
  {
    final byte[] bytes = new ByteArrayBuilder().append("this is not a valid DN").toByteArray();
    new ByteArrayScanner(bytes).nextDN();
  }

}

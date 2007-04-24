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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.protocols.ldap;

import org.opends.server.protocols.asn1.*;
import static org.opends.server.util.ServerConstants.EOL;
import org.opends.server.types.DN;
import org.opends.server.types.RDN;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.LDAPException;
import org.opends.server.core.DirectoryServer;

import java.util.ArrayList;
import java.util.Iterator;

import static org.testng.Assert.*;

import org.testng.annotations.*;

/**
 * This class defines a set of tests for the
 * org.opends.server.protocol.ldap.AddResponseProtocolOp class.
 */
public class TestAddResponseProtocolOp
{
  /**
   * The protocol op type for add requests.
   */
  private static final byte OP_TYPE_ADD_REQUEST = 0x68;

  /**
   * The protocol op type for add responses.
   */
  private static final byte OP_TYPE_ADD_RESPONSE = 0x69;

  /**
   * The result code for add result operations.
   */
  private static final int resultCode = 10;

  /**
   * The error message to use for add result operations.
   */
  private static final String resultMsg = "Test Successful";

  /**
   * The DN to use for add result operations
   */
  private DN dn;

  @BeforeClass
  public void setupDN()
  {
    //Setup the DN to use in the response tests.

    AttributeType attribute =
        DirectoryServer.getDefaultAttributeType("testAttribute");

    AttributeValue attributeValue = new AttributeValue(attribute, "testValue");

    RDN[] rdns = new RDN[1];
    rdns[0] = RDN.create(attribute, attributeValue);

    dn = new DN(rdns);
  }

  /**
   * Test to make sure the class processes the right LDAP op type.
   *
   * @throws Exception If the test failed unexpectedly.
   */
  @Test
  public void testOpType() throws Exception
  {
    AddResponseProtocolOp addResponse = new AddResponseProtocolOp(resultCode);
    assertEquals(addResponse.getType(), OP_TYPE_ADD_RESPONSE);
  }

  /**
   * Test to make sure the class returns the correct protocol name.
   *
   * @throws Exception If the test failed unexpectedly.
   */
  @Test
  public void testProtocolOpName() throws Exception
  {
    AddResponseProtocolOp addResponse = new AddResponseProtocolOp(resultCode);
    assertEquals(addResponse.getProtocolOpName(), "Add Response");
  }

  /**
   * Test the constructors to make sure the right objects are constructed.
   *
   * @throws Exception If the test failed unexpectedly.
   */
  @Test
  public void testConstructors() throws Exception
  {
    AddResponseProtocolOp addResponse;
    ArrayList<LDAPAttribute> attributes;

    //Test to make sure the constructor with result code param works.
    addResponse = new AddResponseProtocolOp(resultCode);
    assertEquals(addResponse.getResultCode(), resultCode);

    //Test to make sure the constructor with result code and error message
    //params works.
    addResponse = new AddResponseProtocolOp(resultCode, resultMsg);
    assertEquals(addResponse.getErrorMessage(), resultMsg);
    assertEquals(addResponse.getResultCode(), resultCode);

    //Test to make sure the constructor with result code, message, dn, and
    //referal params works.
    ArrayList<String> referralURLs = new ArrayList<String>();
    referralURLs.add("ds1.example.com");
    referralURLs.add("ds2.example.com");
    referralURLs.add("ds3.example.com");

    addResponse = new AddResponseProtocolOp(resultCode, resultMsg, dn,
                                           referralURLs);
    assertEquals(addResponse.getErrorMessage(), resultMsg);
    assertEquals(addResponse.getResultCode(), resultCode);
    assertEquals(addResponse.getMatchedDN(), dn);
    assertEquals(addResponse.getReferralURLs(), referralURLs);
  }

  /**
   * Test to make sure that setter methods work.
   *
   * @throws Exception If the test failed unexpectedly.
   */
  @Test
  public void testSetMethods() throws Exception
  {
    AddResponseProtocolOp addResponse;
    addResponse = new AddResponseProtocolOp(resultCode);

    addResponse.setResultCode(resultCode + 1);
    assertEquals(addResponse.getResultCode(), resultCode + 1);

    addResponse.setErrorMessage(resultMsg);
    assertEquals(addResponse.getErrorMessage(), resultMsg);

    addResponse.setMatchedDN(dn);
    assertEquals(addResponse.getMatchedDN(), dn);

    ArrayList<String> referralURLs = new ArrayList<String>();
    referralURLs.add("ds1.example.com");
    referralURLs.add("ds2.example.com");
    referralURLs.add("ds3.example.com");
    addResponse.setReferralURLs(referralURLs);
    assertEquals(addResponse.getReferralURLs(), referralURLs);
  }

  /**
   * Test the decode method when an empty element is passed
   *
   * @throws Exception If the test failed unexpectedly.
   */
  @Test(expectedExceptions = LDAPException.class)
  public void testDecodeEmptyElement() throws Exception
  {
    ArrayList<ASN1Element> elements = new ArrayList<ASN1Element>();
    AddResponseProtocolOp.decode(new ASN1Sequence(OP_TYPE_ADD_RESPONSE,
                                                 elements));
  }

  /**
   * Test the decode method when an element with a invalid result code is
   * passed
   *
   * @throws Exception If the test failed unexpectedly.
   */
  @Test(expectedExceptions = LDAPException.class)
  public void testDecodeInvalidResultCode() throws Exception
  {
    ArrayList<ASN1Element> elements = new ArrayList<ASN1Element>(2);
    elements.add(new ASN1OctetString("Invalid Data"));
    elements.add(new ASN1Null());
    elements.add(new ASN1Null());
    AddRequestProtocolOp.decode(new ASN1Sequence(OP_TYPE_ADD_RESPONSE,
                                                 elements));
  }

  /**
   * Test the decode method when an element with a invalid dn is
   * passed. Never throws an exception as long as the element is not null.
   * This is the expected behavior.
   *
   * @throws Exception If the test failed unexpectedly.
   */
  //@Test(expectedExceptions = LDAPException.class)
  public void testDecodeInvalidDN() throws Exception
  {
    ArrayList<ASN1Element> elements = new ArrayList<ASN1Element>(2);
    elements.add(new ASN1Enumerated(resultCode));
    elements.add(new ASN1Null());
    elements.add(new ASN1Null());
    AddRequestProtocolOp.decode(new ASN1Sequence(OP_TYPE_ADD_RESPONSE,
                                                 elements));
  }

  /**
   * Test the decode method when an element with a invalid result message is
   * passed. Never throws an exception as long as the element is not null.
   * This is the expected behavior.
   *
   * @throws Exception If the test failed unexpectedly.
   */
  //@Test(expectedExceptions = LDAPException.class)
  public void testDecodeInvalidResultMsg() throws Exception
  {
    ArrayList<ASN1Element> elements = new ArrayList<ASN1Element>(2);
    elements.add(new ASN1Enumerated(resultCode));
    elements.add(new ASN1OctetString(dn.toString()));
    elements.add(new ASN1Null());
    AddRequestProtocolOp.decode(new ASN1Sequence(OP_TYPE_ADD_RESPONSE,
                                                 elements));
  }

  /**
   * Test the decode method when an element with a invalid referral URL is
   * passed. Never throws an exception as long as the element is not null.
   * This is the expected behavior.
   *
   * @throws Exception If the test failed unexpectedly.
   */
  //@Test(expectedExceptions = LDAPException.class)
  public void testDecodeInvalidReferralURLs() throws Exception
  {
    ArrayList<ASN1Element> elements = new ArrayList<ASN1Element>(2);
    elements.add(new ASN1Enumerated(resultCode));
    elements.add(new ASN1OctetString(dn.toString()));
    elements.add(new ASN1OctetString(resultMsg));
    elements.add(new ASN1Null());
    AddRequestProtocolOp.decode(new ASN1Sequence(OP_TYPE_ADD_RESPONSE,
                                                 elements));
  }

  /**
   * Test the encode and decode methods and corner cases.
   *
   * @throws Exception If the test failed unexpectedly.
   */
  @Test
  public void testEncodeDecode() throws Exception
  {
    AddResponseProtocolOp addEncoded;
    AddResponseProtocolOp addDecoded;
    ASN1Element element;

    ArrayList<String> referralURLs = new ArrayList<String>();
    referralURLs.add("ds1.example.com");
    referralURLs.add("ds2.example.com");
    referralURLs.add("ds3.example.com");


    //Test case for a full encode decode operation with normal params.
    addEncoded = new AddResponseProtocolOp(resultCode, resultMsg, dn,
                                           referralURLs);
    element = addEncoded.encode();
    addDecoded = (AddResponseProtocolOp)AddResponseProtocolOp.decode(element);

    assertEquals(addEncoded.getType(), OP_TYPE_ADD_RESPONSE);
    assertEquals(addEncoded.getMatchedDN().compareTo(addDecoded.getMatchedDN()),
                 0);
    assertEquals(addEncoded.getErrorMessage(), addDecoded.getErrorMessage());
    assertEquals(addEncoded.getResultCode(), addDecoded.getResultCode());
    assertEquals(addEncoded.getReferralURLs(), addDecoded.getReferralURLs());


    //Test case for a full encode decode operation with an empty DN params.
    addEncoded = new AddResponseProtocolOp(resultCode, resultMsg, DN.nullDN(),
                                           referralURLs);
    element = addEncoded.encode();
    addDecoded = (AddResponseProtocolOp)AddResponseProtocolOp.decode(element);
    assertEquals(addDecoded.getMatchedDN(), null);

    //Test case for a full empty referral url param.
    ArrayList<String> emptyReferralURLs = new ArrayList<String>();
    addEncoded = new AddResponseProtocolOp(resultCode, resultMsg, dn,
                                           emptyReferralURLs);
    element = addEncoded.encode();
    addDecoded = (AddResponseProtocolOp)AddResponseProtocolOp.decode(element);
    assertTrue(addDecoded.getReferralURLs() == null);

    //Test case for a full encode decode operation with resultCode param only.
    addEncoded = new AddResponseProtocolOp(resultCode);
    element = addEncoded.encode();
    addDecoded = (AddResponseProtocolOp)AddResponseProtocolOp.decode(element);

    assertEquals(addDecoded.getMatchedDN(), null);
    assertEquals(addDecoded.getErrorMessage(), null);
    assertEquals(addEncoded.getResultCode(), addDecoded.getResultCode());
    assertTrue(addDecoded.getReferralURLs() == null);
  }

  /**
   * Test the toString (single line) method.
   *
   * @throws Exception If the test fails unexpectedly.
   */
  @Test
  public void TestToStringSingleLine() throws Exception
  {
    AddResponseProtocolOp addResponse;
    StringBuilder buffer = new StringBuilder();
    StringBuilder key = new StringBuilder();

    ArrayList<String> referralURLs = new ArrayList<String>();
    referralURLs.add("ds1.example.com");
    referralURLs.add("ds2.example.com");
    referralURLs.add("ds3.example.com");

    addResponse = new AddResponseProtocolOp(resultCode, resultMsg, dn,
                                           referralURLs);
    addResponse.toString(buffer);

    key.append("AddResponse(resultCode="+resultCode+", " +
        "errorMessage="+resultMsg+", matchedDN="+dn.toString()+", " +
        "referralURLs={");

    Iterator<String> iterator = referralURLs.iterator();
      key.append(iterator.next());

    while (iterator.hasNext())
    {
      key.append(", ");
      key.append(iterator.next());
    }

    key.append("})");

    assertEquals(buffer.toString(), key.toString());
  }

  /**
   * Test the toString (multi line) method.
   *
   * @throws Exception If the test fails unexpectedly.
   */
  @Test
  public void TestToStringMultiLine() throws Exception
  {
    AddResponseProtocolOp addResponse;
    StringBuilder buffer = new StringBuilder();
    StringBuilder key = new StringBuilder();

    ArrayList<String> referralURLs = new ArrayList<String>();
    referralURLs.add("ds1.example.com");
    referralURLs.add("ds2.example.com");
    referralURLs.add("ds3.example.com");
    int indent = 5;
    int i;

    addResponse = new AddResponseProtocolOp(resultCode, resultMsg, dn,
                                           referralURLs);
    addResponse.toString(buffer, indent);

    StringBuilder indentBuf = new StringBuilder(indent);
    for (i=0 ; i < indent; i++)
    {
      indentBuf.append(' ');
    }

    key.append(indentBuf);
    key.append("Add Response");
    key.append(EOL);

    key.append(indentBuf);
    key.append("  Result Code:  ");
    key.append(resultCode);
    key.append(EOL);

    key.append(indentBuf);
    key.append("  Error Message:  ");
    key.append(resultMsg);
    key.append(EOL);

    key.append(indentBuf);
    key.append("  Matched DN:  ");
    key.append(dn.toString());
    key.append(EOL);

    key.append(indentBuf);
    key.append("  Referral URLs:  ");
    key.append(EOL);

    for (String url : referralURLs)
    {
      key.append(indentBuf);
      key.append("  ");
      key.append(url);
      key.append(EOL);
    }

    assertEquals(buffer.toString(), key.toString());
  }
}

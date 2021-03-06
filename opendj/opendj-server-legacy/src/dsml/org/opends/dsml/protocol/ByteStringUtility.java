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
 *      Copyright 2012-2015 ForgeRock AS.
 */
package org.opends.dsml.protocol;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;

import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.w3c.dom.Element;

/**
 * A utility class to assist in converting DsmlValues (in Objects) into
 * the required ByteStrings, and back again.
 */
public class ByteStringUtility
{
  /**
   * Returns a ByteString from a DsmlValue Object.
   *
   * @param obj
   *           the DsmlValue object.
   * @return a new ByteString object with the value, or null if val was null,
   *         or if it could not be converted.
   * @throws IOException if any problems occurred retrieving an anyURI value.
   */
  public static ByteString convertValue(Object obj) throws IOException
  {
    ByteString bs = null;
    if (obj != null)
    {
      if (obj instanceof String)
      {
        bs = ByteString.valueOf((String)obj);
      }
      else if (obj instanceof byte [])
      {
        bs = ByteString.wrap((byte [])obj);
      }
      else if (obj instanceof URI)
      {
        // read raw content and return as a byte[].
        InputStream is = null;
        try
        {
          is = ((URI) obj).toURL().openStream();
          ByteStringBuilder bsb = new ByteStringBuilder();
          while (bsb.append(is, 2048) != -1)
          {
            // do nothing
          }
          bs = bsb.toByteString();
        }
        finally
        {
          if (is != null)
          {
            is.close();
          }
        }
      }
      else if (obj instanceof Element)
      {
        Element element = (Element) obj;
        bs = ByteString.valueOf(element.getTextContent());
      }
    }
    return bs;
  }

  /**
   * Returns a DsmlValue (Object) from an LDAP ByteString. The conversion is
   * simplistic - try and convert it to UTF-8 and if that fails return a byte[].
   *
   * @param bs the ByteString returned from LDAP.
   * @return a String or a byte[].
   */
  public static Object convertByteString(ByteString bs)
  {
    try
    {
      return new String(bs.toCharArray());
    }
    catch (Exception e)
    {
      return bs.toByteArray();
    }
  }
}

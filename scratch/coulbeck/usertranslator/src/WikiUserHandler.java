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

import org.xml.sax.helpers.DefaultHandler;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.opends.server.util.StaticUtils;
import org.opends.server.util.Base64;

import java.text.ParseException;

public class WikiUserHandler extends DefaultHandler
{
  /**
   * Receive notification of the beginning of the document.
   * <p/>
   * <p>By default, do nothing.  Application writers may override this
   * method in a subclass to take specific actions at the beginning
   * of a document (such as allocating the root node of a tree or
   * creating an output file).</p>
   *
   * @throws org.xml.sax.SAXException Any SAX exception, possibly
   *                                  wrapping another exception.
   * @see org.xml.sax.ContentHandler#startDocument
   */
  @Override
  public void startDocument() throws SAXException
  {
  }

  /**
   * Receive notification of the start of an element.
   * <p/>
   * <p>By default, do nothing.  Application writers may override this
   * method in a subclass to take specific actions at the start of
   * each element (such as allocating a new tree node or writing
   * output to a file).</p>
   *
   * @param uri        The Namespace URI, or the empty string if the
   *                   element has no Namespace URI or if Namespace
   *                   processing is not being performed.
   * @param localName  The local name (without prefix), or the
   *                   empty string if Namespace processing is not being
   *                   performed.
   * @param qName      The qualified name (with prefix), or the
   *                   empty string if qualified names are not available.
   * @param attributes The attributes attached to the element.  If
   *                   there are no attributes, it shall be an empty
   *                   Attributes object.
   * @throws org.xml.sax.SAXException Any SAX exception, possibly
   *                                  wrapping another exception.
   * @see org.xml.sax.ContentHandler#startElement
   */
  @Override
  public void startElement(String uri, String localName, String qName,
                           Attributes attributes) throws SAXException
  {
    if (qName.equals("user"))
    {
      String loginName = attributes.getValue("loginName");
      String wikiName = attributes.getValue("wikiName");
      String fullName = attributes.getValue("fullName");
      String email = attributes.getValue("email");
      String password = attributes.getValue("password");
//      String created = attributes.getValue("created");
//      String lastModified = attributes.getValue("lastModified");

      String userPassword = userPassword(password);
      String surname = surnameFromFullName(fullName);

      UserTranslator.wikiLogins.add(loginName);

      UserTranslator.addUser(loginName, email, fullName,
                     surname, wikiName, userPassword);
    }
  }

  /**
   * Get an LDAP userPassword value from a JSPwiki hashed password value.
   * The wiki password is {SHA} followed by the hex encoding of the hash.
   * The LDAP userPassword will be {SHA} followed by the base64 encoding of
   * the hash.
   * @param wikiPassword The JSPwiki hashed password value.
   * @return The hashed password value in LDAP userPassword form.
   */
  public static String userPassword(String wikiPassword)
  {
    try
    {
      String scheme = wikiPassword.substring(0, 5);
      byte[] hash = StaticUtils.hexStringToByteArray(wikiPassword.substring(5));
      return scheme + Base64.encode(hash);
    }
    catch (ParseException e)
    {
      return "";
    }
  }

  /**
   * Derive a surname value from the wiki user's full name.
   * @param fullName The wiki user's full name.
   * @return A surname value.
   */
  public static String surnameFromFullName(String fullName)
  {
    String trimmed = fullName.trim();
    int pos = trimmed.lastIndexOf(' ');
    if (pos == -1)
    {
      return trimmed;
    }
    else
    {
      return trimmed.substring(pos+1);
    }
  }

}

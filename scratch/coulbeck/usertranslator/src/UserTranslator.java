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

import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.parsers.SAXParser;
import java.io.IOException;
import java.io.File;
import java.io.PrintStream;
import java.util.HashSet;
import java.util.Set;

import com.csvreader.CsvReader;

/**
 * Users are mapped to their Wiki login where possible, otherwise they retain
 * their java.net login.
 */
public class UserTranslator
{
  // Set true to give all users the same email address.
  public static final boolean TEST = true;
  public static final String TEST_EMAIL = "opends-jira@sun.com";

  public static HashSet<String> wikiLogins;

  public static PrintStream ldifOut;

  public static void main(String[] args) throws
       SAXException, IOException, ParserConfigurationException
  {
    if (args.length != 3)
    {
      System.out.println("Usage: java UserTranslator userdatabase.xml usermapping.csv users.ldif");
    }

    String wikiUserDatabase = args[0];
    String issueTrackerCSV = args[1];
    String ldif = args[2];
    wikiLogins = new HashSet<String>(100);

    ldifOut = new PrintStream(ldif);

    ldifOut.println("dn: dc=opends,dc=org\n" +
         "objectClass: domain\n" +
         "objectClass: top\n" +
         "dc: opends\n");

    SAXParserFactory parserFactory = SAXParserFactory.newInstance();
    SAXParser parser = parserFactory.newSAXParser();

    WikiUserHandler handler = new WikiUserHandler();

    parser.parse(new File(wikiUserDatabase), handler);

    HashSet<String> userGroup = new HashSet<String>(100);
    HashSet<String> developerGroup = new HashSet<String>(100);
    HashSet<String> adminGroup = new HashSet<String>(100);

    CsvReader csv = new CsvReader(issueTrackerCSV);
    csv.readHeaders();
    while (csv.readRecord())
    {
      String login = csv.get("login");
      String wikiLogin = csv.get("wikilogin");
      String role = csv.get("role");
      if (!wikiLogins.contains(wikiLogin))
      {
        // No wiki login.
        addUser(login, login + "@dev.java.net", login, login, login, null);
      }
      else
      {
        // Map to the wiki login.
        login = wikiLogin;
      }
      if (role.equalsIgnoreCase("user"))
      {
        userGroup.add(login);
      }
      else if (role.equalsIgnoreCase("developer"))
      {
        userGroup.add(login);
        developerGroup.add(login);
      }
      else if (role.equalsIgnoreCase("administrator"))
      {
        userGroup.add(login);
        developerGroup.add(login);
        adminGroup.add(login);
      }
    }
    csv.close();
    addGroup("jira-users", userGroup);
    addGroup("jira-developers", developerGroup);
    addGroup("jira-administrators", adminGroup);
    ldifOut.close();
  }

  public static void addGroup(String groupName, Set<String> members)
  {
    ldifOut.println("dn: cn=" + groupName + ",dc=opends,dc=org");
    ldifOut.println("objectClass: top");
    ldifOut.println("objectClass: groupOfUniqueNames");
    ldifOut.println("cn: " + groupName);
    for (String user : members)
    {
      ldifOut.println("uniqueMember: uid=" + user + ",dc=opends,dc=org");
    }
    ldifOut.println("");
  }


  public static void addUser(String login, String email, String fullName,
                      String surname, String displayName, String password)
  {
    ldifOut.println("dn: uid=" + login + ",dc=opends,dc=org");
    ldifOut.println("objectClass: top");
    ldifOut.println("objectClass: person");
    ldifOut.println("objectClass: organizationalperson");
    ldifOut.println("objectClass: inetorgperson");
    ldifOut.println("uid: " + login);
    if (TEST)
    {
      ldifOut.println("mail: " + TEST_EMAIL);
    }
    ldifOut.println("mail: " + email);
    ldifOut.println("cn: " + fullName);
    ldifOut.println("sn: " + surname);
    ldifOut.println("displayName: " + displayName);
    if (password != null)
    {
      ldifOut.println("userPassword: " + password);
    }
    ldifOut.println("");
  }

}

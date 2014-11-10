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
 *      Copyright 2006-2009 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2014 ForgeRock AS
 */
package org.opends.server.backends.jeb;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ConditionResult;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.opendj.ldap.schema.MatchingRule;
import org.forgerock.opendj.ldap.spi.IndexingOptions;
import org.opends.server.TestCaseUtils;
import org.opends.server.admin.server.AdminTestCaseUtils;
import org.opends.server.admin.std.meta.LocalDBBackendCfgDefn;
import org.opends.server.admin.std.server.LocalDBBackendCfg;
import org.opends.server.controls.SubtreeDeleteControl;
import org.opends.server.core.DeleteOperationBasis;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ModifyDNOperationBasis;
import org.opends.server.core.ModifyOperationBasis;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.protocols.internal.SearchRequest;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeBuilder;
import org.opends.server.types.AttributeType;
import org.opends.server.types.Attributes;
import org.opends.server.types.Control;
import org.opends.server.types.DN;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.Modification;
import org.opends.server.types.RDN;
import org.opends.server.types.SearchResultEntry;
import org.opends.server.util.Base64;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.LockMode;

import static java.util.Collections.*;

import static org.assertj.core.api.Assertions.*;
import static org.forgerock.opendj.ldap.ConditionResult.*;
import static org.forgerock.opendj.ldap.ModificationType.*;
import static org.mockito.Mockito.*;
import static org.opends.server.protocols.internal.InternalClientConnection.*;
import static org.opends.server.protocols.internal.Requests.*;
import static org.opends.server.types.Attributes.*;
import static org.testng.Assert.*;

/**
 * BackendImpl Tester.
 */
@SuppressWarnings("javadoc")
public class TestBackendImpl extends JebTestCase {
  private String homeDirName;

  private BackendImpl backend;

  private List<Entry> topEntries;
  private List<Entry> entries;
  private List<Entry> additionalEntries;
  private Entry replaceEntry;
  private Entry newTop;

  /**
   * The attribute used to return a search index debug string to the client.
   */
  public static final String ATTR_DEBUG_SEARCH_INDEX = "debugsearchindex";

  @BeforeClass
  public void setUp() throws Exception {
    // This test suite depends on having the schema available, so we'll make
    // sure the server is started.
    TestCaseUtils.startServer();
    TestCaseUtils.enableBackend("indexRoot");

    homeDirName = "db_index_test";

    backend = (BackendImpl)DirectoryServer.getBackend("indexRoot");

    topEntries = TestCaseUtils.makeEntries(
        "dn: dc=test,dc=com",
        "objectclass: top",
        "objectclass: domain",
        "dc: example",
        "",
        "dn: ou=People,dc=test,dc=com",
        "objectclass: top",
        "objectclass: organizationalUnit",
        "ou: People",
        "",
        "dn: dc=test1,dc=com",
        "objectclass: top",
        "objectclass: domain",
        "dc: example1");
    entries = TestCaseUtils.makeEntries(
        "dn: uid=user.0,ou=People,dc=test,dc=com",
        "objectClass: top",
        "objectClass: person",
        "objectClass: organizationalPerson",
        "objectClass: inetOrgPerson",
        "givenName: Aaccf",
        "sn: Amar",
        "cn: Aaccf Amar",
        "initials: AQA",
        "employeeNumber: 0",
        "uid: user.0",
        "mail: user.0@example.com",
        "userPassword: password",
        "telephoneNumber: 380-535-2354",
        "homePhone: 707-626-3913",
        "pager: 456-345-7750",
        "mobile: 366-674-7274",
        "street: 99262 Eleventh Street",
        "l: Salem",
        "st: NM",
        "postalCode: 36530",
        "postalAddress: Aaccf Amar$99262 Eleventh Street$Salem, NM  36530",
        "description: This is the description for Aaccf Amar.",
        "",
        "dn: uid=user.1,ou=People,dc=test,dc=com",
        "objectClass: top",
        "objectClass: person",
        "objectClass: organizationalPerson",
        "objectClass: inetOrgPerson",
        "givenName: Aaren",
        "givenName;lang-fr: test2",
        "givenName;lang-cn: test2",
        "givenName;lang-es: test3",
        "sn: Atp",
        "cn: Aaren Atp",
        "initials: APA",
        "employeeNumber: 1",
        "uid: user.1",
        "mail: user.1@example.com",
        "userPassword: password",
        "telephoneNumber: 643-278-6134",
        "homePhone: 546-786-4099",
        "pager: 508-261-3187",
        "mobile: 377-267-7824",
        "carLicense: 377-267-7824",
        "street: 78113 Fifth Street",
        "l: Chico",
        "st: TN",
        "postalCode: 72322",
        "postalAddress: Aaren Atp$78113 Fifth Street$Chico, TN  72322",
        "description: This is the description for Aaren Atp.",
        "",
        "dn: uid=user.2,ou=People,dc=test,dc=com",
        "objectClass: top",
        "objectClass: person",
        "objectClass: organizationalPerson",
        "objectClass: inetOrgPerson",
        "givenName: Aarika",
        "sn: Atpco",
        "cn: Aarika Atpco",
        "initials: ARA",
        "employeeNumber: 2",
        "uid: user.2",
        "mail: user.2@example.com",
        "userPassword: password",
        "telephoneNumber: 547-504-3498",
        "homePhone: 955-899-7308",
        "pager: 710-832-9316",
        "mobile: 688-388-4525",
        "carLicense: 688-388-4525",
        "street: 59208 Elm Street",
        "l: Youngstown",
        "st: HI",
        "postalCode: 57377",
        "postalAddress: Aarika Atpco$59208 Elm Street$Youngstown, HI  57377",
        "description: This is the description for Aarika Atpco.",
        "",
        "dn: uid=user.3,ou=People,dc=test,dc=com",
        "objectClass: top",
        "objectClass: person",
        "objectClass: organizationalPerson",
        "objectClass: inetOrgPerson",
        "givenName: Aaron",
        "sn: Atrc",
        "cn: Aaron Atrc",
        "initials: AIA",
        "employeeNumber: 3",
        "uid: user.3",
        "mail: user.3@example.com",
        "userPassword: password",
        "telephoneNumber: 128-108-4939",
        "homePhone: 512-782-9966",
        "pager: 322-646-5118",
        "mobile: 360-957-9137",
        "carLicense: 360-957-9137",
        "street: 25074 Hill Street",
        "l: Toledo",
        "st: OR",
        "postalCode: 55237",
        "postalAddress: Aaron Atrc$25074 Hill Street$Toledo, OR  55237",
        "description: This is the description for Aaron Atrc.",
        "",
        "dn: uid=user.4,ou=People,dc=test,dc=com",
        "objectClass: top",
        "objectClass: person",
        "objectClass: organizationalPerson",
        "objectClass: inetOrgPerson",
        "givenName: Aartjan",
        "sn: Aalders",
        "cn: Aartjan Aalders",
        "initials: ALA",
        "employeeNumber: 4",
        "uid: user.4",
        "mail: user.4@example.com",
        "userPassword: password",
        "telephoneNumber: 981-148-3303",
        "homePhone: 196-877-2684",
        "pager: 910-998-4607",
        "mobile: 123-239-8262",
        "carLicense: 123-239-8262",
        "street: 81512 Sunset Street",
        "l: Chattanooga",
        "st: WV",
        "postalCode: 29530",
        "postalAddress: Aartjan Aalders$81512 Sunset Street$Chattanooga, WV  29530",
        "description: This is the description for Aartjan Aalders.",
        "",
        "dn: uid=user.5,ou=People,dc=test,dc=com",
        "objectClass: top",
        "objectClass: person",
        "objectClass: organizationalPerson",
        "objectClass: inetOrgPerson",
        "givenName: Abagael",
        "sn: Aasen",
        "cn: Abagael Aasen",
        "initials: AKA",
        "employeeNumber: 5",
        "uid: user.5",
        "mail: user.5@example.com",
        "userPassword: password",
        "telephoneNumber: 930-493-2391",
        "homePhone: 078-254-3960",
        "pager: 281-936-8197",
        "mobile: 559-822-7712",
        "carLicense: 559-822-7712",
        "street: 31988 Central Street",
        "l: Chico",
        "st: MS",
        "postalCode: 20135",
        "postalAddress: Abagael Aasen$31988 Central Street$Chico, MS  20135",
        "description: This is the description for Abagael Aasen.",
        "",
        "dn: uid=user.6,ou=People,dc=test,dc=com",
        "objectClass: top",
        "objectClass: person",
        "objectClass: organizationalPerson",
        "objectClass: inetOrgPerson",
        "givenName: Abagail",
        "sn: Abadines",
        "cn: Abagail Abadines",
        "initials: AQA",
        "employeeNumber: 6",
        "uid: user.6",
        "mail: user.6@example.com",
        "userPassword: password",
        "telephoneNumber: 110-761-3861",
        "homePhone: 459-123-0553",
        "pager: 799-151-2688",
        "mobile: 953-582-7252",
        "carLicense: 953-582-7252",
        "street: 60100 Dogwood Street",
        "l: Hartford",
        "st: NE",
        "postalCode: 79353",
        "postalAddress: Abagail Abadines$60100 Dogwood Street$Hartford, NE  79353",
        "description: This is the description for Abagail Abadines.",
        "",
        "dn: uid=user.7,ou=People,dc=test,dc=com",
        "objectClass: top",
        "objectClass: person",
        "objectClass: organizationalPerson",
        "objectClass: inetOrgPerson",
        "givenName: Abahri",
        "sn: Abazari",
        "cn: Abahri Abazari",
        "initials: AXA",
        "employeeNumber: 7",
        "uid: user.7",
        "mail: user.7@example.com",
        "userPassword: password",
        "telephoneNumber: 594-537-4292",
        "homePhone: 174-724-6390",
        "pager: 733-217-8194",
        "mobile: 879-706-0172",
        "carLicense: 879-706-0172",
        "street: 77693 Oak Street",
        "l: Philadelphia",
        "st: MN",
        "postalCode: 78550",
        "postalAddress: Abahri Abazari$77693 Oak Street$Philadelphia, MN  78550",
        "description: This is the description for Abahri Abazari.",
        "",
        "dn: uid=user.8,ou=People,dc=test,dc=com",
        "objectClass: top",
        "objectClass: person",
        "objectClass: organizationalPerson",
        "objectClass: inetOrgPerson",
        "givenName: Abbas",
        "sn: Abbatantuono",
        "cn: Abbas Abbatantuono",
        "initials: AVA",
        "employeeNumber: 8",
        "uid: user.8",
        "mail: user.8@example.com",
        "userPassword: password",
        "telephoneNumber: 246-674-8407",
        "homePhone: 039-769-3372",
        "pager: 226-950-2371",
        "mobile: 587-709-2996",
        "carLicense: 587-709-2996",
        "street: 23230 Hill Street",
        "l: Little Rock",
        "st: AR",
        "",
        "dn: uid=user.9,ou=People,dc=test,dc=com",
        "objectClass: top",
        "objectClass: person",
        "objectClass: organizationalPerson",
        "objectClass: inetOrgPerson",
        "givenName: Abbe",
        "sn: Abbate",
        "cn: Abbe Abbate",
        "initials: AWA",
        "employeeNumber: 9",
        "uid: user.9",
        "mail: user.9@example.com",
        "userPassword: password",
        "telephoneNumber: 205-805-3357",
        "homePhone: 770-780-5917",
        "pager: 537-074-8005",
        "mobile: 120-204-7597",
        "carLicense: 120-204-7597",
        "street: 47952 Center Street",
        "l: Butte",
        "st: TN",
        "postalCode: 69384",
        "postalAddress: Abbe Abbate$47952 Center Street$Butte, TN  69384",
        "description: This is the description for Abbe Abbate.",
        "",
        "dn: uid=user.10,ou=People,dc=test,dc=com",
        "objectClass: top",
        "objectClass: person",
        "objectClass: organizationalPerson",
        "objectClass: inetOrgPerson",
        "givenName: Abbey",
        "sn: Abbie",
        "cn: Abbey Abbie",
        "initials: AZA",
        "employeeNumber: 10",
        "uid: user.10",
        "mail: user.10@example.com",
        "userPassword: password",
        "telephoneNumber: 457-819-0832",
        "homePhone: 931-305-5452",
        "pager: 118-165-7194",
        "mobile: 553-729-5572",
        "carLicense: 553-729-5572",
        "street: 54262 Highland Street",
        "l: Spartanburg",
        "st: PA",
        "postalCode: 38151",
        "postalAddress: Abbey Abbie$54262 Highland Street$Spartanburg, PA  38151",
        "description: This is the description for Abbey Abbie.",
        "",
        "dn: uid=user.539,ou=People,dc=test,dc=com",
        "objectClass: top",
        "objectClass: person",
        "objectClass: organizationalPerson",
        "objectClass: inetOrgPerson",
        "givenName: Ardyth",
        "sn: Bainton",
        "cn: Ardyth Bainton",
        "initials: AIB",
        "employeeNumber: 539",
        "uid: user.539",
        "mail: user.539@example.com",
        "userPassword: password",
        "telephoneNumber: 641-433-7404",
        "homePhone: 524-765-8780",
        "pager: 985-331-1308",
        "mobile: 279-423-0188",
        "carLicense: 279-423-0188",
        "street: 81170 Taylor Street",
        "l: Syracuse",
        "st: WV",
        "postalCode: 93507",
        "postalAddress: Ardyth Bainton$81170 Taylor Street$Syracuse, WV  93507",
        "description: This is the description for Ardyth Bainton.",
        "",
        "dn: uid=user.446,dc=test1,dc=com",
        "objectClass: top",
        "objectClass: person",
        "objectClass: organizationalPerson",
        "objectClass: inetOrgPerson",
        "givenName: Annalee",
        "sn: Avard",
        "cn: Annalee Avard",
        "initials: ANA",
        "employeeNumber: 446",
        "uid: user.446",
        "mail: user.446@example.com",
        "userPassword: password",
        "telephoneNumber: 875-335-2712",
        "homePhone: 181-995-6635",
        "pager: 586-905-4185",
        "mobile: 826-857-7592",
        "carLicense: 826-857-7592",
        "street: 46168 Mill Street",
        "l: Charleston",
        "st: CO",
        "postalCode: 60948",
        "postalAddress: Annalee Avard$46168 Mill Street$Charleston, CO  60948",
        "description: This is the description for Annalee Avard.",
        "",
        "dn: uid=user.362,dc=test1,dc=com",
        "objectClass: top",
        "objectClass: person",
        "objectClass: organizationalPerson",
        "objectClass: inetOrgPerson",
        "givenName: Andaree",
        "sn: Asawa",
        "cn: Andaree Asawa",
        "initials: AEA",
        "employeeNumber: 362",
        "uid: user.362",
        "mail: user.362@example.com",
        "userPassword: password",
        "telephoneNumber: 399-788-7334",
        "homePhone: 798-076-5683",
        "pager: 034-026-9411",
        "mobile: 948-743-9197",
        "carLicense: 948-743-9197",
        "street: 81028 Forest Street",
        "l: Wheeling",
        "st: IA",
        "postalCode: 60905",
        "postalAddress: Andaree Asawa$81028 Forest Street$Wheeling, IA  60905",
        "description: This is the description for Andaree Asawa.");

    replaceEntry = TestCaseUtils.makeEntry(
        "dn: uid=user.0,ou=People,dc=test,dc=com",
        "objectClass: top",
        "objectClass: person",
        "objectClass: organizationalPerson",
        "objectClass: inetOrgPerson",
        "givenName: Testing",
        "sn: Test",
        "cn: Testing Test",
        "initials: TT",
        "employeeNumber: 777",
        "uid: user.0",
        "mail: user.0@example.com",
        "userPassword: password",
        "telephoneNumber: 380-535-2354",
        "homePhone: 707-626-3913",
        "pager: 456-345-7750",
        "mobile: 366-674-7274",
        "carLicense: 366-674-7274",
        "street: 99262 Eleventh Street",
        "l: Salem",
        "st: NM",
        "postalCode: 36530",
        "postalAddress: Aaccf Amar$99262 Eleventh Street$Salem, NM  36530",
        "description: This is the description for Aaccf Amar.");

    additionalEntries = TestCaseUtils.makeEntries(
        "dn: uid=user.446,ou=People,dc=test,dc=com",
        "objectClass: top",
        "objectClass: person",
        "objectClass: organizationalPerson",
        "objectClass: inetOrgPerson",
        "givenName: Annalee",
        "sn: Avard",
        "cn: Annalee Avard",
        "initials: ANA",
        "employeeNumber: 446",
        "uid: user.446",
        "mail: user.446@example.com",
        "userPassword: password",
        "telephoneNumber: 875-335-2712",
        "homePhone: 181-995-6635",
        "pager: 586-905-4185",
        "mobile: 826-857-7592",
        "carLicense: 826-857-7592",
        "street: 46168 Mill Street",
        "l: Charleston",
        "st: CO",
        "postalCode: 60948",
        "postalAddress: Annalee Avard$46168 Mill Street$Charleston, CO  60948",
        "description: This is the description for Annalee Avard.",
        "",
        "dn: uid=user.362,ou=People,dc=test,dc=com",
        "objectClass: top",
        "objectClass: person",
        "objectClass: organizationalPerson",
        "objectClass: inetOrgPerson",
        "givenName: Andaree",
        "sn: Asawa",
        "cn: Andaree Asawa",
        "initials: AEA",
        "employeeNumber: 362",
        "uid: user.362",
        "mail: user.362@example.com",
        "userPassword: password",
        "telephoneNumber: 399-788-7334",
        "homePhone: 798-076-5683",
        "pager: 034-026-9411",
        "mobile: 948-743-9197",
        "carLicense: 948-743-9197",
        "street: 81028 Forest Street",
        "l: Wheeling",
        "st: IA",
        "postalCode: 60905",
        "postalAddress: Andaree Asawa$81028 Forest Street$Wheeling, IA  60905",
        "description: This is the description for Andaree Asawa.");

    newTop = TestCaseUtils.makeEntry(
        "dn: ou=JEB Testers,dc=test,dc=com",
        "objectclass: top",
        "objectclass: organizationalUnit",
        "ou: People"
    );

  }

  @AfterClass
  public void cleanUp() throws Exception {
    TestCaseUtils.disableBackend("importRoot");
  }

  @Test(expectedExceptions = DirectoryException.class)
  public void testAddNoParent() throws Exception {
    for (Entry entry : entries) {
      backend.addEntry(entry, null);
    }
  }

  @Test(dependsOnMethods = "testAddNoParent")
  public void testAdd() throws Exception {
    for (Entry topEntry : topEntries) {
      backend.addEntry(topEntry, null);
      assertNotNull(backend.getEntry(topEntry.getName()));
    }

    for (Entry entry : entries) {
      backend.addEntry(entry, null);
      assertNotNull(backend.getEntry(entry.getName()));
    }
  }

  @Test(dependsOnMethods = "testAdd")
  public void testSearchScope() throws Exception {
    InternalClientConnection conn = getRootConnection();

    DN dn = DN.valueOf("dc=test,dc=com");
    InternalSearchOperation search = conn.processSearch(newSearchRequest(dn, SearchScope.BASE_OBJECT));
    List<SearchResultEntry> result = search.getSearchEntries();

    assertEquals(result.size(), 1);
    assertEquals(result.get(0).getName(), dn);

    search = conn.processSearch(newSearchRequest(dn, SearchScope.BASE_OBJECT, "(ou=People)"));
    result = search.getSearchEntries();

    assertEquals(result.size(), 0);

    search = conn.processSearch(newSearchRequest(dn, SearchScope.SINGLE_LEVEL));
    result = search.getSearchEntries();

    assertEquals(result.size(), 1);
    assertEquals(result.get(0).getName().toString(),
        "ou=People,dc=test,dc=com");

    search = conn.processSearch(newSearchRequest(dn, SearchScope.SUBORDINATES));
    result = search.getSearchEntries();

    assertEquals(result.size(), 13);
    for (Entry entry : result) {
      assertThat(entry.getName()).isNotEqualTo(dn);
    }

    search = conn.processSearch(newSearchRequest(dn, SearchScope.WHOLE_SUBTREE));
    result = search.getSearchEntries();

    assertEquals(result.size(), 14);
  }

  @Test(dependsOnMethods = "testAdd")
  public void testNumSubordinates() throws Exception
  {
    DN dn = DN.valueOf("dc=test,dc=com");
    assertEquals(backend.numSubordinates(dn, false), 1);
    assertEquals(backend.numSubordinates(dn, true), 13);
    dn = DN.valueOf("ou=People,dc=test,dc=com");
    assertEquals(backend.numSubordinates(dn, false), 12);
    assertEquals(backend.numSubordinates(dn, true), 12);
    dn = DN.valueOf("dc=com");
    assertEquals(backend.numSubordinates(dn, false), -1);
    assertEquals(backend.numSubordinates(dn, true), -1);
    dn = DN.valueOf("dc=test1,dc=com");
    assertEquals(backend.numSubordinates(dn, false), 2);
    assertEquals(backend.numSubordinates(dn, true), 2);
    dn = DN.valueOf("uid=user.10,ou=People,dc=test,dc=com");
    assertEquals(backend.numSubordinates(dn, false), 0);
    assertEquals(backend.numSubordinates(dn, true), 0);
    dn = DN.valueOf("uid=does not exist,ou=People,dc=test,dc=com");
    assertEquals(backend.numSubordinates(dn, false), -1);
    assertEquals(backend.numSubordinates(dn, true), -1);
  }

  @Test(dependsOnMethods = "testAdd")
  public void testSearchIndex() throws Exception {
    Set<String> attribs = new LinkedHashSet<String>();
    String debugString;
    List<SearchResultEntry> result;

    // search 1
    result = doSubtreeSearch("(&(cn=Aaccf Amar)(cn=Ardyth Bainton))", attribs);
    assertEquals(result.size(), 0);

    // Adding a debug search attribute for next searches
    attribs.add(ATTR_DEBUG_SEARCH_INDEX);

    // search 2
    result = doSubtreeSearch("(&(cn=Aaccf Amar)(employeeNumber=222))", attribs);

    // Only one index should be used because it is below the FILTER_CANDIDATE
    debugString = getDebugString(result);
    assertTrue(debugString.split("cn").length <= 3);
    assertResultsCountIs(1, debugString);

    // search 3
    result = doSubtreeSearch("(|(cn=Aaccf Amar)(cn=Ardyth Bainton))", attribs);

    debugString = getDebugString(result);
    assertThat(debugString).doesNotContain("NOT-INDEXED");
    assertResultsCountIs(2, debugString);

    // search 4
    result = doSubtreeSearch("(&(employeeNumber=*)(cn=A*)(employeeNumber>=0)(employeeNumber<=z))", attribs);

    debugString = getDebugString(result);
    assertThat(debugString).doesNotContain("NOT-INDEXED");
    assertResultsCountIs(12, debugString);

    // search 5
    result = doSubtreeSearch("(&(employeeNumber<=z)(cn<=Abbey Abbie)(cn>=0)(|(cn>=Abahri Abazari)(employeeNumber<=9)))",
        attribs);

    debugString = getDebugString(result);
    assertThat(debugString).doesNotContain("NOT-INDEXED");
    assertResultsCountIs(11, debugString);

    // search 6
    result = doSubtreeSearch("(cn~=Aartjan)", attribs);

    debugString = getDebugString(result);
    assertThat(debugString).doesNotContain("NOT-INDEXED");
    assertResultsCountIs(1, debugString);
  }

  private void assertResultsCountIs(int expectedCount, String debugString)
  {
    int finalStartPos = debugString.indexOf("final=") + 13;
    int finalEndPos = debugString.indexOf("]", finalStartPos);
    int finalCount = Integer.valueOf(debugString.substring(finalStartPos, finalEndPos));
    assertEquals(finalCount, expectedCount);
  }

  /** Returns the debug string from a search result. */
  private String getDebugString(List<SearchResultEntry> result)
  {
    return result.get(0).getAttribute("debugsearchindex").get(0).toString();
  }

  /** Returns the results of subtree search on provided connection with provided filter. */
  private List<SearchResultEntry> doSubtreeSearch(String filter, Set<String> attribs) throws Exception
  {
    final SearchRequest request =
        newSearchRequest("dc=test,dc=com", SearchScope.WHOLE_SUBTREE, filter).addAttribute(attribs);
    InternalSearchOperation search = getRootConnection().processSearch(request);
    return search.getSearchEntries();
  }

  @Test(dependsOnMethods = {"testAdd", "testSearchIndex",
      "testSearchScope", "testSearchNotIndexed", "testModifyDNNewSuperior",
      "testMatchedDN", "testNumSubordinates",
      "testNumSubordinatesIndexEntryLimitExceeded"})
  public void testDeleteSubtree() throws Exception {
    Control control = new SubtreeDeleteControl(false);
    List<Control> deleteSubTreeControl = Collections.singletonList(control);

    DeleteOperationBasis delete = new DeleteOperationBasis(
        getRootConnection(), nextOperationID(), nextMessageID(),
        deleteSubTreeControl,
        DN.valueOf("dc=test1,dc=com"));

    backend.deleteEntry(DN.valueOf("dc=test1,dc=com"), delete);

    EntryContainer ec =
        backend.getRootContainer().getEntryContainer(DN.valueOf("dc=test1,dc=com"));
    ec.sharedLock.lock();
    try
    {
      assertFalse(ec.entryExists(DN.valueOf("dc=test1,dc=com")));
      assertFalse(ec.entryExists(DN.valueOf("uid=user.362,dc=test1,dc=com")));
    }
    finally
    {
      ec.sharedLock.unlock();
    }
  }

  @Test(dependsOnMethods = {"testAdd", "testSearchIndex",
      "testSearchScope", "testMatchedDN"})
  public void testDeleteEntry() throws Exception {
    List<Control> noControls = new ArrayList<Control>(0);
    EntryContainer ec =
        backend.getRootContainer().getEntryContainer(DN.valueOf("ou=People,dc=test,dc=com"));

    ec.sharedLock.lock();
    try
    {
      Entry entry =
          ec.getEntry(DN.valueOf("uid=user.539,ou=People,dc=test,dc=com"));
      EntryID entryID = ec.getDN2ID().get(null,
          DN.valueOf("uid=user.539,ou=People,dc=test,dc=com"), LockMode.DEFAULT);

      DeleteOperationBasis delete = new DeleteOperationBasis(
          getRootConnection(), nextOperationID(), nextMessageID(),
          noControls,
          DN.valueOf("uid=user.539,ou=People,dc=test,dc=com"));
      backend.deleteEntry(DN.valueOf("uid=user.539,ou=People,dc=test,dc=com"), delete);


      assertFalse(ec.entryExists(DN.valueOf("uid=user.539,ou=People,dc=test,dc=com")));
      assertNull(ec.getDN2ID().get(null,
          DN.valueOf("uid=user.539,ou=People,dc=test,dc=com"), LockMode.DEFAULT));
      assertFalse(ec.getDN2URI().delete(null,
          DN.valueOf("uid=user.539,ou=People,dc=test,dc=com")));

      AttributeType attribute = entries.get(0).getAttribute("cn").get(0).getAttributeType();
      AttributeIndex index = ec.getAttributeIndex(attribute);
      AttributeType attrType = index.getAttributeType();

      List<? extends Indexer> indexers;
      indexers = singletonList(new PresenceIndexer(index.getAttributeType()));
      assertIndexContainsID(indexers, entry, index.getPresenceIndex(), entryID, FALSE);

      indexers = newJEExtensibleIndexers(attrType, attrType.getEqualityMatchingRule());
      assertIndexContainsID(indexers, entry, index.getEqualityIndex(), entryID, FALSE);

      indexers = newJEExtensibleIndexers(attrType, attrType.getSubstringMatchingRule());
      assertIndexContainsID(indexers, entry, index.getSubstringIndex(), entryID, FALSE);

      indexers = newJEExtensibleIndexers(attrType, attrType.getOrderingMatchingRule());
      assertIndexContainsID(indexers, entry, index.getOrderingIndex(), entryID, FALSE);
    }
    finally
    {
      ec.sharedLock.unlock();
    }
  }

  private List<JEExtensibleIndexer> newJEExtensibleIndexers(AttributeType attrType, MatchingRule matchingRule)
  {
    List<JEExtensibleIndexer> extIndexers = new ArrayList<JEExtensibleIndexer>();
    for (org.forgerock.opendj.ldap.spi.Indexer indexer : matchingRule.getIndexers())
    {
      extIndexers.add(new JEExtensibleIndexer(attrType, indexer));
    }
    return extIndexers;
  }

  private IndexingOptions getOptions()
  {
    final IndexingOptions options = mock(IndexingOptions.class);
    when(options.substringKeySize()).thenReturn(6);
    return options;
  }

  private void assertIndexContainsID(List<? extends Indexer> indexers, Entry entry, Index index, EntryID entryID)
  {
    for (Indexer indexer : indexers)
    {
      Set<ByteString> addKeys = new HashSet<ByteString>();
      indexer.indexEntry(entry, addKeys, getOptions());

      DatabaseEntry key = new DatabaseEntry();
      for (ByteString keyBytes : addKeys)
      {
        key.setData(keyBytes.toByteArray());
        assertEquals(index.containsID(null, key, entryID), TRUE);
      }
    }
  }

  private void assertIndexContainsID(List<? extends Indexer> indexers, Entry entry,
      Index index, EntryID entryID, ConditionResult expected)
  {
    for (Indexer indexer : indexers)
    {
      Set<ByteString> addKeys = new HashSet<ByteString>();
      indexer.indexEntry(entry, addKeys, getOptions());

      assertIndexContainsID(addKeys, index, entryID, expected);
    }
  }

  private void assertIndexContainsID(Set<ByteString> addKeys, Index index,
      EntryID entryID, ConditionResult expected)
  {
    DatabaseEntry key = new DatabaseEntry();
    for (ByteString keyBytes : addKeys)
    {
      key.setData(keyBytes.toByteArray());
      assertEquals(index.containsID(null, key, entryID), expected);
    }
  }

  @Test(dependsOnMethods = {"testSearchNotIndexed", "testAdd",
      "testSearchIndex", "testSearchScope", "testMatchedDN",
      "testNumSubordinates", "testNumSubordinatesIndexEntryLimitExceeded"})
  public void testReplaceEntry() throws Exception {
    Entry oldEntry = entries.get(0);
    backend.replaceEntry(oldEntry, replaceEntry, null);

    EntryContainer ec =
        backend.getRootContainer().getEntryContainer(DN.valueOf("dc=test,dc=com"));
    ec.sharedLock.lock();
    try
    {
      Entry entry =
          ec.getEntry(DN.valueOf("uid=user.0,ou=People,dc=test,dc=com"));
      EntryID entryID = ec.getDN2ID().get(null,
          DN.valueOf("uid=user.0,ou=People,dc=test,dc=com"), LockMode.DEFAULT);

      assertNotNull(entry);
      for (ByteString value : entry.getAttribute("cn").get(0)) {
        assertEquals(value.toString(), "Testing Test");
      }
      for (ByteString value : entry.getAttribute("sn").get(0)) {
        assertEquals(value.toString(), "Test");
      }
      for (ByteString value : entry.getAttribute("givenname").get(0)) {
        assertEquals(value.toString(), "Testing");
      }
      for (ByteString value : entry.getAttribute("employeenumber").get(0)) {
        assertEquals(value.toString(), "777");
      }

      AttributeType attribute = entry.getAttribute("cn").get(0).getAttributeType();
      AttributeIndex index = ec.getAttributeIndex(attribute);
      AttributeType attrType = index.getAttributeType();

      List<? extends Indexer> indexers;
      indexers = newJEExtensibleIndexers(attrType, attrType.getOrderingMatchingRule());
      assertIndexContainsID(indexers, entry, index.getOrderingIndex(), entryID, TRUE);
      assertIndexContainsID(indexers, oldEntry, index.getOrderingIndex(), entryID, FALSE);

      indexers = newJEExtensibleIndexers(attrType, attrType.getSubstringMatchingRule());
      assertIndexContainsID(indexers, entry, index.getSubstringIndex(), entryID, TRUE);
      assertIndexContainsID(indexers, oldEntry, index.getSubstringIndex(), entryID, FALSE);

      indexers = newJEExtensibleIndexers(attrType, attrType.getEqualityMatchingRule());
      assertIndexContainsID(indexers, entry, index.getEqualityIndex(), entryID, TRUE);
      assertIndexContainsID(indexers, oldEntry, index.getEqualityIndex(), entryID, FALSE);
    }
    finally
    {
      ec.sharedLock.unlock();
    }
  }

  @Test(dependsOnMethods = {"testSearchNotIndexed", "testAdd",
      "testSearchIndex", "testSearchScope", "testMatchedDN",
      "testNumSubordinates", "testNumSubordinatesIndexEntryLimitExceeded"})
  public void testModifyEntry() throws Exception
  {
    Entry entry;
    Entry newEntry;
    EntryID entryID;
    AttributeType attribute;
    AttributeIndex titleIndex;
    AttributeIndex nameIndex;
    Set<ByteString> addKeys;
    List<? extends Indexer> indexers;

    EntryContainer ec = backend.getRootContainer().getEntryContainer(
        DN.valueOf("dc=test,dc=com"));
    ec.sharedLock.lock();
    try
    {
      List<Modification> modifications = new ArrayList<Modification>();
      modifications.add(new Modification(ADD, create("title", "debugger")));

      AttributeBuilder builder = new AttributeBuilder("title");
      builder.setOption("lang-en");
      builder.add("debugger2");

      modifications.add(new Modification(ADD, builder.toAttribute()));
      modifications.add(new Modification(DELETE, create("cn", "Aaren Atp")));
      modifications.add(new Modification(ADD, create("cn", "Aaren Rigor")));
      modifications.add(new Modification(ADD, create("cn", "Aarenister Rigor")));

      builder = new AttributeBuilder("givenname");
      builder.add("test");
      builder.setOption("lang-de");
      modifications.add(new Modification(ADD, builder.toAttribute()));

      builder = new AttributeBuilder("givenname");
      builder.add("test2");
      builder.setOption("lang-cn");
      modifications.add(new Modification(DELETE, builder.toAttribute()));

      builder = new AttributeBuilder("givenname");
      builder.add("newtest3");
      builder.setOption("lang-es");
      modifications.add(new Modification(REPLACE, builder.toAttribute()));
      modifications.add(new Modification(REPLACE, create("employeenumber", "222")));

      newEntry = entries.get(1);
      newEntry.applyModifications(modifications);
      entry = ec.getEntry(DN.valueOf("uid=user.1,ou=People,dc=test,dc=com"));
      entryID = ec.getDN2ID().get(null,
          DN.valueOf("uid=user.1,ou=People,dc=test,dc=com"), LockMode.DEFAULT);

      assertNotNull(entryID);

      attribute = DirectoryServer.getAttributeType("title");
      titleIndex = ec.getAttributeIndex(attribute);
      attribute = DirectoryServer.getAttributeType("name");
      nameIndex = ec.getAttributeIndex(attribute);

      // This current entry in the DB shouldn't be in the presence titleIndex.
      addKeys = new HashSet<ByteString>();
      addKeys.add(ByteString.wrap(AttributeIndex.presenceKey.getData()));
      assertIndexContainsID(addKeys, titleIndex.getPresenceIndex(), entryID, FALSE);

      // This current entry should be in the presence nameIndex.
      addKeys = new HashSet<ByteString>();
      addKeys.add(ByteString.wrap(AttributeIndex.presenceKey.getData()));
      assertIndexContainsID(addKeys, nameIndex.getPresenceIndex(), entryID, TRUE);

      List<Control> noControls = new ArrayList<Control>(0);
      ModifyOperationBasis modifyOp = new ModifyOperationBasis(getRootConnection(), nextOperationID(), nextMessageID(),
          noControls, DN.valueOf("uid=user.1,ou=People,dc=test,dc=com"), modifications);

      backend.replaceEntry(entry, newEntry, modifyOp);

      entry = ec.getEntry(DN.valueOf("uid=user.1,ou=People,dc=test,dc=com"));

      assertTrue(entry.getAttribute("title").contains(
          Attributes.create("title", "debugger")));

      final Attribute cnAttr = entry.getAttribute("cn").get(0);
      assertTrue(cnAttr.contains(ByteString.valueOf("Aaren Rigor")));
      assertTrue(cnAttr.contains(ByteString.valueOf("Aarenister Rigor")));
      assertFalse(cnAttr.contains(ByteString.valueOf("Aaren Atp")));

      Set<String> options = Collections.singleton("lang-de");
      assertTrue(entry.getAttribute("givenname", options).get(0).contains(
          ByteString.valueOf("test")));
      options = Collections.singleton("lang-cn");
      assertNull(entry.getAttribute("givenname", options));
      options = Collections.singleton("lang-es");
      assertTrue(entry.getAttribute("givenname", options).get(0).contains(
          ByteString.valueOf("newtest3")));
      options = Collections.singleton("lang-fr");
      assertTrue(entry.getAttribute("givenname", options).get(0).contains(
          ByteString.valueOf("test2")));

      assertTrue(entry.getAttribute("employeenumber").contains(
          Attributes.create("employeenumber", "222")));
      assertFalse(entry.getAttribute("employeenumber").contains(
          Attributes.create("employeenumber", "1")));

      AttributeType titleIndexAttrType = titleIndex.getAttributeType();
      AttributeType nameIndexAttrType = nameIndex.getAttributeType();

      indexers = singletonList(new PresenceIndexer(titleIndexAttrType));
      assertIndexContainsID(indexers, entry, titleIndex.getPresenceIndex(), entryID);
      indexers = singletonList(new PresenceIndexer(nameIndexAttrType));
      assertIndexContainsID(indexers, entry, nameIndex.getPresenceIndex(), entryID);

      indexers = newJEExtensibleIndexers(titleIndexAttrType, titleIndexAttrType.getOrderingMatchingRule());
      assertIndexContainsID(indexers, entry, titleIndex.getOrderingIndex(), entryID);
      indexers = newJEExtensibleIndexers(nameIndexAttrType, nameIndexAttrType.getOrderingMatchingRule());
      assertIndexContainsID(indexers, entry, nameIndex.getOrderingIndex(), entryID);

      indexers = newJEExtensibleIndexers(titleIndexAttrType, titleIndexAttrType.getEqualityMatchingRule());
      assertIndexContainsID(indexers, entry, titleIndex.getEqualityIndex(), entryID);
      indexers = newJEExtensibleIndexers(nameIndexAttrType, nameIndexAttrType.getEqualityMatchingRule());
      assertIndexContainsID(indexers, entry, nameIndex.getEqualityIndex(), entryID);

      indexers = newJEExtensibleIndexers(titleIndexAttrType, titleIndexAttrType.getSubstringMatchingRule());
      assertIndexContainsID(indexers, entry, titleIndex.getSubstringIndex(), entryID);
      indexers = newJEExtensibleIndexers(nameIndexAttrType, nameIndexAttrType.getSubstringMatchingRule());
      assertIndexContainsID(indexers, entry, nameIndex.getSubstringIndex(), entryID);
    }
    finally
    {
      ec.sharedLock.unlock();
    }
  }

  @Test(dependsOnMethods = {"testAdd", "testSearchIndex", "testSearchScope",
      "testMatchedDN"})
  public void testModifyDN() throws Exception {
    EntryContainer ec =
        backend.getRootContainer().getEntryContainer(DN.valueOf("dc=test,dc=com"));
    ec.sharedLock.lock();
    try
    {
      DN user2Dn = DN.valueOf("uid=user.2,ou=People,dc=test,dc=com");
      DN abbieDn = DN.valueOf("cn=Abbey Abbie,ou=People,dc=test,dc=com");
      Entry entry = ec.getEntry(user2Dn);
      entry.setDN(abbieDn);

      backend.renameEntry(user2Dn, entry, null);

      assertNotNull(backend.getEntry(abbieDn));
      assertNotNull(ec.getDN2ID().get(null, abbieDn, LockMode.DEFAULT));

      assertNull(backend.getEntry(user2Dn));
      assertNull(ec.getDN2ID().get(null, user2Dn, LockMode.DEFAULT));
    }
    finally
    {
      ec.sharedLock.unlock();
    }
  }

  @Test(dependsOnMethods = {"testSearchNotIndexed", "testAdd", "testSearchIndex",
      "testSearchScope", "testModifyEntry", "testModifyDN", "testReplaceEntry",
      "testDeleteEntry", "testMatchedDN", "testNumSubordinates",
      "testNumSubordinatesIndexEntryLimitExceeded"})
  public void testModifyDNNewSuperior() throws Exception {
    //Add the new superior entry we want to move to. Test to see if the child ID
    //always above parent invarient is preseved.
    backend.addEntry(newTop, null);

    EntryContainer ec =
        backend.getRootContainer().getEntryContainer(DN.valueOf("dc=test,dc=com"));
    ec.sharedLock.lock();
    try
    {
      EntryID newSuperiorID = ec.getDN2ID().get(null, DN.valueOf("ou=JEB Testers,dc=test,dc=com"), LockMode.DEFAULT);
      EntryID oldID = ec.getDN2ID().get(null,
          DN.valueOf("ou=People,dc=test,dc=com"), LockMode.DEFAULT);
      assertTrue(newSuperiorID.compareTo(oldID) > 0);

      List<Control> noControls = new ArrayList<Control>(0);
      ModifyDNOperationBasis modifyDN = new ModifyDNOperationBasis(
          getRootConnection(), nextOperationID(), nextMessageID(),
          noControls,
          DN.valueOf("ou=People,dc=test,dc=com"),
          RDN.decode("ou=Good People"),
          false,
          DN.valueOf("ou=JEB Testers,dc=test,dc=com"));

      modifyDN.run();

      assertNotNull(backend.getEntry(DN.valueOf("ou=Good People,ou=JEB Testers,dc=test,dc=com")));
      EntryID newID = ec.getDN2ID().get(null, DN.valueOf("ou=Good People,ou=JEB Testers,dc=test,dc=com"), LockMode.DEFAULT);
      assertNotNull(newID);
      assertTrue(newID.compareTo(newSuperiorID) > 0);
      DN subDN = DN.valueOf("uid=user.0,ou=Good People,ou=JEB Testers,dc=test,dc=com");
      Entry subEntry = backend.getEntry(subDN);
      assertNotNull(subEntry);
      assertEquals(subDN, subEntry.getName());
      EntryID newSubordinateID = ec.getDN2ID().get(null, subDN, LockMode.DEFAULT);
      assertTrue(newSubordinateID.compareTo(newID) > 0);

      assertNull(backend.getEntry(DN.valueOf("ou=People,dc=test,dc=com")));
      assertNull(ec.getDN2ID().get(null,
          DN.valueOf("ou=People,dc=test,dc=com"), LockMode.DEFAULT));
    }
    finally
    {
      ec.sharedLock.unlock();
    }
  }

  @Test(dependsOnMethods = {"testModifyDN",
      "testSearchScope", "testSearchIndex", "testReplaceEntry",
      "testModifyEntry", "testModifyDN", "testDeleteSubtree",
      "testDeleteEntry", "testAddNoParent", "testAdd",
      "testSearchNotIndexed",
      "testModifyDNNewSuperior", "testApplyIndexConfig", "testMatchedDN"})
  public void testApplyConfig() throws Exception {
    Entry configEntry = TestCaseUtils.makeEntry(
        "dn: ds-cfg-backend-id=indexRoot,cn=Backends,cn=config",
        "objectClass: top",
        "objectClass: ds-cfg-backend",
        "objectClass: ds-cfg-local-db-backend",
        "ds-cfg-base-dn: dc=test,dc=com",
        "ds-cfg-base-dn: dc=newsuffix,dc=com",
        "ds-cfg-enabled: true",
        "ds-cfg-writability-mode: enabled",
        "ds-cfg-java-class: org.opends.server.backends.jeb.BackendImpl",
        "ds-cfg-backend-id: indexRoot",
        "ds-cfg-db-directory:: " +
            Base64.encode(homeDirName.getBytes()),
        "ds-cfg-import-temp-directory: import-tmp");

    LocalDBBackendCfg cfg = AdminTestCaseUtils.getConfiguration(
         LocalDBBackendCfgDefn.getInstance(), configEntry);

    backend.applyConfigurationChange(cfg);

    RootContainer rootContainer = backend.getRootContainer();

    assertNull(rootContainer.getEntryContainer(DN.valueOf("dc=test1,dc=com")));

    assertNotNull(rootContainer.getEntryContainer(DN.valueOf("dc=newsuffix,dc=com")));
  }

  @Test(dependsOnMethods = {"testModifyDN",
      "testSearchScope", "testSearchIndex", "testReplaceEntry",
      "testModifyEntry", "testModifyDN", "testDeleteSubtree",
      "testDeleteEntry", "testAddNoParent", "testAdd",
      "testSearchNotIndexed",
      "testModifyDNNewSuperior", "testMatchedDN"})
  public void testApplyIndexConfig() throws Exception {
    int resultCode = TestCaseUtils.applyModifications(true,
        "dn: ds-cfg-attribute=givenName,cn=Index," +
            "ds-cfg-backend-id=indexRoot,cn=Backends,cn=config",
        "changetype: modify",
        "replace: ds-cfg-index-type",
        "ds-cfg-index-type: approximate");

    assertEquals(resultCode, 0);

    RootContainer rootContainer = backend.getRootContainer();
    EntryContainer ec = rootContainer.getEntryContainer(DN.valueOf("dc=test,dc=com"));

    AttributeType givennameAttr = DirectoryServer.getAttributeType("givenname");
    AttributeIndex attributeIndex = ec.getAttributeIndex(givennameAttr);
    assertNull(attributeIndex.getEqualityIndex());
    assertNull(attributeIndex.getPresenceIndex());
    assertNull(attributeIndex.getSubstringIndex());
    assertNull(attributeIndex.getOrderingIndex());
    assertNotNull(attributeIndex.getApproximateIndex());
    List<DatabaseContainer> databases = new ArrayList<DatabaseContainer>();
    ec.listDatabases(databases);
    assertFalse(findContainer(databases, "givenname.equality"));
    assertFalse(findContainer(databases, "givenname.presence"));
    assertFalse(findContainer(databases, "givenname.substring"));
    assertFalse(findContainer(databases, "givenname.ordering"));
    assertTrue(findContainer(databases, "givenname.approximate"));

    final SearchRequest request = newSearchRequest("dc=test,dc=com", SearchScope.SUBORDINATES, "(givenName~=Aaccf)")
        .addAttribute(ATTR_DEBUG_SEARCH_INDEX);
    InternalSearchOperation search = getRootConnection().processSearch(request);
    List<SearchResultEntry> result = search.getSearchEntries();

    //No indexes should be used.
    String debugString =
        result.get(0).getAttribute("debugsearchindex").get(0).toString();
    assertThat(debugString).contains("not-indexed");

    resultCode = TestCaseUtils.applyModifications(true,
        "dn: ds-cfg-attribute=givenName,cn=Index," +
            "ds-cfg-backend-id=indexRoot,cn=Backends,cn=config",
        "changetype: modify",
        "replace: ds-cfg-index-type",
        "ds-cfg-index-type: equality",
        "ds-cfg-index-type: presence",
        "ds-cfg-index-type: ordering",
        "ds-cfg-index-type: substring");

    assertEquals(resultCode, 0);

    assertNotNull(attributeIndex.getEqualityIndex());
    assertNotNull(attributeIndex.getPresenceIndex());
    assertNotNull(attributeIndex.getSubstringIndex());
    assertNotNull(attributeIndex.getOrderingIndex());
    assertNull(attributeIndex.getApproximateIndex());
    databases = new ArrayList<DatabaseContainer>();
    ec.listDatabases(databases);
    assertTrue(findContainer(databases, "givenname.equality"));
    assertTrue(findContainer(databases, "givenname.presence"));
    assertTrue(findContainer(databases, "givenname.substring"));
    assertTrue(findContainer(databases, "givenname.ordering"));
    assertFalse(findContainer(databases, "givenname.approximate"));

    // Delete the entries attribute index.
    resultCode = TestCaseUtils.applyModifications(true,
        "dn: ds-cfg-attribute=givenName,cn=Index," +
            "ds-cfg-backend-id=indexRoot,cn=Backends,cn=config",
        "changetype: delete");

    assertEquals(resultCode, 0);

    assertNull(ec.getAttributeIndex(givennameAttr));
    databases = new ArrayList<DatabaseContainer>();
    ec.listDatabases(databases);
    for(DatabaseContainer dc : databases)
    {
      assertFalse(dc.getName().toLowerCase().contains("givenname"));
    }

    // Add it back
    resultCode = TestCaseUtils.applyModifications(true,
        "dn: ds-cfg-attribute=givenName,cn=Index," +
            "ds-cfg-backend-id=indexRoot,cn=Backends,cn=config",
        "changetype: add",
        "objectClass: top",
        "objectClass: ds-cfg-local-db-index",
        "ds-cfg-attribute: givenName",
        "ds-cfg-index-type: equality",
        "ds-cfg-index-type: presence",
        "ds-cfg-index-type: ordering",
        "ds-cfg-index-type: substring");

    assertEquals(resultCode, 0);

    assertNotNull(ec.getAttributeIndex(givennameAttr));
    databases = new ArrayList<DatabaseContainer>();
    ec.listDatabases(databases);
    assertTrue(findContainer(databases, "givenname.equality"));
    assertTrue(findContainer(databases, "givenname.presence"));
    assertTrue(findContainer(databases, "givenname.substring"));
    assertTrue(findContainer(databases, "givenname.ordering"));
    assertFalse(findContainer(databases, "givenname.approximate"));

    // Make sure changing the index entry limit on an index where the limit
    // is already exceeded causes warnings.
    resultCode = TestCaseUtils.applyModifications(true,
        "dn: ds-cfg-attribute=mail,cn=Index," +
            "ds-cfg-backend-id=indexRoot,cn=Backends,cn=config",
        "changetype: modify",
        "replace: ds-cfg-index-entry-limit",
        "ds-cfg-index-entry-limit: 30");

    assertEquals(resultCode, 0);

    // Make sure removing a index entry limit for an index makes it use the
    // backend wide setting.
    resultCode = TestCaseUtils.applyModifications(true,
        "dn: ds-cfg-attribute=mail,cn=Index," +
            "ds-cfg-backend-id=indexRoot,cn=Backends,cn=config",
        "changetype: modify",
        "delete: ds-cfg-index-entry-limit");

    assertEquals(resultCode, 0);
  }

  private boolean findContainer(List<DatabaseContainer> databases, String lowercaseName)
  {
    for (DatabaseContainer dc : databases)
    {
      if (dc.getName().toLowerCase().contains(lowercaseName))
      {
        return true;
      }
    }
    return false;
  }

  @Test(dependsOnMethods = {"testDeleteEntry", "testSearchScope",
      "testSearchIndex", "testMatchedDN"})
  public void testSearchNotIndexed() throws Exception {
    //Add 2 more entries to overflow the index entry limit.
    for (Entry entry : additionalEntries) {
      backend.addEntry(entry, null);
      assertNotNull(backend.getEntry(entry.getName()));
    }

    final SearchRequest request = newSearchRequest("dc=test,dc=com", SearchScope.SUBORDINATES, "(carLicense=377*)")
        .addAttribute(ATTR_DEBUG_SEARCH_INDEX);
    InternalSearchOperation search = getRootConnection().processSearch(request);
    List<SearchResultEntry> result = search.getSearchEntries();

    //No indexes should be used.
    String debugString =
        result.get(0).getAttribute("debugsearchindex").get(0).toString();
    assertThat(debugString).contains("not-indexed");
  }

  @Test(dependsOnMethods = "testSearchNotIndexed")
  public void testNumSubordinatesIndexEntryLimitExceeded() throws Exception
  {
    DN dn = DN.valueOf("dc=test,dc=com");
    assertEquals(backend.numSubordinates(dn, false), 1);
    assertEquals(backend.numSubordinates(dn, true), 14);

    // 1 entry was deleted and 2 added for a total of 13
    dn = DN.valueOf("ou=People,dc=test,dc=com");
    assertEquals(backend.numSubordinates(dn, false), 13);
    assertEquals(backend.numSubordinates(dn, true), 13);
    dn = DN.valueOf("dc=com");
    assertEquals(backend.numSubordinates(dn, false), -1);
    assertEquals(backend.numSubordinates(dn, true), -1);
    dn = DN.valueOf("dc=test1,dc=com");
    assertEquals(backend.numSubordinates(dn, false), 2);
    assertEquals(backend.numSubordinates(dn, true), 2);
    dn = DN.valueOf("uid=user.10,ou=People,dc=test,dc=com");
    assertEquals(backend.numSubordinates(dn, false), 0);
    assertEquals(backend.numSubordinates(dn, true), 0);
    dn = DN.valueOf("uid=does not exist,ou=People,dc=test,dc=com");
    assertEquals(backend.numSubordinates(dn, false), -1);
    assertEquals(backend.numSubordinates(dn, true), -1);
  }


  /**
   * Provides a set of DNs for the matched DN test case.
   *
   * @return set of DNs
   * @throws Exception  when DN.decode fails
   */
  @DataProvider(name = "MatchedDNs")
  public Object[][] initMatchedDNs() throws Exception {

    ResultCode success      = ResultCode.SUCCESS;
    ResultCode noSuchObject = ResultCode.NO_SUCH_OBJECT;

    DN testComDN            = DN.valueOf(                   "dc=test,dc=com");
    DN dummyTestComDN       = DN.valueOf(          "cn=dummy,dc=test,dc=com");
    DN peopleTestComDN      = DN.valueOf(         "ou=people,dc=test,dc=com");
    DN dummyPeopleTestComDN = DN.valueOf("cn=dummy,ou=people,dc=test,dc=com");

    // Sets of DNs
    return new Object[][] {
      {testComDN,            null,            success},
      {peopleTestComDN,      null,            success},
      {dummyTestComDN,       testComDN,       noSuchObject},
      {dummyPeopleTestComDN, peopleTestComDN, noSuchObject},
    };
  }


  /**
   * Executes an internal search operation and check the result code and
   * matched DN field.
   *
   * @param searchBaseDN       the search base DN to use for the current test
   * @param expectedResultCode the expected LDAP result code
   * @param expectedMatchedDN  the expected matched DN, may be <code>null</code>
   */
  @Test(dataProvider = "MatchedDNs", dependsOnMethods = "testAdd")
  public void testMatchedDN(
    DN         searchBaseDN,
    DN         expectedMatchedDN,
    ResultCode expectedResultCode
    ) throws Exception
  {
    // Test is performed with each and every scope
    for (SearchScope scope: SearchScope.values())
    {
      final SearchRequest request = newSearchRequest(searchBaseDN, scope);
      InternalSearchOperation searchOperation = getRootConnection().processSearch(request);

      assertEquals(searchOperation.getResultCode(), expectedResultCode);
      assertEquals(searchOperation.getMatchedDN(), expectedMatchedDN);
    }
  }

}

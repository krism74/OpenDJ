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
 *      Copyright 2011 ForgeRock AS
 *      Portions copyright 2012 ForgeRock AS.
 */

package org.forgerock.opendj.ldif;

import static org.fest.assertions.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyListOf;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizedIllegalArgumentException;
import org.forgerock.opendj.ldap.AttributeDescription;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.LinkedAttribute;
import org.forgerock.opendj.ldap.Modification;
import org.forgerock.opendj.ldap.ModificationType;
import org.forgerock.opendj.ldap.RDN;
import org.forgerock.opendj.ldap.requests.AddRequest;
import org.forgerock.opendj.ldap.requests.DeleteRequest;
import org.forgerock.opendj.ldap.requests.ModifyDNRequest;
import org.forgerock.opendj.ldap.requests.ModifyRequest;
import org.forgerock.opendj.ldap.schema.Schema;
import org.forgerock.opendj.ldap.schema.SchemaBuilder;
import org.forgerock.opendj.ldap.schema.SchemaValidationPolicy;
import org.forgerock.opendj.ldap.schema.SchemaValidationPolicy.Policy;
import org.testng.annotations.Test;

/**
 * This class tests the LDIFChangeRecordReader functionality.
 */
@SuppressWarnings("javadoc")
public final class LDIFChangeRecordReaderTestCase extends LDIFTestCase {

    /**
     * Provide a standard LDIF Change Record, valid, for tests below.
     *
     * @return a string containing a standard LDIF Change Record.
     */
    public final String[] getStandardLDIFChangeRecord() {

        // @formatter:off
        final String[] cr = {
            "version: 1",
            "dn: uid=scarter,ou=People,dc=example,dc=com",
            "changetype: add",
            "sn: Carter",
            "cn: Samnatha Carter",
            "givenName: Sam",
            "objectClass: inetOrgPerson",
            "telephoneNumber: 555 555-5555",
            "mail: scarter@mail.org",
            "entryDN: uid=scarter,ou=people,dc=example,dc=org",
            "entryUUID: ad55a34a-763f-358f-93f9-da86f9ecd9e4",
            "modifyTimestamp: 20120903142126Z",
            "modifiersName: cn=Internal Client,cn=Root DNs,cn=config"
        };
        // @formatter:on
        return cr;
    }

    /**
     * Test to read an LDIFChangeRecord excluding all operational attributes. In
     * this case, force to false, all attributes must be read.
     *
     * @throws Exception
     */
    @Test()
    public void testSetExcludeAllOperationalAttributesFalse() throws Exception {

        final LDIFChangeRecordReader reader =
                new LDIFChangeRecordReader(getStandardLDIFChangeRecord());

        reader.setExcludeAllOperationalAttributes(false);
        final ChangeRecord cr = reader.readChangeRecord();
        reader.close();

        assertThat(cr.getName().toString()).isEqualTo("uid=scarter,ou=People,dc=example,dc=com");

        AddRequest addRequest = (AddRequest) cr;
        assertThat(addRequest.containsAttribute("entryUUID")).isTrue();
        assertThat(addRequest.containsAttribute("entryDN")).isTrue();
        assertThat(addRequest.containsAttribute("modifyTimestamp")).isTrue();
        assertThat(addRequest.containsAttribute("modifiersName")).isTrue();
        assertThat(addRequest.containsAttribute("changetype")).isFalse();
        assertThat(addRequest.getAttributeCount()).isEqualTo(10);
    }

    /**
     * All operational attributes are excluded (true). Therefore, they musn't
     * appear in the request.
     *
     * @throws Exception
     */
    @Test()
    public void testSetExcludeAllOperationalAttributesTrue() throws Exception {

        final LDIFChangeRecordReader reader =
                new LDIFChangeRecordReader(getStandardLDIFChangeRecord());

        reader.setExcludeAllOperationalAttributes(true);
        final ChangeRecord cr = reader.readChangeRecord();
        reader.close();

        assertThat(cr.getName().toString()).isEqualTo("uid=scarter,ou=People,dc=example,dc=com");

        AddRequest addRequest = (AddRequest) cr;
        // Operational attributes are successfully excluded:
        assertThat(addRequest.containsAttribute("entryUUID")).isFalse();
        assertThat(addRequest.containsAttribute("entryDN")).isFalse();
        assertThat(addRequest.containsAttribute("modifyTimestamp")).isFalse();
        assertThat(addRequest.containsAttribute("modifiersName")).isFalse();
        assertThat(addRequest.containsAttribute("changetype")).isFalse();
        // - 4 operational
        assertThat(addRequest.getAttributeCount()).isEqualTo(6);
    }

    /**
     * All user attributes are excluded (false). The reader must fully return
     * the ldif-changes. The changetype line doesn't appear.
     *
     * @throws Exception
     */
    @Test()
    public void testSetExcludeAllUserAttributesFalse() throws Exception {

        final LDIFChangeRecordReader reader =
                new LDIFChangeRecordReader(getStandardLDIFChangeRecord());

        reader.setExcludeAllUserAttributes(false);
        final ChangeRecord cr = reader.readChangeRecord();
        reader.close();

        assertThat(cr.getName().toString()).isEqualTo("uid=scarter,ou=People,dc=example,dc=com");

        AddRequest addRequest = (AddRequest) cr;
        assertThat(addRequest.containsAttribute("sn")).isTrue();
        assertThat(addRequest.containsAttribute("givenName")).isTrue();
        assertThat(addRequest.containsAttribute("mail")).isTrue();
        assertThat(addRequest.containsAttribute("entryDN")).isTrue();
        assertThat(addRequest.containsAttribute("changetype")).isFalse();
        assertThat(addRequest.getAttributeCount()).isEqualTo(10);
    }

    /**
     * All user attributes are excluded (true). The reader must return the
     * ldif-changes without the user attributes. The changetype line doesn't
     * appear.
     *
     * @throws Exception
     */
    @Test()
    public void testSetExcludeAllUserAttributesTrue() throws Exception {

        final LDIFChangeRecordReader reader =
                new LDIFChangeRecordReader(getStandardLDIFChangeRecord());

        reader.setExcludeAllUserAttributes(true);
        final ChangeRecord cr = reader.readChangeRecord();
        reader.close();

        assertThat(cr.getName().toString()).isEqualTo("uid=scarter,ou=People,dc=example,dc=com");

        AddRequest addRequest = (AddRequest) cr;
        assertThat(addRequest.containsAttribute("sn")).isFalse();
        assertThat(addRequest.containsAttribute("givenName")).isFalse();
        assertThat(addRequest.containsAttribute("mail")).isFalse();
        assertThat(addRequest.containsAttribute("entryDN")).isTrue();
        assertThat(addRequest.containsAttribute("changetype")).isFalse();
        assertThat(addRequest.getAttributeCount()).isEqualTo(4);
    }

    /**
     * Test to read an entry with attribute exclusions. Three attributes
     * excluded, entry must contain the others.
     *
     * @throws Exception
     */
    @Test
    public void testSetExcludeAttributeWithMatch() throws Exception {

        final LDIFChangeRecordReader reader =
                new LDIFChangeRecordReader(getStandardLDIFChangeRecord());

        reader.setExcludeAttribute(AttributeDescription.valueOf("cn"));
        reader.setExcludeAttribute(AttributeDescription.valueOf("cn"));
        reader.setExcludeAttribute(AttributeDescription.valueOf("sn"));
        reader.setExcludeAttribute(AttributeDescription.valueOf("entryDN"));

        final ChangeRecord cr = reader.readChangeRecord();
        reader.close();

        assertThat(cr.getName().toString()).isEqualTo("uid=scarter,ou=People,dc=example,dc=com");

        AddRequest addRequest = (AddRequest) cr;
        assertThat(addRequest.containsAttribute("entryDN")).isFalse();
        assertThat(addRequest.containsAttribute("sn")).isFalse();
        assertThat(addRequest.containsAttribute("cn")).isFalse();
        assertThat(addRequest.containsAttribute("changetype")).isFalse();
        assertThat(addRequest.containsAttribute("mail")).isTrue();
        assertThat(addRequest.getAttributeCount()).isEqualTo(7);
    }

    /**
     * Test to read an entry with attribute exclusions. One non-existent
     * attribute is defined. Record must be complete.
     *
     * @throws Exception
     */
    @Test
    public void testSetExcludeAttributeWithNoMatch() throws Exception {
        final LDIFChangeRecordReader reader =
                new LDIFChangeRecordReader(getStandardLDIFChangeRecord());

        reader.setExcludeAttribute(AttributeDescription.valueOf("vip"));

        final ChangeRecord cr = reader.readChangeRecord();
        reader.close();

        assertThat(cr.getName().toString()).isEqualTo("uid=scarter,ou=People,dc=example,dc=com");

        AddRequest addRequest = (AddRequest) cr;
        assertThat(addRequest.containsAttribute("entryDN")).isTrue();
        assertThat(addRequest.containsAttribute("sn")).isTrue();
        assertThat(addRequest.containsAttribute("cn")).isTrue();
        assertThat(addRequest.containsAttribute("mail")).isTrue();
        assertThat(addRequest.containsAttribute("changetype")).isFalse();
        assertThat(addRequest.getAttribute("vip")).isNull();
        assertThat(addRequest.getAttributeCount()).isEqualTo(10);
    }

    /**
     * SetExcludeAttribute doesn't allow null.
     *
     * @throws Exception
     */
    @Test(expectedExceptions = NullPointerException.class)
    public void testSetExcludeAttributeDoesntAllowNull() throws Exception {

        final LDIFChangeRecordReader reader =
                new LDIFChangeRecordReader(getStandardLDIFChangeRecord());

        reader.setExcludeAttribute(null);
    }

    /**
     * Test to read an entry with attribute including.
     *
     * @throws Exception
     */
    @Test()
    public void testSetIncludeAttributeWithMatch() throws Exception {

        final LDIFChangeRecordReader reader =
                new LDIFChangeRecordReader(getStandardLDIFChangeRecord());

        reader.setIncludeAttribute(AttributeDescription.valueOf("cn"));
        reader.setIncludeAttribute(AttributeDescription.valueOf("cn"));
        reader.setIncludeAttribute(AttributeDescription.valueOf("sn"));
        reader.setIncludeAttribute(AttributeDescription.valueOf("entryDN"));

        final ChangeRecord cr = reader.readChangeRecord();
        reader.close();

        assertThat(cr.getName().toString()).isEqualTo("uid=scarter,ou=People,dc=example,dc=com");

        AddRequest addRequest = (AddRequest) cr;
        assertThat(addRequest.containsAttribute("entryDN")).isTrue();
        assertThat(addRequest.containsAttribute("sn")).isTrue();
        assertThat(addRequest.containsAttribute("cn")).isTrue();
        assertThat(addRequest.containsAttribute("changetype")).isFalse();
        assertThat(addRequest.containsAttribute("mail")).isFalse();
        assertThat(addRequest.getAttributeCount()).isEqualTo(3);
    }

    /**
     * Test to read an ldifChangeRecord with attribute including.
     *
     * @throws Exception
     */
    @Test()
    public void testSetIncludeAttributeWithNoMatch() throws Exception {

        final LDIFChangeRecordReader reader =
                new LDIFChangeRecordReader(getStandardLDIFChangeRecord());

        reader.setIncludeAttribute(AttributeDescription.valueOf("manager"));

        final ChangeRecord cr = reader.readChangeRecord();
        reader.close();

        assertThat(cr.getName().toString()).isEqualTo("uid=scarter,ou=People,dc=example,dc=com");

        AddRequest addRequest = (AddRequest) cr;
        assertThat(addRequest.containsAttribute("entryDN")).isFalse();
        assertThat(addRequest.containsAttribute("sn")).isFalse();
        assertThat(addRequest.containsAttribute("cn")).isFalse();
        assertThat(addRequest.containsAttribute("changetype")).isFalse();
        assertThat(addRequest.containsAttribute("mail")).isFalse();
        assertThat(addRequest.getAttributeCount()).isEqualTo(0);
    }

    /**
     * SetIncludeAttribute doesn't allow null.
     *
     * @throws Exception
     */
    @Test(expectedExceptions = NullPointerException.class)
    public void testSetIncludeAttributeDoesntAllowNull() throws Exception {

        final LDIFChangeRecordReader reader =
                new LDIFChangeRecordReader("version: 1",
                        "dn: uid=scarter,ou=People,dc=example,dc=com");
        reader.setIncludeAttribute(null);
    }

    /**
     * Test SetIncludeBranch method of LDIFChangeRecordReader.
     *
     * @throws Exception
     */
    @Test()
    public void testSetIncludeBranchWithMatch() throws Exception {

        final LDIFChangeRecordReader reader =
                new LDIFChangeRecordReader(getStandardLDIFChangeRecord());
        reader.setIncludeBranch(DN.valueOf("dc=example,dc=com"));

        final ChangeRecord cr = reader.readChangeRecord();
        reader.close();

        assertThat(cr.getName().toString()).isEqualTo("uid=scarter,ou=People,dc=example,dc=com");

        AddRequest addRequest = (AddRequest) cr;
        assertThat(addRequest.containsAttribute("entryDN")).isTrue();
        assertThat(addRequest.containsAttribute("sn")).isTrue();
        assertThat(addRequest.containsAttribute("cn")).isTrue();
        assertThat(addRequest.containsAttribute("changetype")).isFalse();
        assertThat(addRequest.containsAttribute("mail")).isTrue();
        assertThat(addRequest.getAttributeCount()).isEqualTo(10);
    }

    /**
     * Test SetIncludeBranch method of LDIFChangeRecordReader. The branch is not
     * included, throw an NoSuchElementException.
     *
     * @throws Exception
     */
    @Test(expectedExceptions = NoSuchElementException.class)
    public void testSetIncludeBranchWithNoMatch() throws Exception {

        final LDIFChangeRecordReader reader =
                new LDIFChangeRecordReader(getStandardLDIFChangeRecord());
        reader.setIncludeBranch(DN.valueOf("dc=example,dc=org"));
        try {
            reader.readChangeRecord();
        } finally {
            reader.close();
        }
    }

    /**
     * SetIncludeBranch doesn't allow null.
     *
     * @throws Exception
     */
    @Test(expectedExceptions = NullPointerException.class)
    public void testSetIncludeBranchDoesntAllowNull() throws Exception {

        final LDIFChangeRecordReader reader =
                new LDIFChangeRecordReader("version: 1",
                        "dn: uid=scarter,ou=People,dc=example,dc=com");
        reader.setIncludeBranch(null);
    }

    /**
     * Test SetExcludeBranch method of LDIFChangeRecordReader.
     *
     * @throws Exception
     */
    @Test()
    public void testSetExcludeBranchWithMatch() throws Exception {

        final LDIFChangeRecordReader reader =
                new LDIFChangeRecordReader(getStandardLDIFChangeRecord());
        reader.setExcludeBranch(DN.valueOf("dc=example,dc=org"));
        ChangeRecord cr = null;

        cr = reader.readChangeRecord();

        AddRequest addRequest = (AddRequest) cr;
        assertThat(addRequest.containsAttribute("entryDN")).isTrue();
        assertThat(addRequest.containsAttribute("sn")).isTrue();
        assertThat(addRequest.containsAttribute("cn")).isTrue();
        assertThat(addRequest.containsAttribute("changetype")).isFalse();
        assertThat(addRequest.containsAttribute("mail")).isTrue();
        assertThat(addRequest.getAttributeCount()).isEqualTo(10);
        reader.close();
    }

    /**
     * Test SetExcludeBranch method of LDIFChangeRecordReader.
     *
     * @throws Exception
     */
    @Test(expectedExceptions = NoSuchElementException.class)
    public void testSetExcludeBranchWithNoMatch() throws Exception {

        final LDIFChangeRecordReader reader =
                new LDIFChangeRecordReader(getStandardLDIFChangeRecord());
        reader.setExcludeBranch(DN.valueOf("dc=example,dc=com"));

        try {
            reader.readChangeRecord();
        } finally {
            reader.close();
        }
    }

    /**
     * SetExcludeBranch doesn't allow null.
     *
     * @throws Exception
     */
    @Test(expectedExceptions = NullPointerException.class)
    public void testSetExcludeBranchDoesntAllowNull() throws Exception {

        final LDIFChangeRecordReader reader =
                new LDIFChangeRecordReader("version: 1",
                        "dn: uid=scarter,ou=People,dc=example,dc=com");
        reader.setExcludeBranch(null);
    }

    /**
     * SetSchema doesn't allow null.
     *
     * @throws Exception
     */
    @Test(expectedExceptions = NullPointerException.class)
    public void testSetSchemaDoesntAllowNull() throws Exception {

        final LDIFChangeRecordReader reader =
                new LDIFChangeRecordReader("version: 1",
                        "dn: uid=scarter,ou=People,dc=example,dc=com");
        reader.setSchema(null);
    }

    /**
     * LDIFChangeRecordReader setSchemaValidationPolicy. Validate the
     * ChangeRecord depending of the selected policy. ChangeRecord is here
     * allowed because it fills the case of validation.
     *
     * @throws Exception
     */
    @Test()
    public void testSetSchemaValidationPolicyDefaultAllowsEntry() throws Exception {

        // @formatter:off
        final String[] strChangeRecord = {
            "dn: uid=user.0,ou=People,dc=example,dc=com",
            "changetype: add",
            "sn: Carter",
            "objectClass: person",
            "objectClass: top",
            "cn: Aaccf Amar",
            "sn: Amar"
        };
        // @formatter:on

        final LDIFChangeRecordReader reader = new LDIFChangeRecordReader(strChangeRecord);
        reader.setSchema(Schema.getDefaultSchema());
        reader.setSchemaValidationPolicy(SchemaValidationPolicy.defaultPolicy());

        final ChangeRecord cr = reader.readChangeRecord();

        AddRequest addRequest = (AddRequest) cr;
        assertThat(cr.getName().toString()).isEqualTo("uid=user.0,ou=People,dc=example,dc=com");
        assertThat(addRequest.containsAttribute("sn")).isTrue();
        assertThat(addRequest.containsAttribute("cn")).isTrue();
        assertThat(addRequest.getAttributeCount()).isEqualTo(3);

        reader.close();

    }

    /**
     * LDIFChangeRecordReader setSchemaValidationPolicy. Validate the Change
     * Record depending of the selected policy. ChangeRecord is here NOT allowed
     * because it contains a uid attribute which is not allowed by the
     * SchemaValidationPolicy.
     *
     * @throws Exception
     */
    @Test(expectedExceptions = DecodeException.class)
    public void testSetSchemaValidationPolicyDefaultRejectsEntry() throws Exception {
        // @formatter:off
        String[] strChangeRecord = {
            "dn: uid=user.0,ou=People,dc=example,dc=com",
            "changetype: add",
            "sn: Carter",
            "objectClass: person",
            "objectClass: top",
            "cn: Aaccf Amar",
            "sn: Amar",
            "uid: user.0"
        };
        // @formatter:on

        final LDIFChangeRecordReader reader = new LDIFChangeRecordReader(strChangeRecord);
        reader.setSchema(Schema.getDefaultSchema());
        reader.setSchemaValidationPolicy(SchemaValidationPolicy.defaultPolicy());

        try {
            reader.readChangeRecord();
        } finally {
            reader.close();
        }
    }

    /**
     * Test an LDIFRecordChange with an empty pair key. Must throw an exception.
     *
     * @throws Exception
     */
    @Test(expectedExceptions = DecodeException.class)
    public void testReadAddRecordWithEmptyPairKeyChangeType() throws Exception {
        // @formatter:off
        LDIFChangeRecordReader reader = new LDIFChangeRecordReader(
            "dn: dc=example,dc=com",
            ":add" // if empty spaces, ko.
        );
        // @formatter:on

        try {
            reader.readChangeRecord();
        } finally {
            reader.close();
        }
    }

    /**
     * LDIFChangeRecordReader used with a wrong changetype. Must return an
     * exception.
     *
     * @throws Exception
     */
    @Test(expectedExceptions = DecodeException.class)
    public void testReadAddRecordWithWrongChangeType() throws Exception {
        // @formatter:off
        LDIFChangeRecordReader reader = new LDIFChangeRecordReader(
            "dn: dc=example,dc=com",
            "changetype: oops", // wrong
            "objectClass: top",
            "objectClass: domainComponent",
            "dc: example"
        );
        // @formatter:on
        try {
            reader.readChangeRecord();
        } finally {
            reader.close();
        }
    }

    /**
     * Tests reading a valid add change record with a changetype.
     *
     * @throws Exception
     *             if an unexpected error occurred.
     */
    @Test()
    public void testReadAddRecordWithChangeType() throws Exception {
        // @formatter:off
        LDIFChangeRecordReader reader = new LDIFChangeRecordReader(
            "dn: dc=example,dc=com",
            "changetype: add",
            "objectClass: top",
            "objectClass: domainComponent",
            "dc: example"
        );
        // @formatter:on

        assertThat(reader.hasNext()).isTrue();
        ChangeRecord record = reader.readChangeRecord();
        assertThat(record).isInstanceOf(AddRequest.class);
        AddRequest addRequest = (AddRequest) record;
        assertThat((Object) addRequest.getName()).isEqualTo(DN.valueOf("dc=example,dc=com"));
        assertThat(addRequest.containsAttribute("objectClass", "top", "domainComponent")).isTrue();
        assertThat(addRequest.containsAttribute("dc", "example")).isTrue();
        assertThat(addRequest.getAttributeCount()).isEqualTo(2);
        assertThat(reader.hasNext()).isFalse();
        reader.close();
    }

    /**
     * Tests reading a valid add change record without a changetype.
     *
     * @throws Exception
     *             if an unexpected error occurred.
     */
    @Test()
    public void testReadAddRecordWithoutChangeType() throws Exception {
        // @formatter:off
        LDIFChangeRecordReader reader = new LDIFChangeRecordReader(
            "dn: dc=example,dc=com",
            "objectClass: top",
            "objectClass: domainComponent",
            "dc: example"
        );
        // @formatter:on

        assertThat(reader.hasNext()).isTrue();
        ChangeRecord record = reader.readChangeRecord();
        assertThat(record).isInstanceOf(AddRequest.class);
        AddRequest addRequest = (AddRequest) record;
        assertThat((Object) addRequest.getName()).isEqualTo(DN.valueOf("dc=example,dc=com"));
        assertThat(addRequest.containsAttribute("objectClass", "top", "domainComponent")).isTrue();
        assertThat(addRequest.containsAttribute("dc", "example")).isTrue();
        assertThat(addRequest.getAttributeCount()).isEqualTo(2);
        reader.close();
    }

    /**
     * Tests reading a valid modify change record.
     *
     * @throws Exception
     *             if an unexpected error occurred.
     */
    @Test()
    public void testReadModifyRecord() throws Exception {
        // @formatter:off
        LDIFChangeRecordReader reader = new LDIFChangeRecordReader(
            "dn: dc=example,dc=com",
            "changetype: modify",
            "add: description",
            "-",
            "add: description",
            "description: value1",
            "-",
            "add: description",
            "description: value1",
            "description: value2",
            "-",
            "delete: description",
            "-",
            "delete: description",
            "description: value1",
            "-",
            "delete: description",
            "description: value1",
            "description: value2",
            "-",
            "replace: description",
            "-",
            "replace: description",
            "description: value1",
            "-",
            "replace: description",
            "description: value1",
            "description: value2",
            "-",
            "increment: description",
            "description: 1"
        );
        // @formatter:on

        assertThat(reader.hasNext()).isTrue();
        ChangeRecord record = reader.readChangeRecord();
        assertThat(record).isInstanceOf(ModifyRequest.class);
        ModifyRequest modifyRequest = (ModifyRequest) record;
        assertThat((Object) modifyRequest.getName()).isEqualTo(DN.valueOf("dc=example,dc=com"));

        Iterator<Modification> changes = modifyRequest.getModifications().iterator();
        Modification modification;

        modification = changes.next();
        assertThat(modification.getModificationType()).isEqualTo(ModificationType.ADD);
        assertThat(modification.getAttribute()).isEqualTo(new LinkedAttribute("description"));

        modification = changes.next();
        assertThat(modification.getModificationType()).isEqualTo(ModificationType.ADD);
        assertThat(modification.getAttribute()).isEqualTo(
                new LinkedAttribute("description", "value1"));

        modification = changes.next();
        assertThat(modification.getModificationType()).isEqualTo(ModificationType.ADD);
        assertThat(modification.getAttribute()).isEqualTo(
                new LinkedAttribute("description", "value1", "value2"));

        modification = changes.next();
        assertThat(modification.getModificationType()).isEqualTo(ModificationType.DELETE);
        assertThat(modification.getAttribute()).isEqualTo(new LinkedAttribute("description"));

        modification = changes.next();
        assertThat(modification.getModificationType()).isEqualTo(ModificationType.DELETE);
        assertThat(modification.getAttribute()).isEqualTo(
                new LinkedAttribute("description", "value1"));

        modification = changes.next();
        assertThat(modification.getModificationType()).isEqualTo(ModificationType.DELETE);
        assertThat(modification.getAttribute()).isEqualTo(
                new LinkedAttribute("description", "value1", "value2"));

        modification = changes.next();
        assertThat(modification.getModificationType()).isEqualTo(ModificationType.REPLACE);
        assertThat(modification.getAttribute()).isEqualTo(new LinkedAttribute("description"));

        modification = changes.next();
        assertThat(modification.getModificationType()).isEqualTo(ModificationType.REPLACE);
        assertThat(modification.getAttribute()).isEqualTo(
                new LinkedAttribute("description", "value1"));

        modification = changes.next();
        assertThat(modification.getModificationType()).isEqualTo(ModificationType.REPLACE);
        assertThat(modification.getAttribute()).isEqualTo(
                new LinkedAttribute("description", "value1", "value2"));

        modification = changes.next();
        assertThat(modification.getModificationType()).isEqualTo(ModificationType.INCREMENT);
        assertThat(modification.getAttribute()).isEqualTo(new LinkedAttribute("description", "1"));

        assertThat(changes.hasNext()).isFalse();
        reader.close();
    }

    /**
     * Tests reading a valid moddn change record.
     *
     * @throws Exception
     *             if an unexpected error occurred.
     */
    @Test()
    public void testReadModdnRecordWithoutNewSuperior() throws Exception {
        // @formatter:off
        LDIFChangeRecordReader reader = new LDIFChangeRecordReader(
            "dn: dc=example,dc=com",
            "changetype: moddn",
            "newrdn: dc=eggsample",
            "deleteoldrdn: true"
        );
        // @formatter:on

        assertThat(reader.hasNext()).isTrue();
        ChangeRecord record = reader.readChangeRecord();
        assertThat(record).isInstanceOf(ModifyDNRequest.class);
        ModifyDNRequest modifyDNRequest = (ModifyDNRequest) record;
        assertThat((Object) modifyDNRequest.getName()).isEqualTo(DN.valueOf("dc=example,dc=com"));
        assertThat((Object) modifyDNRequest.getNewRDN()).isEqualTo(RDN.valueOf("dc=eggsample"));
        assertThat(modifyDNRequest.isDeleteOldRDN()).isTrue();
        assertThat(modifyDNRequest.getNewSuperior()).isNull();
        reader.close();
    }

    /**
     * Tests reading a valid moddn change record.
     *
     * @throws Exception
     *             if an unexpected error occurred.
     */
    @Test()
    public void testReadModdnRecordWithNewSuperior() throws Exception {
        // @formatter:off
        LDIFChangeRecordReader reader = new LDIFChangeRecordReader(
            "dn: dc=example,dc=com",
            "changetype: moddn",
            "newrdn: dc=eggsample",
            "deleteoldrdn: true",
            "newsuperior: dc=org"
        );
        // @formatter:on

        assertThat(reader.hasNext()).isTrue();
        ChangeRecord record = reader.readChangeRecord();
        assertThat(record).isInstanceOf(ModifyDNRequest.class);
        ModifyDNRequest modifyDNRequest = (ModifyDNRequest) record;
        assertThat((Object) modifyDNRequest.getName()).isEqualTo(DN.valueOf("dc=example,dc=com"));
        assertThat((Object) modifyDNRequest.getNewRDN()).isEqualTo(RDN.valueOf("dc=eggsample"));
        assertThat(modifyDNRequest.isDeleteOldRDN()).isTrue();
        assertThat((Object) modifyDNRequest.getNewSuperior()).isEqualTo(DN.valueOf("dc=org"));
        reader.close();
    }

    /**
     * Tests reading a malformed record invokes the rejected record listener.
     *
     * @throws Exception
     *             if an unexpected error occurred.
     */
    @Test()
    public void testRejectedRecordListenerMalformedFirstRecord() throws Exception {

        RejectedLDIFListener listener = mock(RejectedLDIFListener.class);

        // @formatter:off
        LDIFChangeRecordReader reader = new LDIFChangeRecordReader(
            "dn: baddn",
            "changetype: add",
            "objectClass: top",
            "objectClass: domainComponent",
            "dc: example"
        );
        // @formatter:on
        reader.setRejectedLDIFListener(listener);
        assertThat(reader.hasNext()).isFalse();

        verify(listener).handleMalformedRecord(
                eq(1L),
                eq(Arrays.asList("dn: baddn", "changetype: add", "objectClass: top",
                        "objectClass: domainComponent", "dc: example")),
                any(LocalizableMessage.class));
        reader.close();
    }

    /**
     * Tests reading a malformed record invokes the rejected record listener.
     *
     * @throws Exception
     *             if an unexpected error occurred.
     */
    @Test()
    public void testRejectedRecordListenerMalformedSecondRecord() throws Exception {

        RejectedLDIFListener listener = mock(RejectedLDIFListener.class);

        // @formatter:off
        LDIFChangeRecordReader reader = new LDIFChangeRecordReader(
            "dn: dc=example,dc=com",
            "changetype: add",
            "objectClass: top",
            "objectClass: domainComponent",
            "dc: example",
            "",
            "dn: baddn",
            "changetype: add",
            "objectClass: top",
            "objectClass: domainComponent",
            "dc: example"
        );
        // @formatter:on
        reader.setRejectedLDIFListener(listener);
        reader.readChangeRecord(); // Skip good record.
        assertThat(reader.hasNext()).isFalse();

        verify(listener).handleMalformedRecord(
                eq(7L),
                eq(Arrays.asList("dn: baddn", "changetype: add", "objectClass: top",
                        "objectClass: domainComponent", "dc: example")),
                any(LocalizableMessage.class));
        reader.close();
    }

    /**
     * Tests reading a skipped record invokes the rejected record listener.
     *
     * @throws Exception
     *             if an unexpected error occurred.
     */
    @Test()
    public void testRejectedRecordListenerSkipsRecord() throws Exception {
        RejectedLDIFListener listener = mock(RejectedLDIFListener.class);

        // @formatter:off
        LDIFChangeRecordReader reader = new LDIFChangeRecordReader(
            "dn: dc=example,dc=com",
            "changetype: add",
            "objectClass: top",
            "objectClass: domainComponent",
            "dc: example"
        );
        // @formatter:on
        reader.setRejectedLDIFListener(listener).setExcludeBranch(DN.valueOf("dc=com"));
        assertThat(reader.hasNext()).isFalse();

        verify(listener).handleSkippedRecord(
                eq(1L),
                eq(Arrays.asList("dn: dc=example,dc=com", "changetype: add", "objectClass: top",
                        "objectClass: domainComponent", "dc: example")),
                any(LocalizableMessage.class));
        reader.close();
    }

    /**
     * Tests reading a record which does not conform to the schema invokes the
     * rejected record listener.
     *
     * @throws Exception
     *             if an unexpected error occurred.
     */
    @Test()
    public void testRejectedRecordListenerRejectsBadSchemaRecord() throws Exception {
        RejectedLDIFListener listener = mock(RejectedLDIFListener.class);

        // @formatter:off
        LDIFChangeRecordReader reader = new LDIFChangeRecordReader(
            "dn: dc=example,dc=com",
            "changetype: add",
            "objectClass: top",
            "objectClass: domainComponent",
            "dc: example",
            "xxx: unknown attribute"
        );
        reader.setRejectedLDIFListener(listener)
             .setSchemaValidationPolicy(
                 SchemaValidationPolicy.ignoreAll()
                     .checkAttributesAndObjectClasses(Policy.REJECT));
        // @formatter:on

        assertThat(reader.hasNext()).isFalse();

        verify(listener).handleSchemaValidationFailure(
                eq(1L),
                eq(Arrays.asList("dn: dc=example,dc=com", "changetype: add", "objectClass: top",
                        "objectClass: domainComponent", "dc: example", "xxx: unknown attribute")),
                anyListOf(LocalizableMessage.class));
        reader.close();
    }

    /**
     * Tests reading a record which does not conform to the schema invokes the
     * warning record listener.
     *
     * @throws Exception
     *             if an unexpected error occurred.
     */
    @Test()
    public void testRejectedRecordListenerWarnsBadSchemaRecord() throws Exception {
        RejectedLDIFListener listener = mock(RejectedLDIFListener.class);

        // @formatter:off
        LDIFChangeRecordReader reader = new LDIFChangeRecordReader(
            "dn: dc=example,dc=com",
            "changetype: add",
            "objectClass: top",
            "objectClass: domainComponent",
            "dc: example",
            "xxx: unknown attribute"
        );
        reader.setRejectedLDIFListener(listener)
             .setSchemaValidationPolicy(
                 SchemaValidationPolicy.ignoreAll()
                     .checkAttributesAndObjectClasses(Policy.WARN));
        // @formatter:on

        assertThat(reader.hasNext()).isTrue();

        ChangeRecord record = reader.readChangeRecord();
        assertThat(record).isInstanceOf(AddRequest.class);
        AddRequest addRequest = (AddRequest) record;
        assertThat((Object) addRequest.getName()).isEqualTo(DN.valueOf("dc=example,dc=com"));
        assertThat(addRequest.containsAttribute("objectClass", "top", "domainComponent")).isTrue();
        assertThat(addRequest.containsAttribute("dc", "example")).isTrue();
        assertThat(addRequest.getAttributeCount()).isEqualTo(2);

        verify(listener).handleSchemaValidationWarning(
                eq(1L),
                eq(Arrays.asList("dn: dc=example,dc=com", "changetype: add", "objectClass: top",
                        "objectClass: domainComponent", "dc: example", "xxx: unknown attribute")),
                anyListOf(LocalizableMessage.class));
        reader.close();
    }

    /**
     * Read an example containing an invalid url. Must throw an exception.
     *
     * @throws Exception
     */
    @Test(expectedExceptions = DecodeException.class)
    public void testReadFileContainingInvalidURLThrowsError() throws Exception {
        // Obtain the name of a file which is guaranteed not to exist.
        final File file = File.createTempFile("sdk", null);
        final String url = file.toURI().toURL().toString();
        file.delete();

        // @formatter:off
        final  LDIFChangeRecordReader reader = new LDIFChangeRecordReader(
            "version: 1",
            "# Add a new entry",
            "dn: cn=Fiona Jensen, ou=Marketing, dc=airius, dc=com",
            "changetype: add",
            "objectclass: top",
            "objectclass: person",
            "objectclass: organizationalPerson",
            "cn: Fiona Jensen",
            "sn: Jensen",
            "uid: fiona",
            "telephonenumber: +1 408 555 1212",
            "jpegphoto:< " + url
        );
        // @formatter:on

        try {
            reader.readChangeRecord();
        } finally {
            reader.close();
        }
    }

    /**
     * Read a complete LDIFChangeRecord containing serie of changes.
     *
     * @throws Exception
     */
    @Test()
    public void testReadFileContainingSerieOfChanges() throws Exception {
        // @formatter:off
        final  LDIFChangeRecordReader reader = new LDIFChangeRecordReader(
                "version: 1",
                "# Add a new entry",
                "dn: cn=Fiona Jensen, ou=Marketing, dc=airius, dc=com",
                "changetype: add",
                "objectclass: top",
                "objectclass: person",
                "objectclass: organizationalPerson",
                "cn: Fiona Jensen",
                "sn: Jensen",
                "uid: fiona",
                "telephonenumber: +1 408 555 1212",
                "jpegphoto:< http://www.forgerock.com/sites/default/files/forgerock_logo.png",
                "",
                "# Delete an existing entry",
                "dn: cn=Robert Jensen, ou=Marketing, dc=airius, dc=com",
                "changetype: delete",
                "",
                "# Modify an entry's relative distinguished name",
                "dn: cn=Paul Jensen, ou=Product Development, dc=airius, dc=com",
                "changetype: modrdn",
                "newrdn: cn=Paula Jensen",
                "deleteoldrdn: 1",
                "",
                "# Rename an entry and move all of its children to a new location in",
                "# the directory tree (only implemented by LDAPv3 servers).",
                "dn: ou=PD Accountants, ou=Product Development, dc=airius, dc=com",
                "changetype: modrdn",
                "newrdn: ou=Product Development Accountants",
                "deleteoldrdn: 0",
                "newsuperior: ou=Accounting, dc=airius, dc=com",
                ""
        );
        // @formatter:on
        assertThat(reader.hasNext()).isTrue();
        // 1st record
        ChangeRecord record = reader.readChangeRecord();
        assertThat(record).isInstanceOf(AddRequest.class);
        AddRequest addReq = (AddRequest) record;
        assertThat((Object) addReq.getName()).isEqualTo(
                DN.valueOf("cn=Fiona Jensen, ou=Marketing, dc=airius, dc=com"));
        // 2nd record
        record = reader.readChangeRecord();
        assertThat(record).isInstanceOf(DeleteRequest.class);
        DeleteRequest delReq = (DeleteRequest) record;
        assertThat((Object) delReq.getName()).isEqualTo(
                DN.valueOf("cn=Robert Jensen, ou=Marketing, dc=airius, dc=com"));
        assertThat(reader.hasNext()).isTrue();
        // 3rd record
        record = reader.readChangeRecord();
        assertThat(record).isInstanceOf(ModifyDNRequest.class);
        ModifyDNRequest modDNReq = (ModifyDNRequest) record;

        assertThat((Object) modDNReq.getNewRDN()).isEqualTo(RDN.valueOf("cn=Paula Jensen"));
        assertThat((Object) modDNReq.getName()).isEqualTo(
                DN.valueOf("cn=Paul Jensen, ou=Product Development, dc=airius, dc=com"));
        assertThat(reader.hasNext()).isTrue();
        // 4th record
        record = reader.readChangeRecord();
        assertThat(record).isInstanceOf(ModifyDNRequest.class);
        modDNReq = (ModifyDNRequest) record;
        assertThat((Object) modDNReq.getName()).isEqualTo(
                DN.valueOf("ou=PD Accountants, ou=Product Development, dc=airius, dc=com"));
        assertThat(reader.hasNext()).isFalse();

        reader.close();
    }

    /**
     * Test to read an entry containing a control. Not Yet implemented. TODO
     * OPENDJ-185 Add support for controls in SDK LDIFReader.
     *
     * @throws Exception
     */
    @Test(enabled = false)
    public void testLDIFCRRParseAddChangeRecordEntryWithAControl() throws Exception {
        // @formatter:off
        final  LDIFChangeRecordReader reader = new LDIFChangeRecordReader(
                    "# Delete an entry. The operation will attach the LDAPv3",
                    "# Tree Delete Control defined in [9]. The criticality",
                    "# field is \"true\" and the controlValue field is",
                    "# absent, as required by [9].",
                    "dn: ou=Product Development, dc=airius, dc=com",
                    "control: 1.2.840.113556.1.4.805 true",
                    "changetype: delete"
        );
        // @formatter:on

        assertThat(reader.hasNext()).isTrue();

        reader.setSchema(Schema.getDefaultSchema());
        reader.setSchemaValidationPolicy(SchemaValidationPolicy.defaultPolicy());
        // Read the entry
        ChangeRecord record = reader.readChangeRecord();
        assertThat(record).isInstanceOf(AddRequest.class);
        AddRequest request = (AddRequest) record;
        assertThat((Object) request.getName()).isEqualTo(
                DN.valueOf("ou=Product Development, dc=airius, dc=com"));
        assertThat(request.getControls()).isNotEmpty();
        reader.close();
    }

    /**
     * Test an add request malformed, changetype is erroneous (wrongchangetype)
     * Must throw an exception.
     *
     * @throws Exception
     */
    @Test(expectedExceptions = DecodeException.class)
    public void testLDIFCRRParseAddChangeRecordEntryLastLDIFLineIsNull() throws Exception {
        // @formatter:off
        LDIFChangeRecordReader reader = new LDIFChangeRecordReader(
                "dn: uid=scarter,ou=People,dc=example,dc=com",
                "wrongchangetype: add", // wrong
                "uid:Carter"
        );
        // @formatter:on

        reader.setSchema(Schema.getDefaultSchema());
        reader.setSchemaValidationPolicy(SchemaValidationPolicy.defaultPolicy());

        try {
            reader.readChangeRecord();
        } finally {
            reader.close();
        }
    }

    /**
     * Tests reading a valid delete change record.
     *
     * @throws Exception
     *             if an unexpected error occurred.
     */
    @Test()
    public void testLDIFCRRparseDeleteChangeRecordEntry() throws Exception {
        // @formatter:off
        LDIFChangeRecordReader reader = new LDIFChangeRecordReader(
            "dn: dc=example,dc=com",
            "changetype: delete"
        );
        // @formatter:on

        assertThat(reader.hasNext()).isTrue();
        ChangeRecord record = reader.readChangeRecord();
        assertThat(record).isInstanceOf(DeleteRequest.class);
        DeleteRequest deleteRequest = (DeleteRequest) record;
        assertThat((Object) deleteRequest.getName()).isEqualTo(DN.valueOf("dc=example,dc=com"));
        reader.close();
    }

    /**
     * Testing a valid LDIFChangeRecord with the delete type. The LDIF is
     * containing additional lines after the 'changetype' when none were
     * expected. Exception must be throw.
     *
     * @throws Exception
     */
    @Test(expectedExceptions = DecodeException.class)
    public void testLDIFCRRParseDeleteChangeRecordEntryMalformedDelete() throws Exception {

        // @formatter:off
        LDIFChangeRecordReader reader = new LDIFChangeRecordReader(
                "# Delete an existing entry",
                "dn: cn=Robert Jensen, ou=Marketing, dc=airius, dc=com",
                "changetype: delete",
                "-",
                "add: telephonenumber",
                "telephonenumber: 555-4321"
        );
        // @formatter:on
        reader.readChangeRecord();
    }

    /**
     * Read an LDIFChangeRecord for deleting selected values of a multi-valued
     * attribute.
     *
     * @throws Exception
     */
    @Test()
    public void testLDIFCRRParseModifyChangeRecordEntryDeleteMultipleValuableAttributes()
            throws Exception {
        // @formatter:off
        final LDIFChangeRecordReader reader = new LDIFChangeRecordReader(
                "# Add new entry containing multiple attributes",
                "dn: cn=Fiona Jensen, ou=Marketing, dc=airius, dc=com",
                "changetype: modify",
                "delete: telephonenumber",
                "telephonenumber: +1 408 555 1212",
                "telephonenumber: +1 408 555 1213",
                "telephonenumber: +1 408 555 1214"
        );
        // @formatter:on
        assertThat(reader.hasNext()).isTrue();

        reader.setSchema(Schema.getDefaultSchema());
        reader.setSchemaValidationPolicy(SchemaValidationPolicy.defaultPolicy());

        ChangeRecord record = reader.readChangeRecord();
        assertThat(record).isInstanceOf(ModifyRequest.class);
        ModifyRequest req = (ModifyRequest) record;
        assertThat((Object) req.getName()).isEqualTo(
                DN.valueOf("cn=Fiona Jensen, ou=Marketing, dc=airius, dc=com"));
        assertThat(reader.hasNext()).isFalse();
    }

    /**
     * Read an LDIFChangeRecord for deleting selected values of a multi-valued
     * attribute. The 2nd attribute is malformed : Schema validation failure.
     * ERR_LDIF_MALFORMED_ATTRIBUTE_NAME : The provided value is not a valid
     * telephone number because it is empty or null
     *
     * @throws Exception
     */
    @Test(expectedExceptions = DecodeException.class)
    public void testLDIFCRRParseModifyChangeRecordEntryDeleteMultipleValuableAttributesMalformedLDIF()
            throws Exception {
        // @formatter:off
        final LDIFChangeRecordReader reader = new LDIFChangeRecordReader(
                "# Add new entry containing multiple attributes",
                "dn: cn=Fiona Jensen, ou=Marketing, dc=airius, dc=com",
                "changetype: modify",
                "delete: telephonenumber",
                "telephonenumber: +1 408 555 1212",
                "telephonenumber:", // wrong!
                "telephonenumber: +1 408 555 1214",
                "-"
        );
        // @formatter:on

        reader.setSchema(Schema.getDefaultSchema());
        reader.setSchemaValidationPolicy(SchemaValidationPolicy.defaultPolicy());

        try {
            reader.readChangeRecord();
        } finally {
            reader.close();
        }
    }

    /**
     * BER encoding is required for this LDIFChangeRecord. After adding the user
     * certificate to the core schema, LDIFCRR is correctly read.
     *
     * @throws Exception
     */
    @Test()
    public void testLDIFCRRParseModifyChangeRecordBEREncodingRequired() throws Exception {
        // @formatter:off
        String validcert1 = // a valid certificate but wrong can be used => no errors
                "MIICpTCCAg6gAwIBAgIJALeoA6I3ZC/cMA0GCSqGSIb3DQEBBQUAMFYxCzAJBgNV"
                + "BAYTAlVTMRMwEQYDVQQHEwpDdXBlcnRpb25lMRwwGgYDVQQLExNQcm9kdWN0IERl"
                + "dmVsb3BtZW50MRQwEgYDVQQDEwtCYWJzIEplbnNlbjAeFw0xMjA1MDIxNjM0MzVa"
                + "Fw0xMjEyMjExNjM0MzVaMFYxCzAJBgNVBAYTAlVTMRMwEQYDVQQHEwpDdXBlcnRp"
                + "b25lMRwwGgYDVQQLExNQcm9kdWN0IERldmVsb3BtZW50MRQwEgYDVQQDEwtCYWJz"
                + "IEplbnNlbjCBnzANBgkqhkiG9w0BAQEFAAOBjQAwgYkCgYEApysa0c9qc8FB8gIJ"
                + "8zAb1pbJ4HzC7iRlVGhRJjFORkGhyvU4P5o2wL0iz/uko6rL9/pFhIlIMbwbV8sm"
                + "mKeNUPitwiKOjoFDmtimcZ4bx5UTAYLbbHMpEdwSpMC5iF2UioM7qdiwpAfZBd6Z"
                + "69vqNxuUJ6tP+hxtr/aSgMH2i8ECAwEAAaN7MHkwCQYDVR0TBAIwADAsBglghkgB"
                + "hvhCAQ0EHxYdT3BlblNTTCBHZW5lcmF0ZWQgQ2VydGlmaWNhdGUwHQYDVR0OBBYE"
                + "FLlZD3aKDa8jdhzoByOFMAJDs2osMB8GA1UdIwQYMBaAFLlZD3aKDa8jdhzoByOF"
                + "MAJDs2osMA0GCSqGSIb3DQEBBQUAA4GBAE5vccY8Ydd7by2bbwiDKgQqVyoKrkUg"
                + "6CD0WRmc2pBeYX2z94/PWO5L3Fx+eIZh2wTxScF+FdRWJzLbUaBuClrxuy0Y5ifj"
                + "axuJ8LFNbZtsp1ldW3i84+F5+SYT+xI67ZcoAtwx/VFVI9s5I/Gkmu9f9nxjPpK7"
                + "1AIUXiE3Qcck";

        final String[] strChangeRecord = {
            "version: 1",
            "dn:uid=scarter,ou=People,dc=example,dc=com",
            "changetype: modify",
            "add: userCertificate;binary", // with or without the binary its working
            String.format("userCertificate;binary::%s", validcert1)
        };
        // @formatter:on

        final LDIFChangeRecordReader reader = new LDIFChangeRecordReader(strChangeRecord);

        final SchemaBuilder scBuild = new SchemaBuilder();
        // Adding the new schema containing the userCertificate
        scBuild.addObjectClass("( 2.5.6.15 NAME 'strongAuthenticationUser"
                + "' SUP top AUXILIARY MUST userCertificate )", false);
        scBuild.addAttributeType(
                "( 2.5.4.36 NAME 'userCertificate' SYNTAX 1.3.6.1.4.1.1466.115.121.1.8 )", false);
        // Adding to default core schema
        scBuild.addSchema(Schema.getCoreSchema(), false);
        Schema schema = scBuild.toSchema();
        reader.setSchema(schema);
        reader.setSchemaValidationPolicy(SchemaValidationPolicy.defaultPolicy());

        ModifyRequest modifyRequest = (ModifyRequest) reader.readChangeRecord();
        assertThat(modifyRequest.getName().toString()).isEqualTo(
                "uid=scarter,ou=People,dc=example,dc=com");
        assertThat(modifyRequest.getModifications().get(0).getModificationType().toString())
                .isEqualTo("add");
        assertThat(modifyRequest.getModifications().get(0).getAttribute().firstValueAsString())
                .contains("OpenSSL Generated Certificate");
        reader.close();
    }

    /**
     * LDIFChangeREcord reader try to add an unexpected binary option to the sn.
     * sn is a included in the core schema. Must throw an exception.
     *
     * @throws Exception
     */
    @Test(expectedExceptions = DecodeException.class)
    public void testLDIFCRRParseModifyChangeRecordBEREncodingNotRequired() throws Exception {
        // @formatter:off
        final String[] strChangeRecord = {
            "version: 1",
            "dn:uid=scarter,ou=People,dc=example,dc=com",
            "changetype: modify",
            "add: sn;binary",
            "sn;binary:: 5bCP56yg5Y6f"
        };
        // @formatter:on

        final LDIFChangeRecordReader reader = new LDIFChangeRecordReader(strChangeRecord);

        Schema schema = Schema.getCoreSchema();
        reader.setSchema(schema);
        reader.setSchemaValidationPolicy(SchemaValidationPolicy.defaultPolicy());

        try {
            reader.readChangeRecord();
        } finally {
            reader.close();
        }
    }

    /**
     * Test a 'modify' change record, respecting the default schema.
     *
     * @throws Exception
     */
    @Test()
    public void testLDIFCRRParseModifyChangeRecordEntryReplaceOk() throws Exception {

        // @formatter:off
        final String[] strChangeRecord = {
            "version: 1",
            "dn: uid=scarter,ou=People,dc=example,dc=com",
            "changetype: modify",
            "replace: uid",
            "uid: Samantha Carter"
        };
        // @formatter:on

        final LDIFChangeRecordReader reader = new LDIFChangeRecordReader(strChangeRecord);
        reader.setSchema(Schema.getDefaultSchema());
        reader.setSchemaValidationPolicy(SchemaValidationPolicy.defaultPolicy());

        ChangeRecord record = reader.readChangeRecord();
        assertThat(record).isInstanceOf(ModifyRequest.class);

        ModifyRequest modifyRequest = (ModifyRequest) record;
        assertThat(modifyRequest.getName().toString()).isEqualTo(
                "uid=scarter,ou=People,dc=example,dc=com");
        assertThat(modifyRequest.getModifications().get(0).getModificationType().toString())
                .isEqualTo("replace");
        assertThat(modifyRequest.getModifications().get(0).getAttribute().firstValueAsString())
                .isEqualTo("Samantha Carter");
        reader.close();
    }

    /**
     * Test a 'modify' change record, without respecting the default schema. The
     * 'badAttribute' is not recognized by the schema. Must throw an exception.
     *
     * @throws Exception
     */
    @Test(expectedExceptions = DecodeException.class)
    public void testLDIFCRRParseModifyChangeRecordEntryReplaceKOPolicyReject() throws Exception {

        // @formatter:off
        final String[] strChangeRecord = {
            "version: 1",
            "dn: uid=scarter,ou=People,dc=example,dc=com",
            "changetype: modify",
            "replace: badAttribute",
            "badAttribute: scarter"
        };
        // @formatter:on

        final LDIFChangeRecordReader reader = new LDIFChangeRecordReader(strChangeRecord);
        reader.setSchema(Schema.getDefaultSchema());
        reader.setSchemaValidationPolicy(SchemaValidationPolicy.defaultPolicy());
        try {
            reader.readChangeRecord();
        } finally {
            reader.close();
        }
    }

    /**
     * Change Record throw an exception because the added attribute is not valid
     * ('badAttribute') relative to the default schema. Here, we use a
     * Policy.warn instead of a Policy.REJECT (default)
     *
     * @throws Exception
     */
    @Test(expectedExceptions = DecodeException.class)
    public void testLDIFCRRParseModifyChangeRecordEntryReplaceKOPolicyWarn() throws Exception {

        // @formatter:off
        final String[] strChangeRecord = {
            "version: 1",
            "dn: uid=scarter,ou=People,dc=example,dc=com",
            "changetype: modify",
            "replace: badAttribute",
            "badAttribute: scarter"
        };
        // @formatter:on

        final LDIFChangeRecordReader reader = new LDIFChangeRecordReader(strChangeRecord);
        reader.setSchema(Schema.getDefaultSchema());
        reader.setSchemaValidationPolicy(SchemaValidationPolicy.defaultPolicy()
                .checkAttributesAndObjectClasses(Policy.WARN));

        try {
            reader.readChangeRecord();
        } finally {
            reader.close();
        }
    }

    /**
     * Change Record throw an exception because the space added just before uid
     * provokes an LocalizedIllegalArgumentException.
     *
     * @throws Exception
     */
    @Test(expectedExceptions = DecodeException.class)
    public void testLDIFCRRParseModifyChangeRecordEntryReplaceLocalizedIllegalArgumentException()
            throws Exception {

        // @formatter:off
        final String[] strChangeRecord = {
            "version: 1",
            "dn: uid=scarter,ou=People,dc=example,dc=com",
            "changetype: modify",
            "replace: uid",
            " uid:Samantha Carter" // the space before provokes an LocalizedIllegalArgumentException
        };
        // @formatter:on

        final LDIFChangeRecordReader reader = new LDIFChangeRecordReader(strChangeRecord);
        reader.setSchema(Schema.getDefaultSchema());
        reader.setSchemaValidationPolicy(SchemaValidationPolicy.defaultPolicy());

        try {
            reader.readChangeRecord();
        } finally {
            reader.close();
        }

    }

    /**
     * Read a malformed Change Record : changetype is wrong.
     *
     * @throws Exception
     */
    @Test(expectedExceptions = LocalizedIllegalArgumentException.class)
    public void testLDIFCRRParseModifyChangeRecordEntryWithWrongChangetype() {
        // @formatter:off
        LDIFChangeRecordReader.valueOfLDIFChangeRecord(
            "version: 1",
            "dn: uid=scarter,ou=People,dc=example,dc=com",
            "changetype: modify",
            "oops:uidNumber" // wrong
        );
        // @formatter:on
    }

    /**
     * Read a malformed Change Record. 'pair.key' is null.
     *
     * @throws Exception
     */
    @Test(expectedExceptions = LocalizedIllegalArgumentException.class)
    public void testLDIFCRRParseModifyChangeRecordEntryWithNullPairKey() {
        // @formatter:off
        LDIFChangeRecordReader.valueOfLDIFChangeRecord(
            "version: 1",
            "dn: uid=scarter,ou=People,dc=example,dc=com",
            "changetype: modify",
            ":uidNumber" // wrong
        );
        // @formatter:on
    }

    /**
     * Read a well formed Change Record.
     *
     * @throws Exception
     */
    @Test()
    public void testLDIFCRRParseModifyChangeRecordEntryIncrement() throws Exception {
        // @formatter:off
        final ChangeRecord cr = LDIFChangeRecordReader.valueOfLDIFChangeRecord(
            "version: 1",
            "dn: uid=scarter,ou=People,dc=example,dc=com",
            "changetype: modify",
            "increment:uidNumber",
            "uidNumber: 1"
        );
        // @formatter:on

        assertThat(cr).isInstanceOf(ModifyRequest.class);
        ModifyRequest modifyRequest = (ModifyRequest) cr;

        assertThat(modifyRequest.getName().toString()).isEqualTo(
                "uid=scarter,ou=People,dc=example,dc=com");

        assertThat(modifyRequest.getModifications().get(0).getModificationType().toString())
                .isEqualTo("increment");
    }

    /**
     * Read an LDIF Record changes. Trying to modify DN using base64 newrdn.
     *
     * @throws Exception
     */
    @Test()
    public void testLDIFCRRparseModifyDNChangeRecordEntryRecordBase64NewRDN() throws Exception {

        // @formatter:off
        LDIFChangeRecordReader reader = new LDIFChangeRecordReader(
                "dn::ZGM9cGVvcGxlLGRjPWV4YW1wbGUsZGM9b3Jn",
                "changetype: modrdn",
                "newrdn::ZGM9cGVvcGxlLGRjPWV4YW1wbGUsZGM9Y29t",
                "deleteoldrdn: 1"
        );
        // @formatter:on

        assertThat(reader.hasNext()).isTrue();
        ChangeRecord record = reader.readChangeRecord();
        assertThat(record).isInstanceOf(ModifyDNRequest.class);
        ModifyDNRequest modifyDNRequest = (ModifyDNRequest) record;
        assertThat((Object) modifyDNRequest.getName()).isEqualTo(
                DN.valueOf("dc=people,dc=example,dc=org"));
        assertThat((Object) modifyDNRequest.getNewRDN()).isEqualTo(
                RDN.valueOf("dc=people,dc=example,dc=com"));
        assertThat(modifyDNRequest.isDeleteOldRDN()).isTrue();
        reader.close();
    }

    /**
     * Modifying a DN and delete the old one.
     *
     * @throws Exception
     */
    @Test()
    public void testLDIFCRRParseModifyDNChangeRecordEntry() throws Exception {
        // @formatter:off
        final LDIFChangeRecordReader reader = new LDIFChangeRecordReader(
            "version: 1",
            "dn: cn=scarter,dc=example,dc=com",
            "changetype: modrdn",
            "newrdn: cn=Susan Jacobs",
            "deleteoldrdn: 1"
        );
        // @formatter:on

        assertThat(reader.hasNext()).isTrue();
        ChangeRecord record = reader.readChangeRecord();
        assertThat(record).isInstanceOf(ModifyDNRequest.class);
        ModifyDNRequest modifyDNRequest = (ModifyDNRequest) record;
        assertThat((Object) modifyDNRequest.getName()).isEqualTo(
                DN.valueOf("cn=scarter,dc=example,dc=com"));
        assertThat((Object) modifyDNRequest.getNewRDN()).isEqualTo(RDN.valueOf("cn=Susan Jacobs"));
        assertThat(modifyDNRequest.isDeleteOldRDN()).isTrue();
        assertThat((Object) modifyDNRequest.getNewSuperior()).isEqualTo(null);
        reader.close();
    }

    /**
     * Try to change the dn, but the new rdn is missing. Unable to parse LDIF
     * modify DN record starting at line 1 with distinguished name
     * "cn=scarter,dc=example,dc=com" because there was no new RDN.
     *
     * @throws Exception
     */
    @Test(expectedExceptions = DecodeException.class)
    public void testLDIFCRRParseModifyDNChangeRecordEntryMalformedMissedNewRDN() throws Exception {
        // @formatter:off
        final LDIFChangeRecordReader reader = new LDIFChangeRecordReader(
            "version: 1",
            "dn: cn=scarter,dc=example,dc=com",
            "changetype: modrdn"
        );
        // @formatter:on

        try {
            reader.readChangeRecord();
        } finally {
            reader.close();
        }
    }

    /**
     * LDIFChangeRecord.parseModifyDNChangeRecordEntry and try to add an empty
     * new rdn throw an error.
     *
     * @throws Exception
     */
    @Test(expectedExceptions = DecodeException.class)
    public void testLDIFCRRParseModifyDNChangeRecordEntryKeyMalformedEmptyNewRDN() throws Exception {

        // @formatter:off
        final LDIFChangeRecordReader reader = new LDIFChangeRecordReader(
            "version: 1",
            "dn: cn=scarter,dc=example,dc=com",
            "changetype: modrdn",
            "newrdn:",
            "deleteoldrdn: 1"
        );
        // @formatter:on
        try {
            reader.readChangeRecord();
        } finally {
            reader.close();
        }
    }

    /**
     * LDIFChangeRecord.parseModifyDNChangeRecordEntry and try to add a
     * malformed rdn throw an error.
     *
     * @throws Exception
     */
    @Test(expectedExceptions = DecodeException.class)
    public void testLDIFCRRParseModifyDNChangeRecordEntryKeyValueMalformedRDN() throws Exception {
        // @formatter:off
        final LDIFChangeRecordReader reader = new LDIFChangeRecordReader(
            "version: 1",
            "dn: cn=scarter,dc=example,dc=com",
            "changetype: modrdn",
            "newrdn:oops", // wrong
            "deleteoldrdn: 1"
        );
        // @formatter:on
        try {
            reader.readChangeRecord();
        } finally {
            reader.close();
        }
    }

    /**
     * LDIFChangeRecord.parseModifyDNChangeRecordEntry and try to add a
     * malformed rdn throw an error. (deleteoldrdn value is wrong).
     *
     * @throws Exception
     */
    @Test(expectedExceptions = DecodeException.class)
    public void testLDIFCRRParseModifyDNChangeRecordEntryKeyValueMalformedDeleteOldRDN()
            throws Exception {

        // @formatter:off
        final LDIFChangeRecordReader reader = new LDIFChangeRecordReader(
            "version: 1",
            "dn: cn=scarter,dc=example,dc=com",
            "changetype: modrdn",
            "newrdn:cn=Susan Jacobs",
            "deleteoldrdn: cn=scarter,dc=example,dc=com" // wrong
        );
        // @formatter:on
        try {
            reader.readChangeRecord();
        } finally {
            reader.close();
        }
    }

    /**
     * LDIFChangeRecord.parseModifyDNChangeRecordEntry and try to add a
     * malformed rdn throw an error. (pair.key != deleteoldrdn)
     *
     * @throws Exception
     */
    @Test(expectedExceptions = DecodeException.class)
    public void testLDIFCRRParseModifyDNChangeRecordEntryKeyValueMalformedDeleteOldRDN2()
            throws Exception {
        // @formatter:off
        final LDIFChangeRecordReader reader = new LDIFChangeRecordReader(
            "version: 1",
            "dn: cn=scarter,dc=example,dc=com",
            "changetype: modrdn",
            "newrdn:cn=Susan Jacobs",
            "deleteold: 1" // wrong
        );
        // @formatter:on
        try {
            reader.readChangeRecord();
        } finally {
            reader.close();
        }
    }

    /**
     * Try to change the DN but deleteoldrdn is missing. Must throw an exception
     *
     * @throws Exception
     */
    @Test(expectedExceptions = DecodeException.class)
    public void testLDIFCRRParseModifyDNChangeRecordEntryKeyValueMalformedDeleteOldRDN3()
            throws Exception {

        // @formatter:off
        final LDIFChangeRecordReader reader = new LDIFChangeRecordReader(
            "version: 1",
            "dn: cn=scarter,dc=example,dc=com",
            "changetype: modrdn",
            "newrdn:cn=Susan Jacobs"
            // missing deleteoldrn: 1/0||true/false||yes/no
        );
        // @formatter:on
        try {
            reader.readChangeRecord();
        } finally {
            reader.close();
        }
    }

    /**
     * LDIFCRR delete old rdn and add a new superior to the new one.
     *
     * @throws Exception
     */
    @Test()
    public void testLDIFCRRparseModifyRecordEntryDeleteOldRDNFalse() throws Exception {

        // @formatter:off
        LDIFChangeRecordReader reader = new LDIFChangeRecordReader(
            "dn: cn=scarter,ou=People,dc=example,dc=com",
            "changeType: modrdn",
            "newrdn: cn=Susan Jacobs",
            "deleteOldRdn: 0",
            "newSuperior:ou=Manager,dc=example,dc=org"
        );
        // @formatter:on
        assertThat(reader.hasNext()).isTrue();
        ChangeRecord record = reader.readChangeRecord();
        assertThat(record).isInstanceOf(ModifyDNRequest.class);
        ModifyDNRequest modifyDNRequest = (ModifyDNRequest) record;

        assertThat((Object) modifyDNRequest.getName()).isEqualTo(
                DN.valueOf("cn=scarter,ou=People,dc=example,dc=com"));
        assertThat((Object) modifyDNRequest.getNewRDN()).isEqualTo(RDN.valueOf("cn=Susan Jacobs"));
        assertThat(modifyDNRequest.isDeleteOldRDN()).isFalse();
        assertThat((Object) modifyDNRequest.getNewSuperior().toString()).isEqualTo(
                "ou=Manager,dc=example,dc=org");
        reader.close();
    }

    /**
     * LDIFCRR delete old rdn and add a new superior.
     *
     * @throws Exception
     */
    @Test()
    public void testLDIFCRRparseModifyRecordEntryNewSuperior() throws Exception {
        // @formatter:off
        LDIFChangeRecordReader reader = new LDIFChangeRecordReader(
            "dn: cn=scarter,ou=People,dc=example,dc=com",
            "changeType: modrdn",
            "newrdn: cn=Susan Jacobs",
            "deleteOldRdn: 1",
            "newSuperior:ou=Manager,dc=example,dc=org"
        );
        // @formatter:on
        assertThat(reader.hasNext()).isTrue();
        ChangeRecord record = reader.readChangeRecord();
        assertThat(record).isInstanceOf(ModifyDNRequest.class);
        ModifyDNRequest modifyDNRequest = (ModifyDNRequest) record;

        assertThat((Object) modifyDNRequest.getName()).isEqualTo(
                DN.valueOf("cn=scarter,ou=People,dc=example,dc=com"));
        assertThat((Object) modifyDNRequest.getNewRDN()).isEqualTo(RDN.valueOf("cn=Susan Jacobs"));
        assertThat(modifyDNRequest.isDeleteOldRDN()).isTrue();
        assertThat((Object) modifyDNRequest.getNewSuperior().toString()).isEqualTo(
                "ou=Manager,dc=example,dc=org");
        reader.close();
    }

    /**
     * LDIFCRR delete old rdn and add a new superior willingly malformed. Syntax
     * is wrong "newSuperior:". Must throw an exception.
     *
     * @throws Exception
     */
    @Test(expectedExceptions = DecodeException.class)
    public void testLDIFCRRparseModifyRecordEntryNewSuperiorMalformed() throws Exception {
        // @formatter:off
        LDIFChangeRecordReader reader = new LDIFChangeRecordReader(
            "dn: cn=scarter,ou=People,dc=example,dc=com",
            "changeType: modrdn",
            "newrdn: cn=Susan Jacobs",
            "deleteOldRdn: 1",
            "newSuperior:" // wrong
        );
        // @formatter:on
        try {
            reader.readChangeRecord();
        } finally {
            reader.close();
        }
    }

    /**
     * LDIFCRR delete old rdn and add a new superior willingly malformed. Syntax
     * is wrong "newSuperior: Susan Jacobs". Must throw an exception.
     *
     * @throws Exception
     */
    @Test(expectedExceptions = DecodeException.class)
    public void testLDIFCRRparseModifyRecordEntryNewSuperiorMalformed2() throws Exception {

        // @formatter:off
        LDIFChangeRecordReader reader = new LDIFChangeRecordReader(
            "dn: cn=scarter,ou=People,dc=example,dc=com",
            "changeType: modrdn",
            "newrdn: cn=Susan Jacobs",
            "deleteOldRdn: 1",
            "newSuperior: Susan Jacobs" // wrong
        );
        // @formatter:on
        try {
            reader.readChangeRecord();
        } finally {
            reader.close();
        }
    }

    /**
     * Mock an inputstream for verifying LDIFChangeRecordReader close().
     *
     * @throws Exception
     */
    @Test(expectedExceptions = IOException.class)
    public void testChangeRecordReaderClosesAfterReading() throws Exception {

        final FileInputStream mockIn = mock(FileInputStream.class);
        final LDIFChangeRecordReader reader = new LDIFChangeRecordReader(mockIn);

        doThrow(new IOException()).when(mockIn).read();
        try {
            reader.readChangeRecord();
        } finally {
            reader.close();
            verify(mockIn, times(1)).close();
        }
    }

    /**
     * Read an ldif-changes using a List<String>.
     *
     * @throws Exception
     */
    @Test()
    public void testChangeRecordReaderUseListConstructor() throws Exception {
        // @formatter:off
        List<String> cr = Arrays.asList(
            "dn: dc=example,dc=com",
            "changetype: add",
            "objectClass: top",
            "objectClass: domainComponent",
            "dc: example"
        );
        // @formatter:on

        LDIFChangeRecordReader reader = new LDIFChangeRecordReader(cr);
        ChangeRecord rc = reader.readChangeRecord();
        assertThat(rc).isNotNull();
        assertThat(rc.getName().toString()).isEqualTo("dc=example,dc=com");
        reader.close();
    }

    /**
     * Try to read an LDIFChangeREcord without changetype. Must throw an
     * exception.
     *
     * @throws Exception
     */
    @Test(expectedExceptions = LocalizedIllegalArgumentException.class)
    public void testChangeRecordReaderHasNoChange() throws Exception {

        // @formatter:off
        LDIFChangeRecordReader.valueOfLDIFChangeRecord(
            "version: 1",
            "# Add a new entry without changes !",
            "dn: dc=example,dc=com"
        );
        // @formatter:on
    }

    /**
     * Try to read a null ldif-changes using a List<String>. Exception expected.
     *
     * @throws Exception
     */
    @Test(expectedExceptions = NoSuchElementException.class)
    public void testChangeRecordReaderDoesntAllowNull() throws Exception {
        List<String> cr = Arrays.asList();
        LDIFChangeRecordReader reader = new LDIFChangeRecordReader(cr);
        try {
            reader.readChangeRecord();
        } finally {
            reader.close();
        }
    }

    /**
     * Try to read a null ldif-changes using an empty String. Exception
     * expected.
     *
     * @throws Exception
     */
    @Test(expectedExceptions = NoSuchElementException.class)
    public void testChangeRecordReaderLDIFLineDoesntAllowNull() throws Exception {
        LDIFChangeRecordReader reader = new LDIFChangeRecordReader(new String());
        try {
            reader.readChangeRecord();
        } finally {
            reader.close();
        }
    }

    /**
     * LDIFChangeRecordReader cause NullPointerException when InputStream is
     * null.
     *
     * @throws Exception
     */
    @Test(expectedExceptions = NullPointerException.class)
    public void testChangeRecordReaderInpuStreamDoesntAllowNull() throws Exception {
        new LDIFChangeRecordReader((InputStream) null);
    }

    /**
     * LDIFChangeRecordReader cause NullPointerException when
     * valueOfLDIFChangeRecord is null.
     *
     * @throws Exception
     */
    @Test(expectedExceptions = LocalizedIllegalArgumentException.class)
    public void testValueOfLDIFChangeRecordDoesntAllowNull() throws Exception {
        LDIFChangeRecordReader.valueOfLDIFChangeRecord("");
    }

    /**
     * valueOfLDIFChangeRecord cause an exception due to the presence of
     * multiple change record.
     *
     * @throws Exception
     */
    @Test(expectedExceptions = LocalizedIllegalArgumentException.class)
    public void testValueOfLDIFChangeRecordDoesntAllowMultipleChangeRecords() throws Exception {
        // @formatter:off
        LDIFChangeRecordReader.valueOfLDIFChangeRecord(
            "version: 1",
            "# Add a new entry",
            "dn: cn=Fiona Jensen, ou=Marketing, dc=airius, dc=com",
            "changetype: add",
            "objectclass: top",
            "objectclass: person",
            "objectclass: organizationalPerson",
            "cn: Fiona Jensen",
            "sn: Jensen",
            "uid: fiona",
            "telephonenumber: +1 408 555 1212",
            "jpegphotojpegphoto:< http://www.forgerock.com/sites/default/files/forgerock_logo.png",
            "",
            "# Delete an existing entry",
            "dn: cn=Robert Jensen, ou=Marketing, dc=airius, dc=com",
            "changetype: delete"
        );
        // @formatter:on
    }

    /**
     * valueOfLDIFChangeRecord cause an exception due to badly formed ldif. In
     * this case, DN is missing.
     *
     * @throws Exception
     */
    @Test(expectedExceptions = LocalizedIllegalArgumentException.class)
    public void testValueOfLDIFChangeRecordMalformedLDIFDNIsMissing() throws Exception {
        // @formatter:off
        LDIFChangeRecordReader.valueOfLDIFChangeRecord(
            "version: 1",
            "# Add a new entry",
            "changetype: add",
            "objectclass: top",
            "objectclass: person",
            "objectclass: organizationalPerson",
            "cn: Fiona Jensen",
            "sn: Jensen",
            "uid: fiona",
            "telephonenumber: +1 408 555 1212"
        );
        // @formatter:on
        // DN is missing above.
    }

    /**
     * Try to read a malformed LDIF : The provided LDIF content did not contain
     * any LDIF change records. Coverage on AbstractLDIFReader -
     * readLDIFRecordDN.
     *
     * @throws Exception
     */
    @Test(expectedExceptions = LocalizedIllegalArgumentException.class)
    public void testValueOfLDIFChangeRecordMalformedLDIFContainingOnlyVersion() throws Exception {

        // @formatter:off
        LDIFChangeRecordReader.valueOfLDIFChangeRecord(
                "version: 1"
        );
        // @formatter:on
    }

    /**
     * Try to read a malformed LDIF : Unable to parse LDIF entry starting at
     * line 1 because the line ":wrong" does not include an attribute name.
     * Coverage on AbstractLDIFReader - readLDIFRecordDN.
     *
     * @throws Exception
     */
    @Test(expectedExceptions = LocalizedIllegalArgumentException.class)
    public void testValueOfLDIFChangeRecordMalformedLDIFContainingVersionAndWrongLine()
            throws Exception {

        // @formatter:off
        LDIFChangeRecordReader.valueOfLDIFChangeRecord(
                "version: 1",
                ":wrong"
        );
        // @formatter:on
    }

    /**
     * Try to read a standard Change Record LDIF.
     *
     * @throws Exception
     */
    @Test()
    public void testValueOfLDIFChangeRecordStandardLDIF() throws Exception {

        // @formatter:off
        final ChangeRecord cr =
                LDIFChangeRecordReader.valueOfLDIFChangeRecord(getStandardLDIFChangeRecord());
        // @formatter:on

        AddRequest addRequest = (AddRequest) cr;
        assertThat(cr.getName().toString()).isEqualTo("uid=scarter,ou=People,dc=example,dc=com");
        assertThat(addRequest.containsAttribute("sn")).isTrue();
        assertThat(addRequest.containsAttribute("cn")).isTrue();
        assertThat(addRequest.getAttributeCount()).isEqualTo(10);
    }

}

package org.opendj.scratch.be;

import java.util.Map;

import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.requests.ModifyRequest;
import org.forgerock.opendj.ldif.EntryReader;

public interface Backend {

    void close();

    void importEntries(EntryReader entries) throws Exception;

    void open() throws Exception;

    void modifyEntry(ModifyRequest request) throws LdapException;

    Entry readEntryByDescription(ByteString description) throws LdapException;

    Entry readEntryByDN(DN name) throws LdapException;

    void initialize(Map<String, String> options) throws Exception;

}

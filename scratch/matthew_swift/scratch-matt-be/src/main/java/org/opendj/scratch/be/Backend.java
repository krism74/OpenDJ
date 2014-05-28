package org.opendj.scratch.be;

import java.util.Map;

import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldap.ErrorResultException;
import org.forgerock.opendj.ldap.requests.ModifyRequest;
import org.forgerock.opendj.ldif.EntryReader;

public interface Backend {

    void close();

    void importEntries(EntryReader entries, Map<String, String> options) throws Exception;

    void initialize(Map<String, String> options) throws Exception;

    void modifyEntry(ModifyRequest request) throws ErrorResultException;

    Entry readEntryByDN(DN name) throws ErrorResultException;

    Entry readEntryByDescription(ByteString description) throws ErrorResultException;

}

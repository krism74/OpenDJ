package org.opendj.scratch.be;

import java.util.Map;

import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldap.ErrorResultException;
import org.forgerock.opendj.ldap.requests.ModifyRequest;
import org.forgerock.opendj.ldif.EntryReader;

public interface Backend {

    void importEntries(EntryReader ldif, Map<String, String> options) throws Exception;

    Entry readEntry(DN name) throws ErrorResultException;

    void close();

    void initialize(Map<String, String> options) throws Exception;

    void modifyEntry(ModifyRequest request) throws ErrorResultException;

}

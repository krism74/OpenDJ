/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyrighted [year] [name of copyright owner]".
 *
 * Copyright 2012 ForgeRock AS. All rights reserved.
 */

package org.forgerock.opendj.rest2ldap;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.forgerock.json.fluent.JsonPointer;
import org.forgerock.json.fluent.JsonValue;
import org.forgerock.opendj.ldap.Attribute;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldap.ResultHandler;
import org.forgerock.resource.provider.Context;

/**
 *
 */
public interface AttributeMapper {

    /**
     * Returns an unmodifiable set containing the names of the LDAP attributes
     * required by this attribute mapper. The returned set should only contain
     * the names of attributes found in the LDAP entry directly associated with
     * the resource.
     *
     * @return An unmodifiable set containing the names of the LDAP attributes
     *         required by this attribute mapper.
     */
    Set<String> getAllLDAPAttributes();

    /**
     * Returns an unmodifiable collection containing the names of the LDAP
     * attributes required by this attribute mapper and which are associated
     * with the provided resource attribute. The returned set should only
     * contain the names of attributes found in the LDAP entry directly
     * associated with the resource.
     *
     * @param resourceAttribute
     *            The name of the resource attribute requested by the client.
     * @return An unmodifiable collection containing the names of the LDAP
     *         attributes required by this attribute mapper and which are
     *         associated with the provided resource attribute.
     */
    Collection<String> getLDAPAttributesFor(JsonPointer resourceAttribute);

    /**
     * Transforms attributes contained in the provided LDAP entry to JSON
     * content, invoking a completion handler once the transformation has
     * completed.
     * <p>
     * This method is invoked whenever an LDAP entry is converted to a REST
     * resource, i.e. when responding to read, query, create, put, or patch
     * requests.
     *
     * @param c
     * @param e
     * @param v
     * @param h
     */
    void toJson(Context c, Entry e, JsonValue v, ResultHandler<JsonValue> h);

    /**
     * Transforms JSON content in the provided JSON value to LDAP attributes,
     * invoking a completion handler once the transformation has completed.
     * <p>
     * This method is invoked whenever a REST resource is converted to an LDAP
     * entry or LDAP modification, i.e. when performing create, put, or patch
     * requests.
     *
     * @param c
     * @param v
     * @param a
     * @param h
     */
    void toLDAP(Context c, JsonValue v, List<Attribute> a, ResultHandler<Entry> h);

    // TODO: methods for obtaining schema information (e.g. name, description,
    // type information).
    // TODO: methods for creating filters createLDAPEqualityFilter().
    // TODO: methods for creating sort controls.
}
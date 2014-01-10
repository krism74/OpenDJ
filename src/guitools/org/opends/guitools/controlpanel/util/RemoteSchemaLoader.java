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
 *      Copyright 2009-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2013-2014 ForgeRock AS
 */

package org.opends.guitools.controlpanel.util;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.InitialLdapContext;

import org.opends.guitools.controlpanel.browser.BrowserController;
import org.opends.guitools.controlpanel.datamodel.CustomSearchResult;
import org.opends.server.config.ConfigConstants;
import org.opends.server.config.ConfigException;
import org.opends.server.schema.AttributeTypeSyntax;
import org.opends.server.schema.ObjectClassSyntax;
import org.opends.server.types.AttributeType;
import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.InitializationException;
import org.opends.server.types.ObjectClass;
import org.opends.server.types.Schema;

/**
 * Class used to retrieve the schema from the schema files.
 */
public class RemoteSchemaLoader extends SchemaLoader
{
  private Schema schema;

  /**
   * Constructor.
   */
  public RemoteSchemaLoader()
  {
    super();
  }

  /**
   * Reads the schema.
   *
   * @param ctx
   *          the connection to be used to load the schema.
   * @throws NamingException
   *           if an error occurs reading the schema.
   * @throws DirectoryException
   *           if an error occurs parsing the schema.
   * @throws InitializationException
   *           if an error occurs finding the base schema.
   * @throws ConfigException
   *           if an error occurs loading the configuration required to use the
   *           schema classes.
   */
  public void readSchema(InitialLdapContext ctx) throws NamingException,
      DirectoryException, InitializationException, ConfigException
  {
    final String[] schemaAttrs =
        { ConfigConstants.ATTR_ATTRIBUTE_TYPES_LC,
          ConfigConstants.ATTR_OBJECTCLASSES_LC };

    final SearchControls searchControls = new SearchControls();
    searchControls.setSearchScope(SearchControls.OBJECT_SCOPE);
    searchControls.setReturningAttributes(schemaAttrs);

    final String filter = BrowserController.ALL_OBJECTS_FILTER;
    final NamingEnumeration<SearchResult> srs =
        ctx.search(ConfigConstants.DN_DEFAULT_SCHEMA_ROOT, filter,
            searchControls);
    SearchResult sr = null;
    try
    {
      while (srs.hasMore())
      {
        sr = srs.next();
      }
    }
    finally
    {
      srs.close();
    }
    final CustomSearchResult csr =
        new CustomSearchResult(sr, ConfigConstants.DN_DEFAULT_SCHEMA_ROOT);

    schema = getBaseSchema();

    for (final String str : schemaAttrs)
    {
      registerSchemaAttr(csr, str);
    }
  }

  private void registerSchemaAttr(final CustomSearchResult csr,
      final String schemaAttr) throws DirectoryException
  {
    @SuppressWarnings({ "unchecked", "rawtypes" })
    final Set<String> remainingAttrs =
        new HashSet<String>((List) csr.getAttributeValues(schemaAttr));

    while (!remainingAttrs.isEmpty())
    {
      DirectoryException lastException = null;
      boolean oneRegistered = false;
      Set<String> registered = new HashSet<String>();
      for (final String definition : remainingAttrs)
      {
        final ByteStringBuilder sb = new ByteStringBuilder();
        sb.append(definition);
        try
        {
          if (schemaAttr.equals(ConfigConstants.ATTR_ATTRIBUTE_TYPES_LC))
          {
            final AttributeType attrType =
                AttributeTypeSyntax.decodeAttributeType(sb, schema, false);
            schema.registerAttributeType(attrType, true);
          }
          else if (schemaAttr.equals(ConfigConstants.ATTR_OBJECTCLASSES_LC))
          {
            final ObjectClass oc =
                ObjectClassSyntax.decodeObjectClass(sb, schema, false);
            schema.registerObjectClass(oc, true);
          }
          oneRegistered = true;
          registered.add(definition);
        }
        catch (DirectoryException de)
        {
          lastException = de;
        }
      }
      if (!oneRegistered)
      {
        throw lastException;
      }
      remainingAttrs.removeAll(registered);
    }
  }

  /**
   * Returns the schema that was read.
   *
   * @return the schema that was read.
   */
  @Override
  public Schema getSchema()
  {
    return schema;
  }
}

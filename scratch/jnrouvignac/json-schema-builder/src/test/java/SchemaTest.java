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
 *      Copyright 2013 ForgeRock AS
 */
package org.opends.server.protocols.http.jsonschema;

import static org.assertj.core.api.Assertions.*;
import static org.testng.Assert.*;

import org.codehaus.jackson.JsonParser;
import org.codehaus.jackson.map.ObjectMapper;
import org.forgerock.json.schema.validator.CollectErrorsHandler;
import org.forgerock.json.schema.validator.ObjectValidatorFactory;
import org.forgerock.json.schema.validator.validators.Validator;
import org.opends.server.DirectoryServerTestCase;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class SchemaTest extends DirectoryServerTestCase
{

  private static final ObjectMapper JSON_MAPPER = new ObjectMapper().configure(
      JsonParser.Feature.ALLOW_COMMENTS, true);

  @DataProvider(name = "invalidConfigs")
  public Object[][] getInvalidConfigs()
  {
    return new Object[][] {{""
  // @formatter:off
        + "{"
        + "  \"ldapConnectionFactories\" : \"SOME STUFF\"," // this is invalid
        + "  \"authenticationFilter\" : {"
        + "    \"supportHTTPBasicAuthentication\"  : true,"
        + "    \"supportAltAuthentication\"        : true,"
        + "    \"altAuthenticationUsernameHeader\" : \"X-OpenIDM-Username\","
        + "    \"altAuthenticationPasswordHeader\" : \"X-OpenIDM-Password\","
        + "    \"searchBaseDN\"         : \"ou=people,dc=example,dc=com\","
        + "    \"searchScope\"          : \"sub\","
        + "    \"searchFilterTemplate\" : \"(&(objectClass=inetOrgPerson)(uid=%s))\""
        + "  },"
        + "  \"servlet\" : {"
        + "    \"mappings\" : {"
        + "    }"
        + "  }"
        + "}"
  // @formatter:on
      },
      {""
  // @formatter:off
        + "{"
        + "  \"authenticationFilter\" : {"
        + "    \"supportHTTPBasicAuthentication\"  : true,"
        + "    \"supportAltAuthentication\"        : true,"
        + "    \"altAuthenticationUsernameHeader\" : \"X-OpenIDM-Username\","
        + "    \"altAuthenticationPasswordHeader\" : \"X-OpenIDM-Password\","
        // start of invalid content
        + "    \"reuseAuthenticatedConnection\" : true,"
        + "    \"method\" : \"search-simple\","
        + "    \"bindLDAPConnectionFactory\" : \"default\","
        + "    \"saslAuthzIdTemplate\" : \"dn:uid=%s,ou=people,dc=example,dc=com\","
        + "    \"searchLDAPConnectionFactory\" : \"root\","
        // end of invalid content
        + "    \"searchBaseDN\"         : \"ou=people,dc=example,dc=com\","
        + "    \"searchScope\"          : \"sub\","
        + "    \"searchFilterTemplate\" : \"(&(objectClass=inetOrgPerson)(uid=%s))\""
        + "  },"
        + "  \"servlet\" : {"
        + "    \"mappings\" : {"
        + "    }"
        + "  }"
        + "}"
  // @formatter:on
      },
      {""
  // @formatter:off
        + "{"
        + "  \"authenticationFilter\" : {"
        + "    \"supportHTTPBasicAuthentication\"  : true,"
        + "    \"supportAltAuthentication\"        : true,"
        + "    \"altAuthenticationUsernameHeader\" : \"X-OpenIDM-Username\","
        + "    \"altAuthenticationPasswordHeader\" : \"X-OpenIDM-Password\","
        + "    \"searchBaseDN\"         : \"ou=people,dc=example,dc=com\","
        + "    \"searchScope\"          : \"sub\","
        + "    \"searchFilterTemplate\" : \"(&(objectClass=inetOrgPerson)(uid=%s))\""
        + "  },"
        + "  \"servlet\" : {"
        + "    \"mappings\" : {"
        + "      \"/users\" : {"
        + "          \"baseDN\"              : \"ou=people,dc=example,dc=com\","
        + "          \"readOnUpdatePolicy\"  : \"controls\","
        + "          \"useSubtreeDelete\"    : false,"
        + "          \"usePermissiveModify\" : true,"
        + "          \"etagAttribute\"       : \"etag\","
        + "          \"namingStrategy\"      : {"
        + "              \"strategy\"    : \"clientDNNaming\","
        + "              \"dnAttribute\" : \"uid\""
        + "          },"
        + "          \"additionalLDAPAttributes\" : ["
        + "              {"
        + "                    \"type\" : \"objectClass\","
        + "                    \"values\" : ["
        + "                        \"top\","
        + "                        \"person\","
        + "                        \"organizationalPerson\","
        + "                        \"inetOrgPerson\""
        + "                    ]"
        + "              }"
        + "          ],"
        + "          \"attributes\" : {"
        + "              \"schemas\"     : { \"somethig wrong\" : \"wrong\" }" // this is wrong
        + "          }"
        + "      }"
        + "    }"
        + "  }"
        + "}"
  // @formatter:on
      } };
  }

  @DataProvider(name = "validConfigs")
  public Object[][] getValidConfigs()
  {
    return new Object[][] {{""
  // @formatter:off
        + "{"
        + "  \"authenticationFilter\" : {"
        + "    \"supportHTTPBasicAuthentication\"  : true,"
        + "    \"supportAltAuthentication\"        : true,"
        + "    \"altAuthenticationUsernameHeader\" : \"X-OpenIDM-Username\","
        + "    \"altAuthenticationPasswordHeader\" : \"X-OpenIDM-Password\","
        + "    \"searchBaseDN\"         : \"ou=people,dc=example,dc=com\","
        + "    \"searchScope\"          : \"sub\","
        + "    \"searchFilterTemplate\" : \"(&(objectClass=inetOrgPerson)(uid=%s))\""
        + "  },"
        + "  \"servlet\" : {"
        + "    \"mappings\" : {"
        // + "      \"/users\" : {"
        // + "      }"
        + "    }"
        + "  }"
        + "}"
  // @formatter:on
      },
      {""
  // @formatter:off
        + "{"
        + "  \"authenticationFilter\" : {"
        + "    \"supportHTTPBasicAuthentication\"  : true,"
        + "    \"supportAltAuthentication\"        : true,"
        + "    \"altAuthenticationUsernameHeader\" : \"X-OpenIDM-Username\","
        + "    \"altAuthenticationPasswordHeader\" : \"X-OpenIDM-Password\","
        + "    \"searchBaseDN\"         : \"ou=people,dc=example,dc=com\","
        + "    \"searchScope\"          : \"sub\","
        + "    \"searchFilterTemplate\" : \"(&(objectClass=inetOrgPerson)(uid=%s))\""
        + "  },"
        + "  \"servlet\" : {"
        + "    \"mappings\" : {"
        + "      \"/users\" : {"
        + "          \"baseDN\"              : \"ou=people,dc=example,dc=com\","
        + "          \"readOnUpdatePolicy\"  : \"controls\","
        + "          \"useSubtreeDelete\"    : false,"
        + "          \"usePermissiveModify\" : true,"
        + "          \"etagAttribute\"       : \"etag\","
        + "          \"namingStrategy\"      : {"
        + "              \"strategy\"    : \"clientDNNaming\","
        + "              \"dnAttribute\" : \"uid\""
        + "          },"
        + "          \"additionalLDAPAttributes\" : ["
        + "              {"
        + "                    \"type\" : \"objectClass\","
        + "                    \"values\" : ["
        + "                        \"top\","
        + "                        \"person\","
        + "                        \"organizationalPerson\","
        + "                        \"inetOrgPerson\""
        + "                    ]"
        + "              }"
        + "          ],"
        + "          \"attributes\" : {"
        + "              \"schemas\"     : { \"constant\" : [ \"urn:scim:schemas:core:1.0\" ] },"
        + "              \"_id\"         : { \"simple\"   : { \"ldapAttribute\" : \"uid\", \"isSingleValued\" : true, \"isRequired\" : true, \"writability\" : \"createOnly\" } },"
        + "              \"_rev\"        : { \"simple\"   : { \"ldapAttribute\" : \"etag\", \"isSingleValued\" : true, \"writability\" : \"readOnly\" } },"
        + "              \"userName\"    : { \"simple\"   : { \"ldapAttribute\" : \"mail\", \"isSingleValued\" : true, \"writability\" : \"readOnly\" } },"
        + "              \"displayName\" : { \"simple\"   : { \"ldapAttribute\" : \"cn\", \"isSingleValued\" : true, \"isRequired\" : true } },"
        + "              \"name\"        : { \"object\"   : {"
        + "                    \"givenName\"  : { \"simple\" : { \"ldapAttribute\" : \"givenName\", \"isSingleValued\" : true } },"
        + "                    \"familyName\" : { \"simple\" : { \"ldapAttribute\" : \"sn\", \"isSingleValued\" : true, \"isRequired\" : true } }"
        + "              } },"
        + "              \"manager\"     : { \"reference\" : {"
        + "                    \"ldapAttribute\" : \"manager\","
        + "                    \"baseDN\"        : \"ou=people,dc=example,dc=com\","
        + "                    \"primaryKey\"    : \"uid\","
        + "                    \"mapper\"         : { \"object\" : {"
        + "                        \"_id\"         : { \"simple\"   : { \"ldapAttribute\" : \"uid\", \"isSingleValued\" : true, \"isRequired\" : true } },"
        + "                        \"displayName\" : { \"simple\"   : { \"ldapAttribute\" : \"cn\", \"isSingleValued\" : true, \"writability\" : \"readOnlyDiscardWrites\" } }"
        + "                    } }"
        + "              } },"
        + "              \"groups\"     : { \"reference\" : {"
        + "                    \"ldapAttribute\" : \"isMemberOf\","
        + "                    \"baseDN\"        : \"ou=groups,dc=example,dc=com\","
        + "                    \"writability\"   : \"readOnly\","
        + "                    \"primaryKey\"    : \"cn\","
        + "                    \"mapper\"        : { \"object\" : {"
        + "                        \"_id\"         : { \"simple\"   : { \"ldapAttribute\" : \"cn\", \"isSingleValued\" : true } }"
        + "                    } }"
        + "              } },"
        + "              \"contactInformation\" : { \"object\" : {"
        + "                    \"telephoneNumber\" : { \"simple\" : { \"ldapAttribute\" : \"telephoneNumber\", \"isSingleValued\" : true } },"
        + "                    \"emailAddress\"    : { \"simple\" : { \"ldapAttribute\" : \"mail\", \"isSingleValued\" : true } }"
        + "              } },"
        + "              \"meta\"        : { \"object\" : {"
        + "                    \"created\"      : { \"simple\" : { \"ldapAttribute\" : \"createTimestamp\", \"isSingleValued\" : true, \"writability\" : \"readOnly\" } },"
        + "                    \"lastModified\" : { \"simple\" : { \"ldapAttribute\" : \"modifyTimestamp\", \"isSingleValued\" : true, \"writability\" : \"readOnly\" } }"
        + "              } }"
        + "          }"
        + "      }"
        + "    }"
        + "  }"
        + "}"
  // @formatter:on
      } };
  }

  @Test(dataProvider = "validConfigs")
  public void validHttpConfigsJsonSchema(String httpConfigJson)
      throws Exception
  {
    final Object content = JSON_MAPPER.readValue(httpConfigJson, Object.class);
    final JsonSchemaObject schema = Schema.getHttpConnectionHandlerSchema();
    final CollectErrorsHandler handler = new CollectErrorsHandler();
    final Validator v =
        ObjectValidatorFactory.getTypeValidator(schema.toJsonSchema());
    v.validate(content, null, handler);
    final String errorMessage = handler.getExceptions().toString();
    assertFalse(handler.hasError(), errorMessage);
    assertThat(handler.getExceptions()).isEmpty();
  }

  @Test(dataProvider = "invalidConfigs")
  public void invalidHttpConfigs(String httpConfigJson) throws Exception
  {
    final Object content = JSON_MAPPER.readValue(httpConfigJson, Object.class);
    final JsonSchemaObject schema = Schema.getHttpConnectionHandlerSchema();
    final CollectErrorsHandler handler = new CollectErrorsHandler();
    final Validator v =
        ObjectValidatorFactory.getTypeValidator(schema.toJsonSchema());
    v.validate(content, null, handler);
    final String errorMessage = handler.getExceptions().toString();
    assertTrue(handler.hasError(), errorMessage);
    assertThat(handler.getExceptions()).isNotEmpty();
  }
}

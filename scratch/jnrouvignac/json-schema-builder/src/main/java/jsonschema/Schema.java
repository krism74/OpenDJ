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

public class Schema
{

  private static JsonSchemaObject schema(JsonSchemaPart... parts)
  {
    return new JsonSchemaObject(parts);
  }

  private static JsonSchemaProperty property(String key, boolean required,
      JsonSchema type)
  {
    return new JsonSchemaProperty(key, required, type);
  }

  private static JsonSchemaPatternProperty patternProperty(String pattern,
      boolean required, JsonSchema type)
  {
    return new JsonSchemaPatternProperty(pattern, required, type);
  }

  private static JsonSchemaBoolean bool()
  {
    return new JsonSchemaBoolean();
  }

  private static JsonSchemaString string()
  {
    return new JsonSchemaString();
  }

  private static JsonSchemaInteger integer()
  {
    return new JsonSchemaInteger();
  }

  private static JsonSchemaNumber number()
  {
    return new JsonSchemaNumber();
  }

  private static JsonSchemaArray array(JsonSchema type)
  {
    return new JsonSchemaArray(type);
  }

  private static <T> JsonSchemaEnum<T> enumeration(T... values)
  {
    return new JsonSchemaEnum<T>(values);
  }

  private static JsonSchemaDefinition definition(String name, JsonSchema schema)
  {
    return new JsonSchemaDefinition(name, schema);
  }

  static JsonSchemaObject getHttpConnectionHandlerSchema()
  {
    // @formatter:off
    final JsonSchemaProperty propWritability = property("writability", false, string().enumeration(
        "createOnly",
        "createOnlyDiscardWrites",
        "readOnly",
        "readOnlyDiscardWrites",
        "readWrite"
    ));

    final JsonSchemaDefinition defConstant =
        definition("constant", schema(
            property("constant", true, array(string()).min(1).max(1))
        ));

    final JsonSchemaDefinition defSimple =
        definition("simple", schema(
            property("simple", true, schema(
                property("ldapAttribute", true, string()),
                property("defaultJSONValue", false, schema(
                    patternProperty(".", true, string())
                )),
                property("isBinary", false, bool()),
                property("isRequired", false, bool()),
                property("isSingleValued", false, bool()),
                propWritability
            ))
        ));

    final JsonSchemaForwardDecl defObjectForwardDecl = new JsonSchemaForwardDecl();
    final JsonSchemaForwardDecl defReferenceForwardDecl = new JsonSchemaForwardDecl();
    final JsonSchemaForwardDecl defAttributesForwardDecl = new JsonSchemaForwardDecl();

    final JsonSchemaDefinition defReference =
        definition("reference", schema(
            property("reference", true, schema(
                property("ldapAttribute", true, string()),
                property("baseDN", true, string()),
                property("primaryKey", true, string()),
                property("isRequired", false, bool()),
                property("isSingleValued", false, bool()),
                property("searchFilter", false, string()),
                propWritability,
                property("mapper", true, schema(
                    property("object", true, defAttributesForwardDecl)
                ))
            ))
        ));
    defReferenceForwardDecl.define(defReference.reference());


    final JsonSchemaDefinition defObject = definition("object", schema(
        property("object", true, defAttributesForwardDecl)
    ));
    defObjectForwardDecl.define(defObject.reference());

    final JsonSchemaDefinition defAttributes =
        definition("attributes", schema(
            patternProperty("^(\\w|_)+$", true, schema().oneOf(
                defConstant.reference(),
                defSimple.reference(),
                defReference.reference(),
                defObject.reference()
            ))
        ));
    defAttributesForwardDecl.define(defAttributes.reference());

    return schema(
        property("authenticationFilter", true, schema(
            property("supportHTTPBasicAuthentication", true, bool()),
            property("supportAltAuthentication", true, bool()),
            property("altAuthenticationPasswordHeader", true, string()),
            property("altAuthenticationUsernameHeader", true, string()),
            property("searchBaseDN", true, string()),
            property("searchScope", true, string()),
            property("searchFilterTemplate", true, string())
        )),
        property("servlet", true, schema(
            property("mappings", true, schema(
                patternProperty("^/(_|\\w)+(/(_|\\w)+)?$", false, schema(
                    property("baseDN", true, string()),
                    property("readOnUpdatePolicy", false, string()),
                    property("useSubtreeDelete", true, bool()),
                    property("usePermissiveModify",true, bool()),
                    property("etagAttribute", false, string()),
                    property("namingStrategy", true, schema().oneOf(
                        schema(
                            property("strategy", true, string().enumeration("clientDNNaming")),
                            property("dnAttribute", true, string())
                        ),
                        schema(
                            property("strategy", true, string().enumeration("clientNaming")),
                            property("dnAttribute", true, string()),
                            property("idAttribute", true, string())
                        ),
                        schema(
                            property("strategy", true, string().enumeration("serverNaming")),
                            property("dnAttribute", true, string()),
                            property("idAttribute", true, string())
                        )
                    )),
                    property("additionalLDAPAttributes", true, array(
                        schema(
                            property("type", true, string()),
                            property("values", true, array(string()).min(1).uniqueItems(true))
                        )
                    )),
                    property("attributes", true, defAttributes.reference())
                ))
            ))
        )),
        defAttributes,
        defConstant,
        defSimple,
        defReference,
        defObject
    );
    // @formatter:on
  }

  static JsonSchemaObject getGatewaySchema()
  {
    // @formatter:off
    final JsonSchemaProperty propWritability = property("writability", false, string().enumeration(
        "createOnly",
        "createOnlyDiscardWrites",
        "readOnly",
        "readOnlyDiscardWrites",
        "readWrite"
    ));

    final JsonSchemaDefinition defConstant =
        definition("constant", schema(
            property("constant", true, array(string()).min(1).max(1))
        ));

    final JsonSchemaDefinition defSimple =
        definition("simple", schema(
            property("simple", true, schema(
                property("ldapAttribute", true, string()),
                property("defaultJSONValue", false, schema(
                    patternProperty(".", true, string())
                )),
                property("isBinary", false, bool()),
                property("isRequired", false, bool()),
                property("isSingleValued", false, bool()),
                propWritability
            ))
        ));

    final JsonSchemaForwardDecl defObjectForwardDecl = new JsonSchemaForwardDecl();
    final JsonSchemaForwardDecl defReferenceForwardDecl = new JsonSchemaForwardDecl();
    final JsonSchemaForwardDecl defAttributesForwardDecl = new JsonSchemaForwardDecl();

    final JsonSchemaDefinition defReference =
        definition("reference", schema(
            property("reference", true, schema(
                property("ldapAttribute", true, string()),
                property("baseDN", true, string()),
                property("primaryKey", true, string()),
                property("isRequired", false, bool()),
                property("isSingleValued", false, bool()),
                property("searchFilter", false, string()),
                propWritability,
                property("mapper", true, schema(
                    property("object", true, defAttributesForwardDecl)
                ))
            ))
        ));
    defReferenceForwardDecl.define(defReference.reference());


    final JsonSchemaDefinition defObject = definition("object", schema(
        property("object", true, defAttributesForwardDecl)
    ));
    defObjectForwardDecl.define(defObject.reference());

    final JsonSchemaDefinition defAttributes =
        definition("attributes", schema(
            patternProperty("^(\\w|_)+$", true, schema().oneOf(
                defConstant.reference(),
                defSimple.reference(),
                defReference.reference(),
                defObject.reference()
            ))
        ));
    defAttributesForwardDecl.define(defAttributes.reference());

    final JsonSchemaObject schemaHostAndPort = schema(
        property("hostname", true, string()),
        property("port", true, integer().minimum(1).maximum(65535))
    );

    return schema(
        property("ldapConnectionFactories", true, schema(
            patternProperty(".", true, schema(
                property("connectionPoolSize", true, integer().minimum(1)),
                property("heartBeatIntervalSeconds", false, integer().minimum(0)),
                property("primaryLDAPServers", true, array(
                    schemaHostAndPort
                )),
                property("secondaryLDAPServers", false, array(
                    schemaHostAndPort
                )),
                property("inheritFrom", false, string()),
                property("authentication", true, schema(
                    property("bindDN", true, string()),
                    property("bindPassword", true, string())
                ))
            ))
        )),
        property("authenticationFilter", true, schema(
            property("supportHTTPBasicAuthentication", true, bool()),
            property("supportAltAuthentication", true, bool()),
            property("altAuthenticationPasswordHeader", true, string()),
            property("altAuthenticationUsernameHeader", true, string()),
            property("reuseAuthenticatedConnection", false, bool()),
            property("method", false, enumeration("search-simple", "sasl-plain", "simple")),
            property("bindLDAPConnectionFactory", false, string()),
            property("saslAuthzIdTemplate", false, string()),
            property("saslAuthzIdTemplate", false, string()),
            property("searchBaseDN", true, string()),
            property("searchScope", true, string()),
            property("searchFilterTemplate", true, string())
        )),
        property("servlet", true, schema(
            property("ldapConnectionFactory", true, string()),
            property("authorizationPolicy", false, enumeration("proxy", "none", "reuse")),
            property("proxyAuthzIdTemplate", false, string()),
            property("mappings", true, schema(
                patternProperty("^/(_|\\w)+(/(_|\\w)+)?$", false, schema(
                    property("baseDN", true, string()),
                    property("readOnUpdatePolicy", false, string()),
                    property("useSubtreeDelete", true, bool()),
                    property("usePermissiveModify",true, bool()),
                    property("etagAttribute", false, string()),
                    property("namingStrategy", true, schema().oneOf(
                        schema(
                            property("strategy", true, string().enumeration("clientDNNaming")),
                            property("dnAttribute", true, string())
                        ),
                        schema(
                            property("strategy", true, string().enumeration("clientNaming")),
                            property("dnAttribute", true, string()),
                            property("idAttribute", true, string())
                        ),
                        schema(
                            property("strategy", true, string().enumeration("serverNaming")),
                            property("dnAttribute", true, string()),
                            property("idAttribute", true, string())
                        )
                    )),
                    property("additionalLDAPAttributes", true, array(
                        schema(
                            property("type", true, string()),
                            property("values", true, array(string()).min(1).uniqueItems(true))
                        )
                    )),
                    property("attributes", true, defAttributes.reference())
                ))
            ))
        )),
        defAttributes,
        defConstant,
        defSimple,
        defReference,
        defObject
    );
    // @formatter:on
  }

}

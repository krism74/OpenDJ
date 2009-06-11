package org.opends.common.api.raw.request.filter;

import org.opends.server.protocols.asn1.ASN1Writer;
import org.opends.common.protocols.ldap.LDAPEncoder;

import java.io.IOException;

/**
 * Created by IntelliJ IDEA. User: digitalperk Date: Jun 10, 2009 Time: 11:48:45
 * AM To change this template use File | Settings | File Templates.
 */
public final class RawAndFilter extends RawCompoundFilter
{
  public RawAndFilter(RawFilter component)
  {
    super(component);
  }

  public void encodeLDAP(ASN1Writer writer) throws IOException
  {
    LDAPEncoder.encodeFilter(writer, this);
  }

  public void toString(StringBuilder buffer)
  {
    buffer.append("AndFilter(components=");
    buffer.append(components);
    buffer.append(")");
  }
}

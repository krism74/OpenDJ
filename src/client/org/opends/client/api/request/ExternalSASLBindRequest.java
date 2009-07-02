package org.opends.client.api.request;

import static org.opends.server.util.ServerConstants.SASL_MECHANISM_EXTERNAL;
import static org.opends.server.util.ServerConstants.SASL_MECHANISM_CRAM_MD5;
import static org.opends.server.util.ServerConstants.SASL_DEFAULT_PROTOCOL;
import org.opends.server.util.Validator;
import org.opends.server.types.ByteString;
import org.opends.common.api.DN;

import javax.security.sasl.SaslClient;
import javax.security.sasl.SaslException;
import javax.security.sasl.Sasl;

/**
 * Created by IntelliJ IDEA.
 * User: boli
 * Date: Jul 1, 2009
 * Time: 5:55:58 PM
 * To change this template use File | Settings | File Templates.
 */
public final class ExternalSASLBindRequest extends AbstractSASLBindRequest
{
  private SaslClient saslClient;
  private ByteString outgoingCredentials = null;

  private String authorizationID;

  public ExternalSASLBindRequest()
  {
  }

  public ExternalSASLBindRequest(String authorizationID)
  {
    Validator.ensureNotNull(authorizationID);
    this.authorizationID = authorizationID;
  }

  public ExternalSASLBindRequest(DN authorizationDN)
  {
    Validator.ensureNotNull(authorizationDN);
    this.authorizationID = "dn:" + authorizationDN.toString();
  }

  public String getAuthorizationID() {
    return authorizationID;
  }

  public ExternalSASLBindRequest setAuthorizationID(String authorizationID)
  {
    this.authorizationID = authorizationID;
    return this;
  }

  public ByteString getSASLCredentials()
  {
    return outgoingCredentials;
  }

  public String getSASLMechanism()
  {
    return saslClient.getMechanismName();
  }

  public void dispose() throws SaslException
  {
    saslClient.dispose();
  }

  public boolean evaluateCredentials(ByteString incomingCredentials)
      throws SaslException
  {
    byte[] bytes =
        saslClient.evaluateChallenge(incomingCredentials.toByteArray());
    if(bytes != null)
    {
      this.outgoingCredentials = ByteString.wrap(bytes);
    }
    else
    {
      this.outgoingCredentials = null;
    }

    return isComplete();
  }

  public void initialize(String serverName) throws SaslException
  {
    saslClient = Sasl.createSaslClient(new String[]{SASL_MECHANISM_EXTERNAL},
        authorizationID, SASL_DEFAULT_PROTOCOL, serverName, null, this);

    if(saslClient.hasInitialResponse())
    {
      byte[] bytes = saslClient.evaluateChallenge(new byte[0]);
      if(bytes != null)
      {
        this.outgoingCredentials = ByteString.wrap(bytes);
      }
    }
  }

  public boolean isComplete() {
    return saslClient.isComplete();
  }

  public void toString(StringBuilder buffer) {
    buffer.append("ExternalSASLBindRequest(bindDN=");
    buffer.append(getBindDN());
    buffer.append(", authentication=SASL");
    buffer.append(", saslMechanism=");
    buffer.append(getSASLMechanism());
    buffer.append(", authorizationID=");
    buffer.append(authorizationID);
    buffer.append(", controls=");
    buffer.append(getControls());
    buffer.append(")");
  }
}

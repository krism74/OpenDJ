package org.opends.common.api.raw.request;

import org.opends.server.types.ByteString;
import org.opends.server.types.DirectoryException;
import org.opends.server.util.Validator;
import org.opends.server.core.operations.BindRequest;
import org.opends.server.core.operations.Schema;

/**
 * Created by IntelliJ IDEA.
 * User: digitalperk
 * Date: May 26, 2009
 * Time: 7:46:51 PM
 * To change this template use File | Settings | File Templates.
 */
public final class RawSASLBindRequest extends RawBindRequest
{
  // The SASL credentials.
  private ByteString saslCredentials;

  // The SASL mechanism.
  private String saslMechanism;


  public RawSASLBindRequest(String saslMechanism, ByteString saslCredentials)
  {
    super("");
    Validator.ensureNotNull(saslCredentials, saslMechanism);
    this.saslCredentials = saslCredentials;
    this.saslMechanism = saslMechanism;
  }



  /**
   * Returns the SASL credentials for this bind request.
   *
   * @return The SASL credentials for this bind request, or {@code null}
   *         if there are none or if the bind does not use SASL
   *         authentication.
   */
  public ByteString getSASLCredentials()
  {
    return saslCredentials;
  }



  /**
   * Returns the SASL mechanism for this bind request.
   *
   * @return The SASL mechanism for this bind request, or {@code null}
   *         if there are none or if the bind does not use SASL
   *         authentication.
   */
  public String getSASLMechanism()
  {
    return saslMechanism;
  }



  /**
   * Sets the SASL credentials for this bind request.
   *
   * @param saslCredentials
   *          The SASL credentials for this bind request, or {@code
   *          null} if there are none or if the bind does not use SASL
   *          authentication.
   * @return This raw bind request.
   */
  public RawBindRequest setSASLCredentials(ByteString saslCredentials)
  {
    Validator.ensureNotNull(saslCredentials);
    this.saslCredentials = saslCredentials;
    return this;
  }



  /**
   * Sets The SASL mechanism for this bind request.
   *
   * @param saslMechanism
   *          The SASL mechanism for this bind request, or {@code null}
   *          if there are none or if the bind does not use SASL
   *          authentication.
   * @return This raw bind request.
   */
  public RawBindRequest setSASLMechanism(String saslMechanism)
  {
    Validator.ensureNotNull(saslMechanism);
    this.saslMechanism = saslMechanism;
    return this;
  }



  /**
   * {@inheritDoc}
   */
  public BindRequest toRequest(Schema schema) throws DirectoryException
  {
    // TODO: not yet implemented.
    return null;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public void toString(StringBuilder buffer)
  {
    buffer.append("SASLBindRequest(bindDN=");
    buffer.append(getBindDN());
    buffer.append(", authentication=SASL");
    buffer.append(", saslMechanism=");
    buffer.append(String.valueOf(saslMechanism));
    buffer.append(", saslCredentials=");
    buffer.append(String.valueOf(saslCredentials));
    buffer.append(", controls=");
    buffer.append(getControls());
    buffer.append(")");
  }
}

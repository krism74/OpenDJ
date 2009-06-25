package org.opends.common.api.extended;

import org.opends.server.types.ByteString;
import org.opends.server.util.Validator;
import org.opends.common.api.ResultCode;

/**
 * Created by IntelliJ IDEA.
* User: boli
* Date: Jun 22, 2009
* Time: 6:22:58 PM
* To change this template use File | Settings | File Templates.
*/
public final class GenericExtendedResponse extends
    ExtendedResponse<GenericExtendedOperation>
{
  private ByteString responseValue;

  public GenericExtendedResponse(ResultCode resultCode,
                                 String matchedDN,
                                 String diagnosticMessage)
  {
    super(resultCode, matchedDN, diagnosticMessage);
  }

  public GenericExtendedResponse setResponseName(
      String responseName)
  {
    Validator.ensureNotNull(responseName);
    this.responseName = responseName;
    return this;
  }

  public ByteString getResponseValue()
  {
    return responseValue;
  }

  public GenericExtendedResponse setResponseValue(
      ByteString responseValue)
  {
    Validator.ensureNotNull(responseValue);
    this.responseValue = responseValue;
    return this;
  }

  public GenericExtendedOperation getExtendedOperation() {
    return GenericExtendedOperation.getInstance();
  }

  public void toString(StringBuilder buffer) {
    buffer.append("ExtendedResponse(resultCode=");
    buffer.append(resultCode);
    buffer.append(", matchedDN=");
    buffer.append(matchedDN);
    buffer.append(", diagnosticMessage=");
    buffer.append(diagnosticMessage);
    buffer.append(", referrals=");
    buffer.append(referrals);
    buffer.append(", responseName=");
    buffer.append(responseName);
    buffer.append(", responseValue=");
    buffer.append(responseValue);
    buffer.append(", controls=");
    buffer.append(getControls());
    buffer.append(")");
  }
}

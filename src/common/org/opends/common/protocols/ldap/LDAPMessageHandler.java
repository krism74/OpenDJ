package org.opends.common.protocols.ldap;

import org.opends.common.api.raw.request.*;
import org.opends.common.api.raw.response.*;
import org.opends.common.api.raw.RawUnknownMessage;
import org.opends.server.protocols.asn1.ASN1Exception;
import org.opends.server.types.LDAPException;

import java.io.IOException;

/**
 * Created by IntelliJ IDEA.
 * User: digitalperk
 * Date: May 25, 2009
 * Time: 9:39:07 AM
 * To change this template use File | Settings | File Templates.
 */
public interface LDAPMessageHandler {
  public void handleRequest(int messageID, RawAbandonRequest abandonRequest);

  public void handleRequest(int messageID, RawAddRequest addRequest);

  public void handleResponse(int messageID, RawAddResponse addResponse);

  public void handleRequest(int messageID, int version,
                            RawSimpleBindRequest bindRequest);

  public void handleRequest(int messageID, int version,
                            RawSASLBindRequest bindRequest);

  public void handleRequest(int messageID, int version,
                            RawUnknownBindRequest bindRequest);

  public void handleResponse(int messageID, RawBindResponse bindResponse);

  public void handleRequest(int messageID, RawCompareRequest compareRequest);

  public void handleResponse(int messageID, RawCompareResponse compareResponse);

  public void handleRequest(int messageID, RawDeleteRequest deleteRequest);

  public void handleResponse(int messageID, RawDeleteResponse deleteResponse);

  public void handleRequest(int messageID, RawExtendedRequest extendedRequest);

  public void handleResponse(int messageID,
                             RawExtendedResponse extendedResponse);

  public void handleRequest(int messageID, RawModifyDNRequest modifyDNRequest);

  public void handleResponse(int messageID,
                             RawModifyDNResponse modifyDNResponse);

  public void handleRequest(int messageID, RawModifyRequest modifyRequest);

  public void handleResponse(int messageID, RawModifyResponse modifyResponse);

  public void handleRequest(int messageID, RawSearchRequest searchRequest);

  public void handleResponse(int messageID,
                             RawSearchResultEntry searchResultEntry);

  public void handleResponse(int messageID,
                             RawSearchResultReference searchResultReference);

  public void handleResponse(int messageID,
                             RawSearchResultDone searchResultDone);

  public void handleRequest(int messageID, RawUnbindRequest unbindRequest);

  public void handleResponse(int messageID,
                             RawIntermediateResponse intermediateResponse);

  public void handleMessage(int messageID, RawUnknownMessage unknownMessage);

  public void handleException(Throwable throwable);

  public void handleClose();
}

<jsp:root version="2.0" xmlns:f="http://java.sun.com/jsf/core" xmlns:h="http://java.sun.com/jsf/html" xmlns:jsp="http://java.sun.com/JSP/Page" xmlns:webuijsf="http://www.sun.com/webui/webuijsf">
    <jsp:directive.page contentType="text/html"/>
    <f:view>
        <f:loadBundle basename="org.opends.ui.web.labels" var="labels"/>
        <webuijsf:page>
            <webuijsf:html id="html">
                <webuijsf:head id="blah" title="#{labels.TITLE_WINDOW}" />
                    
                <webuijsf:body id="body">
                    <webuijsf:form id="form1">
                        <webuijsf:alert type="#{LoginHandler.errorType}"
                        summary="#{LoginHandler.errorSummary}"
                        detail="#{LoginHandler.errorDetail}"/>
                        <table>
                            <tr>
                                <td>
                                    <webuijsf:staticText text="User Name:" />
                                </td>
                                <td>
                                    <webuijsf:textField text="#{LoginHandler.userName}"/>
                                </td>
                            </tr>
                            <tr>
                                <td>
                                    <webuijsf:staticText text="Password:" />
                                </td>
                                <td>
                                    <webuijsf:passwordField password="#{LoginHandler.password}" />
                                </td>
                            </tr>
                            <tr>
                                <td></td>
                                <td>
                                    <webuijsf:button text="Log In" actionExpression="#{LoginHandler.login}"/>
                                </td>
                            </tr>
                        </table>
                    </webuijsf:form>
                </webuijsf:body>
            </webuijsf:html>
        </webuijsf:page>
    </f:view>
</jsp:root>
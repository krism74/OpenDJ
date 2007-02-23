
<jsp:root version="2.0" xmlns:f="http://java.sun.com/jsf/core" xmlns:h="http://java.sun.com/jsf/html" xmlns:jsp="http://java.sun.com/JSP/Page" xmlns:webuijsf="http://www.sun.com/webui/webuijsf">
    <jsp:directive.page contentType="text/html"/>
    <f:view>
        <webuijsf:page>
            <webuijsf:html>
                <webuijsf:head id="head" title="return to previous page" />
                <webuijsf:body id="body">
                    <webuijsf:form id="form2">
                        <!-- Masthead with Attributes -->
                        <webuijsf:masthead id="masthead" serverInfo="localhost" userInfo="#{LoginHandler.userName}" 
                                           productImageURL="/images/example_primary_masthead.png" productImageDescription="#{msgs.mastheadAltText}"/>
                        
                        <!-- Breadcrumbs -->       
                        <webuijsf:breadcrumbs id="breadcrumbs">
                            <webuijsf:hyperlink id="hyplink1" actionExpression="#{IndexBean.showIndex}" text="#{msgs.exampleTitle}"
                                                toolTip="#{msgs.index_title}" immediate="true"
                                                onMouseOver="javascript:window.status='#{msgs.index_breadcrumbMouseOver}'; return true"
                                                onMouseOut="javascript:window.status=''; return true" />
                            <webuijsf:hyperlink id="hyplink2" text="#{msgs.masthead_title}"/>
                        </webuijsf:breadcrumbs>
                        
                        <!-- Page Title -->
                        <webuijsf:contentPageTitle id="pagetitle" title="#{msgs.masthead_title}" />
                        
                        <!-- Masthead with Attributes Page Hyperlink -->
                        <webuijsf:markup tag="div" styleClass="#{themeStyles.CONTENT_MARGIN}"> 
                            <br/>                         
                            Go back to the log in page:
                            <webuijsf:hyperlink  id="hyperlinktest1"
                                                 text="Log Out"
                                                 actionExpression="logout" />        
                        </webuijsf:markup>
                    </webuijsf:form>
                </webuijsf:body> 
            </webuijsf:html>
        </webuijsf:page>
    </f:view>
</jsp:root>
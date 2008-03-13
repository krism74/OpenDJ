/* SiteCatalyst code version: H.14.
Copyright 1997-2008 Omniture, Inc. More info available at http://www.omniture.com */
/************************** CONFIG SECTION ****************************************/
/* Specify the Report Suite(s) */
var s_account="sunopendsdev";
var sun_dynamicAccountSelection=false;
var sun_dynamicAccountList="sunglobal,sunopends=www.opends.org;sunopendsdev=.";	
/* Specify the Report Suite ID */
var s_siteid="opends:";
/* Settings for pageName */
/* Remote Omniture JS call  */
var sun_ssl=(window.location.protocol.toLowerCase().indexOf("https")!=-1);
	if(sun_ssl == true) { var fullURL = "https://www.sun.com/share/metrics/metrics_group1.js"; }
		else { var fullURL= "http://www-cdn.sun.com/share/metrics/metrics_group1.js"; }
document.write("<sc" + "ript language=\"JavaScript\" src=\""+fullURL+"\"></sc" + "ript>");
/************************** END CONFIG SECTION **************************************/
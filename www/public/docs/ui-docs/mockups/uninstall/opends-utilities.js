    // Impede Seleção
    document.onselectstart = function() { return false; }
    
    // Menu Lateral
    if (document.getElementById){
    document.write('<style type="text/css">\n')
    document.write('.options{display: none;}\n')
    document.write('</style>\n')
    }
 function TrocaMenu(obj){
    	if(document.getElementById){
    	var el = document.getElementById(obj);
  //          alert(el.parentNode.parentNode.id);
            deSelectAllCats(el.parentNode.parentNode.id);
            document.getElementById(obj+'img').src='tree_handledowntopns.png';
			document.getElementById(obj+'lnk').className='cpmenulabelsel';
    		if(el.style.display != "block"){
    			el.style.display = "block";
			}else{
    			el.style.display = "none";
				document.getElementById(obj+'img').src='tree_handlerighttopns.png';
    		}
    	}
    }
	
function closeSubWin(){
  document.getElementById('content-inner').style.display='none';
  document.getElementById(clicked).focus();
}

function closeSubWin2(){
  document.getElementById('content-inner2').style.display='none';
  document.getElementById(clicked).focus();
}

 function showhide(obj, arg){
    	if(document.getElementById){
    	var el = document.getElementById(obj);
    	var ar = el.getElementsByTagName("div");
     for (var i=0; i<ar.length; i++){
    	if (ar[i].className=="options") {
   	       if(arg == 'hide'){
			 ar[i].style.display = "none";
    		 } else {
			 ar[i].style.display = "block";
			 }
		}
	 }
   	}
   }

 function deSelectAllCats(parent){
   	if(document.getElementById){
 //  	var nav = document.getElementById("nav");
   	var ar = document.getElementById(parent).getElementsByTagName("a");
     for (var i=0; i<ar.length; i++){
    	if (ar[i].className=="cpmenulabelsel") {
 			 ar[i].className="cpmenulabel";
			 return;
			 }
	 }
   	}
   }


function toggleLegend (arg) {
 switch (arg) {
 case 'show':
  document.getElementById('topo-legend').style.display='block'; 
  document.getElementById('hide-legend').style.display='block'; 
  document.getElementById('show-legend').style.display='none';
 break;
 case 'hide':
  document.getElementById('topo-legend').style.display='none'; 
  document.getElementById('hide-legend').style.display='none'; 
  document.getElementById('show-legend').style.display='block';
 break;
 }
}


function handleSelection (obj) {
 if(obj){
  deSelectAll();
  obj.parentNode.parentNode.parentNode.style.display ="block";
  obj.parentNode.className = 'optionlabelsel';
  obj.className = 'AdnSelLnk_sun4';
  leftsel = obj.text;
  leftsellnk = obj.href;
 }
}

 function deSelectAll(){
   	if(document.getElementById){
 //  	var nav = document.getElementById("nav");
   	var ar = document.getElementById("nav").getElementsByTagName("div");
     for (var i=0; i<ar.length; i++){
    	if (ar[i].className=="optionlabelsel") {
 			 ar[i].className="optionlabel";
			 ar[i].firstChild.className="AdnLnk_sun4";
			 return;
			 }
	 }
   	}
   }

function makeBcm (make2) {
 bcmDiv=document.getElementById('bcm');
 var issvc = leftsel.substr(2, 3) == "dc=";
 var svrbcm ="";
 if (leftsel) { 
   if ((make2 == 'mod') && issvc) {
     var svr = clicked.substr(0, clicked.indexOf("-"));
     svrbcmlnk = "javascript:loadFile('serverhome.html','"+svr+"');";
     svrbcm = "<a href="+svrbcmlnk+" class='BcmLnk_sun4'>"+svr+"</a><span class='BcmSep_sun4'>&gt;</span>"
   } else {
	if ((make2 == 'svr') && !issvc) {
		handleSelection(document.getElementById('server-example-allsrvs.html'));
	}
   }
   bcmDiv.innerHTML="<div class='BcmGryDiv_sun4'><a href="+leftsellnk+";clearBcm(); class='BcmLnk_sun4'>"+leftsel+"</a><span class='BcmSep_sun4'>&gt; </span>"+svrbcm+"</div>"
 }
}

function clearBcm () {
  document.getElementById('bcm').innerHTML="";
}


function loadFile2(url, clickedThing) {
  target = 'content-inner2';
 document.getElementById(target).style.display='block';
 if (clickedThing) {clicked = clickedThing;}
  document.getElementById(target).innerHTML = ' ';
  if (window.XMLHttpRequest) {
    req = new XMLHttpRequest();
  } else if (window.ActiveXObject) {
    req = new ActiveXObject("Microsoft.XMLHTTP");
  }
  if (req != undefined) {
    req.onreadystatechange = function() {loadFileDone(url, target);};
    req.open("GET", url, true);
    req.send("");
	  }
}  


function loadFile(url, clickedThing) {
  target = 'content-inner';
 document.getElementById(target).style.display='block';
 if (clickedThing) {clicked = clickedThing;}
  document.getElementById(target).innerHTML = ' ';
  if (window.XMLHttpRequest) {
    req = new XMLHttpRequest();
  } else if (window.ActiveXObject) {
    req = new ActiveXObject("Microsoft.XMLHTTP");
  }
  if (req != undefined) {
    req.onreadystatechange = function() {loadFileDone(url, target);};
    req.open("GET", url, true);
    req.send("");
	  }
}  

function loadFileDone(url, target) {
  targObj = document.getElementById(target);
  if (req.readyState == 4) { // only if req is "loaded"
    if (req.status == 200) { // only if "OK"
      targObj.innerHTML = req.responseText;
      execJS(targObj);
      targObj.scrollTop=0;
	  lastfile = url;
	  if (document.getElementById(url)) {handleSelection(document.getElementById(url))}
	} else {
 //     document.getElementById(target).innerHTML="Error:\n"+ req.status + "\n" +req.statusText;
       targObj.innerHTML="<p></br>&nbsp;&nbsp;&nbsp;&nbsp;Page not found, most likely because it hasn't been designed yet. </br ></br>&nbsp;&nbsp;&nbsp;&nbsp;<a href=javascript:loadFile('"+lastfile+"');> &lt;&lt; Back to Previous Page</a></p> ";
 }
  }
}

var bSaf = (navigator.userAgent.indexOf('Safari') != -1);
var bOpera = (navigator.userAgent.indexOf('Opera') != -1);
var bMoz = (navigator.appName == 'Netscape');
function execJS(node) {
  var st = node.getElementsByTagName('SCRIPT');
  var strExec;
  for(var i=0;i<st.length; i++) {     
    if (bSaf) {
      strExec = st[i].innerHTML;
    }
    else if (bOpera) {
      strExec = st[i].text;
    }
    else if (bMoz) {
      strExec = st[i].textContent;
    }
    else {
      strExec = st[i].text;
    }
    try {
      eval(strExec.split("<!--").join("").split("-->").join(""));
    } catch(e) {
      alert(e);
    }
  }
}
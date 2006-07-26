// kill "description" h3 bar
ld = document.getElementById("longdescription");
for( n=ld.firstChild; n!=null; n=n.nextSibling ) {
  if(n.nodeType==1 && n.innerHTML=="Description" ) {
    n.parentNode.removeChild(n);
    break;
  }
}

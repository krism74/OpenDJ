function makeProgress() {
window.setTimeout('progUpd1()',2000);
}

function progUpd1() {
document.getElementById('progimg').src='prog2.png';
document.getElementById('progtask').innerHTML='The next thing I am doing...';
document.getElementById('progtime').innerHTML='30% complete (About 7 Seconds Remaining).';
document.getElementById('progdetails').innerHTML='Info and output...<strong>Done.</strong><br\>More info and output...';
window.setTimeout('progUpd2()',2000);
}

function progUpd2() {
if (canceled==false) {
document.getElementById('progimg').src='prog3.png';
document.getElementById('progtask').innerHTML='The very next thing I am doing...';
document.getElementById('progtime').innerHTML='75% complete (About 4 Seconds Remaining).';
document.getElementById('progdetails').innerHTML='Info and output...<strong>Done.</strong><br\>More info and output...<strong>Done.</strong><br\>Even more info and output...<strong>Done.</strong><br\>Bonus info and output...';
window.setTimeout('progUpd3()',2000);
  } else { progUpd4();
 }
}

function progUpd3() {
if (canceled==false) {
document.getElementById('progimg').src='prog4.png';
document.getElementById('progtask').innerHTML='Pretty much the last thing I am doing...';
document.getElementById('progtime').innerHTML='99% complete (About 1 Second Remaining).';
document.getElementById('progdetails').innerHTML='Info and output...<strong>Done.</strong><br\>More info and output...<strong>Done.</strong><br\>Even more info and output...<strong>Done.</strong><br\>Bonus info and output...<strong>Done.</strong><br\>Buy one get one free info and output...<strong>Done.</strong><br\>Final bit of info and output..';
window.setTimeout('progUpd4()',2000);
  } else { progUpd4();
 }
}

function progUpd4() {
document.getElementById('windowbutton').disabled=false;
document.getElementById('windowbutton').className='Btn1_sun4';
document.getElementById('windowbutton').focus();
document.getElementById('progbarstuff').style.display='none';
if (canceled==true) {document.getElementById('canceledalert').style.display='block';} else { document.getElementById('finalalert').style.display='block';}
canceled=false;
document.getElementById('progdetails').innerHTML='Info and output...<strong>Done.</strong><br\>More info and output...<strong>Done.</strong><br\>Even more info and output...<strong>Done.</strong><br\>Bonus info and output...<strong>Done.</strong><br\>Buy one get one free info and output...<strong>Done.</strong><br\>Final bit of info and output...<strong>Done.</strong>';
}

function applyFilter() {
document.getElementById('brstatusarea').style.display='none';
document.getElementById('browsetree').style.display='none';
document.getElementById('browsetree_filtered').style.display='none';
document.getElementById('progressarea').style.display='block';
deselectEntry();
window.setTimeout('loadTree()',3000);
}

function loadTree() {
document.getElementById('progressarea').style.display='none';
document.getElementById('browsetree_filtered').style.display='block';
document.getElementById('brstatusarea').innerHTML='17 Entries Found';
document.getElementById('brstatusarea').style.display='block';
}

function selectEntry() {
document.getElementById('selectedrow').style.backgroundColor='#4b6983';
document.getElementById('selectedrow_filtered').style.backgroundColor='#4b6983';
document.getElementById('selectedrow').style.color='#ffffff';
document.getElementById('selectedrow_filtered').style.color='#ffffff';
document.getElementById('noneselected').style.display='none';
document.getElementById('entrydn').style.display='inline';
document.getElementById('brsimpleview').style.display='block';
document.getElementById('brldifview').style.display='none';
document.getElementById('entryldif').disabled=false;
document.getElementById('entryldif').className='TxtAra_sun4';
document.getElementById('entryldif').innerHTML='cn: Barbara Jensen\ncn: Babs Jensen\nsn: Jensen\ngivenname: Barbara\nobjectclass: top\nobjectclass: person\nobjectclass: organizationalPerson\nobjectclass: inetOrgPerson\nou: Product Development\nou: People\nl: Cupertino\nuid: bjensen\nmail: bjensen@example.com\ntelephonenumber: +1 408 555 1862\nfacsimiletelephonenumber: +1 408 555 1992\nroomnumber: 0209';
//document.getElementById('newEntry').className='Btn2_sun4';document.getElementById('newEntry').disabled=false;
//document.getElementById('deleteEntry').className='Btn2_sun4';document.getElementById('deleteEntry').disabled=false;
document.getElementById('saveEntry').className='Btn2_sun4';document.getElementById('saveEntry').disabled=false;
}


function selectIndexEntry() {
deselectIndexes();
document.getElementById('selectedrow').style.backgroundColor='#4b6983';
document.getElementById('selectedrow').style.color='#ffffff';
document.getElementById('ibindexview').style.display='block';
document.getElementById('ibentryactions').style.display='block';
}

function selectIndexList() {
deselectIndexes();
document.getElementById('indexcontainerrow').style.backgroundColor='#4b6983';
document.getElementById('indexcontainerrow').style.color='#ffffff';
document.getElementById('ibindexlist').style.display='block';
document.getElementById('ibentryactions').style.display='none';
}


function selectVLVIndexEntry() {
deselectIndexes();
document.getElementById('vlvselectedrow').style.backgroundColor='#4b6983';
document.getElementById('vlvselectedrow').style.color='#ffffff';
document.getElementById('ibvlvindexview').style.display='block';
document.getElementById('ibentryactions').style.display='block';
}

function selectVLVIndexList() {
deselectIndexes();
document.getElementById('vlvindexcontainerrow').style.backgroundColor='#4b6983';
document.getElementById('vlvindexcontainerrow').style.color='#ffffff';
document.getElementById('ibvlvindexlist').style.display='block';
document.getElementById('ibentryactions').style.display='none';
}


function deselectIndexes() {
document.getElementById('selectedrow').style.backgroundColor='#ffffff';
document.getElementById('selectedrow').style.color='#000000';
document.getElementById('vlvselectedrow').style.backgroundColor='#ffffff';
document.getElementById('vlvselectedrow').style.color='#000000';
document.getElementById('indexcontainerrow').style.backgroundColor='#ffffff';
document.getElementById('indexcontainerrow').style.color='#000000';
document.getElementById('vlvindexcontainerrow').style.backgroundColor='#ffffff';
document.getElementById('vlvindexcontainerrow').style.color='#000000';
document.getElementById('ibvlvindexview').style.display='none';
document.getElementById('ibindexview').style.display='none';
document.getElementById('ibindexlist').style.display='none';
document.getElementById('ibvlvindexlist').style.display='none';
}


function deselectEntry() {
document.getElementById('selectedrow').style.backgroundColor='#ffffff';
document.getElementById('selectedrow_filtered').style.backgroundColor='#ffffff';
document.getElementById('selectedrow').style.color='#000000';
document.getElementById('selectedrow_filtered').style.color='#000000';
document.getElementById('noneselected').style.display='inline';
document.getElementById('entrydn').style.display='none';
document.getElementById('brsimpleview').style.display='none';
document.getElementById('brattrview').style.display='none';
document.getElementById('brldifview').style.display='block';
document.getElementById('entryldif').disabled=true;
document.getElementById('entryldif').className='TxtAraDis_sun4';
document.getElementById('entryldif').innerHTML=' ';
//document.getElementById('newEntry').className='Btn2Dis_sun4';document.getElementById('newEntry').disabled=true;
//document.getElementById('deleteEntry').className='Btn2Dis_sun4';document.getElementById('deleteEntry').disabled=true;
document.getElementById('saveEntry').className='Btn2Dis_sun4';document.getElementById('saveEntry').disabled=true;
}

function handleRB(num) {
 switch(num) {
 case 1:
  if (document.theform.availnodes.disabled=true) {
  document.theform.availnodes.disabled=false;
  document.theform.availnodes.className='Lst_sun4';
  document.theform.selnodes.disabled=false;
  document.theform.selnodes.className='Lst_sun4';
  document.theform.selnodes.options[0].selected=true;
  handleButtonsSel(document.theform, document.theform.availnodes);
  document.getElementById('cleanindex').disabled=true;
  document.getElementById('cleanindex').className='MnuStdDis_sun4';
  }
  break;
 case 2:
  if (document.getElementById('cleanindex').disabled=true) {
  document.theform.availnodes.disabled=true;
  document.theform.availnodes.className='LstDis_sun4';
  document.theform.selnodes.disabled=true;
  document.theform.selnodes.className='LstDis_sun4';
  deselectAll(document.theform.availnodes);
  deselectAll(document.theform.selnodes);
  document.theform.add.disabled=true;
  document.theform.add.className='Btn2Dis_sun4';
  document.theform.remove.disabled=true;
  document.theform.remove.className='Btn2Dis_sun4';
  document.getElementById('cleanindex').disabled=false;
  document.getElementById('cleanindex').className='MnuStd_sun4';
  }
  break;
  }
}

function handleUpdateCB(code) {
 switch(code) {
 case 'ac_man':
 document.getElementById('accessRefresh').className='Btn2_sun4';
 document.getElementById('accessRefresh').disabled=false;
 break;
 case 'ac_live':
 document.getElementById('accessRefresh').className='Btn2Dis_sun4';
 document.getElementById('accessRefresh').disabled=true;
 break;
 case 'er_man':
 document.getElementById('errorRefresh').className='Btn2_sun4';
 document.getElementById('errorRefresh').disabled=false;
 break;
 case 'er_live':
 document.getElementById('errorRefresh').className='Btn2Dis_sun4';
 document.getElementById('errorRefresh').disabled=true;
 break;
 case 'rp_man':
 document.getElementById('replRefresh').className='Btn2_sun4';
 document.getElementById('replRefresh').disabled=false;
 break;
 case 'rp_live':
 document.getElementById('replRefresh').className='Btn2Dis_sun4';
 document.getElementById('replRefresh').disabled=true;
 break;
 }
}

function handleGroupRB(num) {
 switch(num) {
 case 1:
 document.getElementById('staticdns').className='TxtAra_sun4';
 document.getElementById('staticdns').disabled=false;
 document.getElementById('dynamicurl').className='TxtFldDis_sun4';
 document.getElementById('dynamicurl').disabled=true;
 document.getElementById('virtualreference').className='MnuStdDis_sun4';
 document.getElementById('virtualreference').disabled=true;
 break;
 case 2:
 document.getElementById('staticdns').className='TxtAraDis_sun4';
 document.getElementById('staticdns').disabled=true;
 document.getElementById('dynamicurl').className='TxtFld_sun4';
 document.getElementById('dynamicurl').disabled=false;
 document.getElementById('virtualreference').className='MnuStdDis_sun4';
 document.getElementById('virtualreference').disabled=true;
 break;
 case 3:
 document.getElementById('staticdns').className='TxtAraDis_sun4';
 document.getElementById('staticdns').disabled=true;
 document.getElementById('dynamicurl').className='TxtFldDis_sun4';
 document.getElementById('dynamicurl').disabled=true;
 document.getElementById('virtualreference').className='MnuStd_sun4';
 document.getElementById('virtualreference').disabled=false;
 break;
 }
}


function applySchemaFilter() {
document.getElementById('browsetree').style.display='none';
document.getElementById('progressarea').style.display='block';
document.getElementById('editableclass').style.display='none';
document.getElementById('panelbuttons').style.display='none';
document.getElementById('readonlyclass').style.display='none';
window.setTimeout('loadSchemaFilter()',3000);
}

function loadSchemaFilter() {
document.getElementById('progressarea').style.display='none';
document.getElementById('browsetree').style.display='block';
selectEditableClass();
}

function selectStandardClass() {
document.getElementById('selectedStandardClass').style.backgroundColor='#4b6983';
document.getElementById('selectedStandardClass').style.color='#ffffff';
document.getElementById('selectedEditableClass').style.backgroundColor='#ffffff';
document.getElementById('selectedEditableClass').style.color='#000000';
document.getElementById('selectedStandardAttribute').style.backgroundColor='#ffffff';
document.getElementById('selectedStandardAttribute').style.color='#000000';
document.getElementById('selectedEditableAttribute').style.backgroundColor='#ffffff';
document.getElementById('selectedEditableAttribute').style.color='#000000';
document.getElementById('panelbuttons').style.display='none';
document.getElementById('editableattribute').style.display='none';
document.getElementById('editableclass').style.display='none';
document.getElementById('readonlyattribute').style.display='none';
document.getElementById('readonlyclass').style.display='block';
}

function selectEditableClass() {
document.getElementById('selectedStandardClass').style.backgroundColor='#ffffff';
document.getElementById('selectedStandardClass').style.color='#000000';
document.getElementById('selectedEditableClass').style.backgroundColor='#4b6983';
document.getElementById('selectedEditableClass').style.color='#ffffff';
document.getElementById('selectedStandardAttribute').style.backgroundColor='#ffffff';
document.getElementById('selectedStandardAttribute').style.color='#000000';
document.getElementById('selectedEditableAttribute').style.backgroundColor='#ffffff';
document.getElementById('selectedEditableAttribute').style.color='#000000';
document.getElementById('readonlyclass').style.display='none';
document.getElementById('editableattribute').style.display='none';
document.getElementById('readonlyattribute').style.display='none';
document.getElementById('panelbuttons').style.display='block';
document.getElementById('editableclass').style.display='block';
}

function selectStandardAttribute() {
document.getElementById('selectedStandardClass').style.backgroundColor='#ffffff';
document.getElementById('selectedStandardClass').style.color='#000000';
document.getElementById('selectedEditableClass').style.backgroundColor='#ffffff';
document.getElementById('selectedEditableClass').style.color='#000000';
document.getElementById('selectedStandardAttribute').style.backgroundColor='#4b6983';
document.getElementById('selectedStandardAttribute').style.color='#ffffff';
document.getElementById('selectedEditableAttribute').style.backgroundColor='#ffffff';
document.getElementById('selectedEditableAttribute').style.color='#000000';
document.getElementById('panelbuttons').style.display='none';
document.getElementById('editableattribute').style.display='none';
document.getElementById('editableclass').style.display='none';
document.getElementById('readonlyattribute').style.display='block';
document.getElementById('readonlyclass').style.display='none';
}

function selectEditableAttribute() {
document.getElementById('selectedStandardClass').style.backgroundColor='#ffffff';
document.getElementById('selectedStandardClass').style.color='#000000';
document.getElementById('selectedEditableClass').style.backgroundColor='#ffffff';
document.getElementById('selectedEditableClass').style.color='#000000';
document.getElementById('selectedStandardAttribute').style.backgroundColor='#ffffff';
document.getElementById('selectedStandardAttribute').style.color='#000000';
document.getElementById('selectedEditableAttribute').style.backgroundColor='#4b6983';
document.getElementById('selectedEditableAttribute').style.color='#ffffff';
document.getElementById('readonlyclass').style.display='none';
document.getElementById('editableattribute').style.display='block';
document.getElementById('readonlyattribute').style.display='none';
document.getElementById('panelbuttons').style.display='block';
document.getElementById('editableclass').style.display='none';
}



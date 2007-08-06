// brian.ehret@sun.com Last Updated: 10/08/2002
// This is code to be used for add and remove idiom
// To remedy a usability issue with Netscape 4 on Solaris, this code needs to be
//   called in conjunction with browserVersion.js browser sniffing code.
// The last item in lists is a series of underscores to keep the width of the 
//   list box constant -- this item needs to have a value of "ignoreMe"

function handleButtonsAvail(form, box) {
	changeState (form.add, 1, 'Btn2_sun4');
	changeState (form.addall, 1, 'Btn2_sun4');
	changeState (form.remove, 0, 'Btn2Dis_sun4');
	changeState (form.moveup, 0, 'Btn2Dis_sun4');
	changeState (form.movedown, 0, 'Btn2Dis_sun4');
	if (box.length == 1) { 
		changeState (form.removeall, 0, 'Btn2Dis_sun4');
		} else {
		changeState (form.removeall, 1, 'Btn2_sun4');
    }
}

function handleButtonsSel(form, box) {
	changeState (form.add, 0, 'Btn2Dis_sun4');
	changeState (form.remove, 1, 'Btn2_sun4');
	changeState (form.removeall, 1, 'Btn2_sun4');
	changeState (form.moveup, 1, 'Btn2_sun4');
	changeState (form.movedown, 1, 'Btn2_sun4');
	if (box.length == 1) { 
		changeState (form.addall, 0, 'Btn2Dis_sun4');
		} else {
		changeState (form.addall, 1, 'Btn2_sun4');
    }
}


function changeState (element, state, classname) {
if (element) {
	if (state == 0) {
		 element.disabled = true;
		} else {
		 element.disabled = false;
		}
 	   element.className = classname;
   } 
} 	 


function shiftBetween(fbox,tbox,allp) { //shifts from one box to another
	if (fbox.length > 1 && (fbox.selectedIndex != -1 | allp == 1))  {
		deselectAll(tbox);
		sbv = tbox.options[tbox.length-1].value; 
		sbt = tbox.options[tbox.length-1].text;
		i=0; 
	    do {
			if (fbox.options[i].selected | allp == 1) {
				iotext = fbox.options[i].text;
				iovalue = fbox.options[i].value;
				didx = tbox.length;
				fbox.options[i] = null;
				tbox.options[didx] = new Option (sbt, sbv, false, false);
				tbox.options[didx-1] = new Option (iotext, iovalue, false, true);
				tbox.options[didx].selected = false;    // ns4 bug
				tbox.options[didx-1].selected = true;   // ns6 bug
			} else {
				if (allp == 0) { i++ };
			}
       tbox.options[tbox.length-1].disabled = true;   // disable spacer in to box
       fbox.options[fbox.length-1].disabled = true;   // disable spacer in from box	   
		} 
		while (fbox.options[i].value != "ignoreMe");
			//if (is_nav4 == 1 && is_sun == 1) {  // ns4sol usability issue with multi-select boxes
	 //    	deselectAll(tbox);               // only select the last-moved item
			//tbox.options[tbox.length -2].selected = true;
			//}
		fbox.options[0].selected = false; // deselects spaceer in from box
	} else {
		alert("There are no selected items to be moved.");
	}  
}


function deselectAll(box)  { // deselects all items in list
	for(i=0; i<box.options.length; i++) {
		box.options[i].selected = false;
	}
}


function shiftWithin(box, down)  { // shifts position of item within list
	ind = box.selectedIndex;
	if (ind != -1 && box.options[ind].value != "ignoreMe") {
		iotext = box.options[ind].text;
		iovalue = box.options[ind].value;
		if (ind > 0 && down == 0) {
			deselectAll(box);
			box.options[ind].text = box.options[ind-1].text;
			box.options[ind].value = box.options[ind-1].value;
			box.options[ind-1].text = iotext;
			box.options[ind-1].value = iovalue;
			box.options[ind-1].selected =true;
		} else if (ind < box.length-1 && box.options[ind+1].value != "ignoreMe" && down == 1) {
			deselectAll(box);
			box.options[ind].text = box.options[ind+1].text;
			box.options[ind].value = box.options[ind+1].value;
			box.options[ind+1].text = iotext;
			box.options[ind+1].value = iovalue;
			box.options[ind+1].selected =true;
		}
	} else {
		alert("You must first select an item to be moved.");
	}
}

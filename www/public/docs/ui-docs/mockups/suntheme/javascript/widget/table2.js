//<!--
// The contents of this file are subject to the terms
// of the Common Development and Distribution License
// (the License).  You may not use this file except in
// compliance with the License.
// 
// You can obtain a copy of the license at
// https://woodstock.dev.java.net/public/CDDLv1.0.html.
// See the License for the specific language governing
// permissions and limitations under the License.
// 
// When distributing Covered Code, include this CDDL
// Header Notice in each file and include the License file
// at https://woodstock.dev.java.net/public/CDDLv1.0.html.
// If applicable, add the following below the CDDL Header,
// with the fields enclosed by brackets [] replaced by
// you own identifying information:
// "Portions Copyrighted [year] [name of copyright owner]"
// 
// Copyright 2007 Sun Microsystems, Inc. All rights reserved.
//

dojo.provide("webui.suntheme.widget.table2");

dojo.require("dojo.widget.*");
dojo.require("webui.suntheme.*");
dojo.require("webui.suntheme.widget.*");

/**
 * This function will be invoked when creating a Dojo widget. Please see
 * webui.suntheme.widget.table2.setProps for a list of supported
 * properties.
 *
 * Note: This is considered a private API, do not use.
 */
webui.suntheme.widget.table2 = function() {
    // Set defaults.
    this.widgetType = "table2";

    // Register widget.
    dojo.widget.Widget.call(this);

    /**
     * This function is used to generate a template based widget.
     */
    this.fillInTemplate = function() {
        // Set ids.
        if (this.id) {
            this.actionsContainer.id = this.id + "_actionsContainer";
            this.filterPanelContainer.id = this.id + "_filterPanelContainer";
            this.marginContainer.id = this.id + "_marginContainer";
            this.preferencesPanelContainer.id = this.id + "_preferencesPanelContainer";
            this.sortPanelContainer.id = this.id + "_sortPanelContainer";
            this.rowGroupsContainer.id = this.id + "_rowGroupsContainer";
            this.titleContainer.id = this.id + "_titleContainer";
            this.tableFooterContainer.id = this.id + "_tableFooterContainer";
        }

        // Set public functions.
        this.domNode.getProps = function() { return dojo.widget.byId(this.id).getProps(); }
        this.domNode.refresh = function(execute) { return dojo.widget.byId(this.id).refresh(execute); }
        this.domNode.setProps = function(props) { return dojo.widget.byId(this.id).setProps(props); }

        // Set private functions.
        this.setProps = webui.suntheme.widget.table2.setProps;
        this.refresh = webui.suntheme.widget.table2.refresh.processEvent;
        this.getProps = webui.suntheme.widget.table2.getProps;

        // Initialize properties.
        return webui.suntheme.widget.common.initProps(this);
    }
}

/**
 * This function is used to get widget properties. Please see
 * webui.suntheme.widget.table2.setProps for a list of supported
 * properties.
 */
webui.suntheme.widget.table2.getProps = function() {
    var props = {};

    // Set properties.
    if (this.actions) { props.actions = this.actions; }
    if (this.filterText) { props.filterText = this.filterText; }
    if (this.rowGroups) { props.rowGroups = this.rowGroups; }
    if (this.width) { props.width = this.width; }

    // Add DOM node properties.
    Object.extend(props, webui.suntheme.widget.common.getCommonProps(this));
    Object.extend(props, webui.suntheme.widget.common.getCoreProps(this));
    Object.extend(props, webui.suntheme.widget.common.getJavaScriptProps(this));

    return props;
}

/**
 * This closure is used to process refresh events.
 */
webui.suntheme.widget.table2.refresh = {
    /**
     * Event topics for custom AJAX implementations to listen for.
     */
    beginEventTopic: "webui_suntheme_widget_table2_refresh_begin",
    endEventTopic: "webui_suntheme_widget_table2_refresh_end",
 
    /**
     * Process refresh event.
     *
     * @param execute Comma separated string containing a list of client ids 
     * against which the execute portion of the request processing lifecycle
     * must be run.
     */
    processEvent: function(execute) {
        // Publish event.
        webui.suntheme.widget.table2.refresh.publishBeginEvent({
            id: this.id,
            execute: execute
        });
        return true;
    },

    /**
     * Publish an event for custom AJAX implementations to listen for.
     *
     * @param props Key-Value pairs of properties of the widget.
     */
    publishBeginEvent: function(props) {
        dojo.event.topic.publish(webui.suntheme.widget.table2.refresh.beginEventTopic, props);
        return true;
    },

    /**
     * Publish an event for custom AJAX implementations to listen for.
     *
     * @param props Key-Value pairs of properties of the widget.
     */
    publishEndEvent: function(props) {
        dojo.event.topic.publish(webui.suntheme.widget.table2.refresh.endEventTopic, props);
        return true;
    }
}

/**
 * This function is used to set widget properties with the
 * following Object literals.
 *
 * <ul>
 *  <li>actions</li>
 *  <li>filterText</li>
 *  <li>id</li>
 *  <li>rowGroups</li>
 *  <li>title</li>
 *  <li>width</li>
 * </ul>
 *
 * @param props Key-Value pairs of properties.
 */
webui.suntheme.widget.table2.setProps = function(props) {
    // Save properties for later updates.
    if (props != null) {
        webui.suntheme.widget.common.extend(this, props);
    } else {
        props = this.getProps(); // Widget is being initialized.
    }

    // Set DOM node properties.
    webui.suntheme.widget.common.setCoreProps(this.domNode, props);
    webui.suntheme.widget.common.setCommonProps(this.domNode, props);
    webui.suntheme.widget.common.setJavaScriptProps(this.domNode, props);

    // Set container width.
    if (props.width) {
        this.domNode.style.width = this.width;
    }

    // Add title.
    if (props.title) {
        webui.suntheme.widget.common.addFragment(this.titleContainer, props.title);
        webui.suntheme.common.setVisibleElement(this.titleContainer, true);
    }

    // Add actions.
    if (props.actions) {
        webui.suntheme.widget.common.addFragment(this.actionsContainer, props.actions);
        webui.suntheme.common.setVisibleElement(this.actionsContainer, true);
    }

    // Add row groups.
    if (props.rowGroups) {
        this.rowGroupsContainer.innerHTML = ""; // Cannot be null for IE.
        for (var i = 0; i < props.rowGroups.length; i++) {
            // Each group must be added to separate containers for padding.
            var rowGroupsClone = this.rowGroupsContainer;

            // Clone nodes.
            if (i + 1 < props.rowGroups.length) {
                rowGroupsClone = this.rowGroupsContainer.cloneNode(true);
                this.marginContainer.insertBefore(rowGroupsClone, this.rowGroupsContainer);
            }
            webui.suntheme.widget.common.addFragment(rowGroupsClone, props.rowGroups[i], "last");
        }
    }
    return true;
}

dojo.inherits(webui.suntheme.widget.table2, dojo.widget.HtmlWidget);

//-->

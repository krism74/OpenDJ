/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE
 * or https://OpenDS.dev.java.net/OpenDS.LICENSE.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE.  If applicable,
 * add the following below this CDDL HEADER, with the fields enclosed
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2008 Sun Microsystems, Inc.
 */
package org.opends.server.snmp;

import com.sun.management.snmp.InetAddressAcl;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.Vector;
import org.opends.server.admin.std.server.SNMPConnectionHandlerCfg;
import org.opends.server.loggers.debug.DebugLogger;
import org.opends.server.loggers.debug.DebugTracer;

/**
 * This class allows to manage the IP-ACL based access rights
 * for SNMP v1/v2c.
 */
public class SNMPInetAddressAcl implements InetAddressAcl {

    /**
     * The debug log tracer for this class.
     */
    private static final DebugTracer TRACER = DebugLogger.getTracer();
    /**
     * Current Security Configuration for the SNMP Connection Handler.
     */
    private SNMPConnectionHandlerCfg currentConfig;
   
    private TreeSet<InetAddress> hostsList;
    private boolean allManagers = false;
    
    private SortedSet<String> trapsDestinations;
    private String trapsCommunity;
    
    private SortedSet<String> communitiesList;
    

    /**
     * Creates an IP-Based ACL controller
     * @param configuration of the Configuration 
     */
    public SNMPInetAddressAcl(SNMPConnectionHandlerCfg configuration) {
        super();
        this.currentConfig = configuration;
        
        // hostsList
        SortedSet tmp = this.currentConfig.getAllowedManager();
        if (tmp.isEmpty()) {
            this.allManagers=true;
        }
        this.hostsList = new TreeSet<InetAddress>();
        // Transform the String list into InetAddress List 
        for (Iterator iter = tmp.iterator(); iter.hasNext();) {
            try {
                String dest = (String) iter.next();
                this.hostsList.add(InetAddress.getByName(dest));
            } catch (UnknownHostException ex) {
            }
        }
        
        this.trapsDestinations = this.currentConfig.getTrapsDestination();
        this.communitiesList = this.currentConfig.getCommunity();
        this.trapsCommunity = this.currentConfig.getTrapsCommunity();
        
    }

    /**
     * Gets the name of the acl
     * @return the name of the acl as a String
     */
    public String getName() {
        return "OpenDS";
    }

    /**
     * {@inheritDoc}
     */
    public boolean checkReadPermission(InetAddress address) {

        if (this.allManagers) {
            return true;
        }
        
        if ((this.hostsList==null) || (this.hostsList.isEmpty())) {
            return false;
        }

        // check the address is in the configured allowed managers
        boolean found = false;
        for (Iterator iter = this.hostsList.iterator(); iter.hasNext();) {
            InetAddress host = (InetAddress)iter.next();
            if (host.equals(address)) {
                found = true;
                break;
            }
        }
        return found;
    }

    /**
     * {@inheritDoc}
     */
    public boolean checkReadPermission(InetAddress address, String community) {
        if ((this.checkReadPermission(address)) &&
                (this.checkCommunity(community))) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * {@inheritDoc}
     */
    public boolean checkCommunity(String community) {
        return this.communitiesList.contains(community);
    }

    /**
     * {@inheritDoc}
     */
    public boolean checkWritePermission(InetAddress address) {
        // WRITE Access are always denied
        return false;
    }

    /**
     * {@inheritDoc}
     */
    public boolean checkWritePermission(InetAddress address, String community) {
        // WRITE Access are always denied
        return false;
    }

    /**
     * {@inheritDoc}
     * @return the list<InetAddress> of traps destinations
     */
    public Enumeration getTrapDestinations() {
        Vector<InetAddress> tempDests = new Vector<InetAddress>();
        for (Iterator iter = this.trapsDestinations.iterator(); iter.hasNext();) 
        {
            try {
                String dest = (String) iter.next();
                InetAddress addr = InetAddress.getByName(dest);
                tempDests.add(addr);
            } catch (UnknownHostException ex) {
            }
        }
        return tempDests.elements();
    }

    /**
     * {@inheritDoc}
     * @param address
     * @return 
     */
    public Enumeration getTrapCommunities(InetAddress address) {
        Vector<String> trapCommunities = new Vector<String>();
        trapCommunities.add(this.trapsCommunity);
        return trapCommunities.elements();
    }

    /**
     * {@inheritDoc}
     * @return an empty enumeration
     */
    public Enumeration getInformDestinations() {
        Vector<String> informDests = new Vector<String>();
        return informDests.elements();
    }

    /**
     * {@inheritDoc}
     * @param address
     * @return an empty enumeration
     */
    public Enumeration getInformCommunities(InetAddress address) {
        Vector<String> informCommunities = new Vector<String>();
        return informCommunities.elements();
    }
    
}

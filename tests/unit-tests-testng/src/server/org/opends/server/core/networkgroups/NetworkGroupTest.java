/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2006-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2014 ForgeRock AS.
 */
package org.opends.server.core.networkgroups;

import java.util.ArrayList;

import org.forgerock.opendj.ldap.ModificationType;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SearchScope;
import org.opends.server.DirectoryServerTestCase;
import org.opends.server.TestCaseUtils;
import org.opends.server.core.ModifyOperation;
import org.opends.server.core.SearchOperation;
import org.opends.server.core.Workflow;
import org.opends.server.core.WorkflowImpl;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.internal.SearchRequest;
import org.opends.server.types.Attribute;
import org.opends.server.types.Attributes;
import org.opends.server.types.DN;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.InitializationException;
import org.opends.server.types.Modification;
import org.opends.server.util.StaticUtils;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.opends.messages.CoreMessages.*;
import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.protocols.internal.InternalClientConnection.*;
import static org.opends.server.protocols.internal.Requests.*;
import static org.testng.Assert.*;

/**
 * This set of tests test the network groups.
 */
@SuppressWarnings("javadoc")
public class NetworkGroupTest extends DirectoryServerTestCase {
  //===========================================================================
  //                      B E F O R E    C L A S S
  //===========================================================================

  /**
   * Sets up the environment for performing the tests in this suite.
   *
   * @throws Exception if the environment could not be set up.
   */
  @BeforeClass
  public void setUp() throws Exception
  {
    // This test suite depends on having the schema available,
    // so we'll start the server.
    TestCaseUtils.startServer();
  }

  //===========================================================================
  //                      D A T A    P R O V I D E R
  //===========================================================================

  /**
   * Provides information to create a network group with one workflow inside.
   *
   * Each set of DNs contains:
   * - one network group identifier
   * - one base DN for the workflow to register with the network group

   */
  @DataProvider (name = "DNSet_0")
  public Object[][] initDNSet_0() throws Exception
  {
    // Network group ID
    String networkGroupID1 = "networkGroup1";
    String networkGroupID2 = "networkGroup2";

    // Workflow base DNs
    DN dn1 = DN.valueOf("o=test1");
    DN dn2 = DN.valueOf("o=test2");

    // Network group info
    return new Object[][] {
        // Test1: create a network group with the identifier networkGroupID1
        { networkGroupID1, dn1 },

        // Test2: create the same network group to check that previous
        // network group was properly cleaned.
        { networkGroupID1, dn1 },

        // Test3: create another network group
        { networkGroupID2, dn2 },
    };
  }


  /**
   * Provides a single DN to search a workflow in a network group.
   *
   * Each set of DNs is composed of:
   * - one baseDN
   * - one subordinateDN
   * - a boolean telling whether we expect to find a workflow for the baseDN
   *   in the default network group
   * - a boolean telling whether we expect to find a workflow for the baseDN
   *   in the administration network group
   * - a boolean telling whether we expect to find a workflow for the baseDN
   *   in the internal network group
   *
   * @return set of DNs
   * @throws Exception  when DN.decode fails
   */
  @DataProvider(name = "DNSet_1")
  public Object[][] initDNSet_1() throws Exception
  {
    DN dnRootDSE = DN.valueOf("");
    DN dnConfig  = DN.valueOf("cn=config");
    DN dnMonitor = DN.valueOf("cn=monitor");
    DN dnSchema  = DN.valueOf("cn=schema");
    DN dnTasks   = DN.valueOf("cn=tasks");
    DN dnBackups = DN.valueOf("cn=backups");
    DN dnDummy   = DN.valueOf("o=dummy_suffix");

    DN dnSubordinateConfig  = DN.valueOf("cn=Work Queue,cn=config");
    DN dnSubordinateMonitor = DN.valueOf("cn=schema Backend,cn=monitor");
    DN dnSubordinateTasks   = DN.valueOf("cn=Scheduled Tasks,cn=tasks");
    // No DN subordinate for schema because the schema backend is
    // currently empty.
    // No DN subordinate for cn=backups because by default there is no
    // child entry under cn=backups.

    // Sets of DNs
    return new Object[][] {
        { dnRootDSE,  null,                 true,  true,  true },
        { dnConfig,   dnSubordinateConfig,  true,  true,  true },
        { dnMonitor,  dnSubordinateMonitor, true,  true,  true },
        { dnTasks,    dnSubordinateTasks,   true,  true,  true },
        { dnSchema,   null,                 true,  true,  true },
        { dnBackups,  null,                 true,  true,  true },
        { dnDummy,    null,                 false, false, false },
    };
  }


  /**
   * Provides information to create a network group to test the routing
   * process.
   *
   * Each set of DNs contains:
   * - one base DN for the 1st workflow
   * - one base DN for the 2nd workflow
   * - one base DN for the 3rd workflow
   * - one subordinate DN for the 1st workflow
   * - one subordinate DN for the 2nd workflow
   * - one subordinate DN for the 3rd workflow
   * - one unrelated DN which has no hierarchical relationship with
   *   any of the above DNs

   */
  @DataProvider (name = "DNSet_2")
  public Object[][] initDNSet_2() throws Exception
  {
    // Network group definition
    DN     dn1          = DN.valueOf("o=test1");
    DN     dn2          = DN.valueOf("o=test2");
    DN     dn3          = DN.valueOf("o=test3");
    DN     subordinate1 = DN.valueOf("ou=subtest1,o=test1");
    DN     subordinate2 = DN.valueOf("ou=subtest2,o=test2");
    DN     subordinate3 = DN.valueOf("ou=subtest3,o=test3");
    DN     unrelatedDN  = DN.valueOf("o=dummy");

    // Network group info
    return new Object[][] {
        // Test1: one DN for one workflow
        {
          dn1, null, null,
          subordinate1, null, null,
          unrelatedDN
        },
        // Test2: two DNs for two workflows
        {
          dn1, dn2, null,
          subordinate1, subordinate2, null,
          unrelatedDN
        },
        // Test3: three DNs for three workflows
        {
          dn1, dn2, dn3,
          subordinate1, subordinate2, subordinate3,
          unrelatedDN
        }
    };
  }


  /**
   * Provides information to create a network group with resource limits.
   */
  @DataProvider (name = "DNSet_3")
  public Object[][] initDNSet_3() throws Exception
  {
    // Network group definition
    String networkGroupID = "networkGroup1";
    DN  dn = DN.valueOf("o=test1");
    int prio = 1;

    // Resource limits
    int maxConnections = 10;
    int maxConnectionsFromSameClient = 5;
    int maxOpsPerConn = 4;
    int maxConcurrentOpsPerConn = 2;
    int searchTimeLimit = 100;
    int searchSizeLimit = 50;
    int minSubstringLength = 4;

    // Network group info
    return new Object[][] {
        // Test1: one DN for one workflow
        {
          networkGroupID,
          dn,
          prio,
          maxConnections,
          maxConnectionsFromSameClient,
          maxOpsPerConn,
          maxConcurrentOpsPerConn,
          searchTimeLimit,
          searchSizeLimit,
          minSubstringLength
        }
    };
  }


  /**
   * Provides information to create 2 network groups with different priorities.
   */
  @DataProvider (name = "DNSet_4")
  public Object[][] initDNSet_4() throws Exception
  {
    String networkGroupID1 = "group1";
    String networkGroupID2 = "group2";
    DN dn1 = DN.valueOf("o=test1");
    DN dn2 = DN.valueOf("o=test2");

    return new Object[][] {
      {
        networkGroupID1, dn1, 1,
        networkGroupID2, dn2, 2
      },
      {
        networkGroupID1, dn1, 2,
        networkGroupID2, dn2, 1
      }
    };
  }

  //===========================================================================
  //                        T E S T   C A S E S
  //===========================================================================


  /**
   * Tests the network group registration.
   *
   * @param networkGroupID   the ID of the network group to register
   * @param workflowBaseDN1  the base DN of the first workflow node to register
   *                         in the network group
   */
  @Test (dataProvider = "DNSet_0", groups = "virtual")
  public void testNetworkGroupRegistration(
      String networkGroupID,
      DN     workflowBaseDN
      )
      throws Exception
  {
    // Create and register the network group with the server.
    NetworkGroup networkGroup = new NetworkGroup(networkGroupID);
    networkGroup.register();

    // Register again the network group with the server and catch the
    // expected DirectoryServer exception.
    try
    {
      networkGroup.register();
      fail("InitializationException sjhould have been thrown");
    }
    catch (InitializationException e)
    {
      assertTrue(StaticUtils.hasDescriptor(e.getMessageObject(),
          ERR_REGISTER_NETWORK_GROUP_ALREADY_EXISTS));
    }

    // Create a workflow -- the workflow ID is the string representation
    // of the workflow base DN.
    WorkflowImpl workflow = new WorkflowImpl(workflowBaseDN.toString(), workflowBaseDN, null, null);

    // Register the workflow with the network group.
    networkGroup.registerWorkflow(workflow);

    // Register again the workflow with the network group and catch the
    // expected DirectoryServer exception.
    try
    {
      networkGroup.registerWorkflow(workflow);
      fail("DirectoryException sjhould have been thrown");
    }
    catch (DirectoryException de)
    {
      assertTrue(StaticUtils.hasDescriptor(de.getMessageObject(),
          ERR_REGISTER_WORKFLOW_NODE_ALREADY_EXISTS));
    }

    // Clean the network group
    networkGroup.deregisterWorkflow(workflow.getWorkflowId());
    networkGroup.deregister();
  }


  /**
   * Check the route process in the default network group.
   *
   *  @param dnToSearch     the DN of a workflow to search in the default
   *                        network group
   *  @param dnSubordinate  a subordinate DN of dnToSearch
   *  @param exists         true if we are supposed to find a workflow for
   *                        dnToSearch
   */
  @Test (dataProvider = "DNSet_1", groups = "virtual")
  public void checkDefaultNetworkGroup(
      DN      dnToSearch,
      DN      dnSubordinate,
      boolean existsInDefault,
      boolean existsInAdmin,
      boolean existsInInternal
      )
  {
    // let's get the default network group -- it should always exist
    NetworkGroup defaultNG = NetworkGroup.getDefaultNetworkGroup();
    assertNotNull(defaultNG);

    // let's check the routing through the network group
    doCheckNetworkGroup(defaultNG, dnToSearch, dnSubordinate, null, existsInDefault);

    // let's get the admin network group -- it should always exist
    NetworkGroup adminNG = NetworkGroup.getAdminNetworkGroup();
    assertNotNull(adminNG);

    // let's check the routing through the network group
    doCheckNetworkGroup(adminNG, dnToSearch, dnSubordinate, null, existsInAdmin);

    // let's get the internal network group -- it should always exist
    NetworkGroup internalNG = NetworkGroup.getInternalNetworkGroup();
    assertNotNull(internalNG);

    // let's check the routing through the network group
    doCheckNetworkGroup(internalNG, dnToSearch, dnSubordinate, null, existsInInternal);
  }


  /**
   * Creates a network group with several workflows inside and do some check
   * on the route processing.
   *
   * @param dn1           the DN for the 1st workflow
   * @param dn2           the DN for the 2nd workflow
   * @param dn3           the DN for the 3rd workflow
   * @param subordinate1  the subordinate DN for the 1st workflow
   * @param subordinate2  the subordinate DN for the 2nd workflow
   * @param subordinate3  the subordinate DN for the 3rd workflow
   * @param unrelatedDN   a DN with no hierarchical relationship with
   *                      any of the DNs above
   *
   * @throws  DirectoryException  If the network group ID for a provided
   *                              network group conflicts with the network
   *                              group ID of an existing network group.
   */
  @Test (dataProvider = "DNSet_2", groups = "virtual")
  public void createNetworkGroup(
      DN dn1,
      DN dn2,
      DN dn3,
      DN subordinate1,
      DN subordinate2,
      DN subordinate3,
      DN unrelatedDN
      ) throws Exception
  {
    // The network group identifier is always the same for this test.
    String networkGroupID = "Network Group for test2";

    // Create the network group
    NetworkGroup networkGroup = new NetworkGroup(networkGroupID);
    assertNotNull(networkGroup);

    // Register the network group with the server
    networkGroup.register();

    // Create and register workflow 1, 2 and 3
    createAndRegisterWorkflow(networkGroup, dn1);
    createAndRegisterWorkflow(networkGroup, dn2);
    createAndRegisterWorkflow(networkGroup, dn3);

    // Check the route through the network group
    doCheckNetworkGroup(networkGroup, dn1, subordinate1, unrelatedDN, true);
    doCheckNetworkGroup(networkGroup, dn2, subordinate2, unrelatedDN, true);
    doCheckNetworkGroup(networkGroup, dn3, subordinate3, unrelatedDN, true);

    // Deregister the workflow1 and check the route again.
    // Workflow to deregister is identified by its baseDN.
    networkGroup.deregisterWorkflow(dn1);
    doCheckNetworkGroup(networkGroup, dn1, subordinate1, unrelatedDN, false);
    doCheckNetworkGroup(networkGroup, dn2, subordinate2, unrelatedDN, true);
    doCheckNetworkGroup(networkGroup, dn3, subordinate3, unrelatedDN, true);

    // Deregister the workflow2 and check the route again
    networkGroup.deregisterWorkflow(dn2);
    doCheckNetworkGroup(networkGroup, dn1, subordinate1, unrelatedDN, false);
    doCheckNetworkGroup(networkGroup, dn2, subordinate2, unrelatedDN, false);
    doCheckNetworkGroup(networkGroup, dn3, subordinate3, unrelatedDN, true);

    // Deregister the workflow3 and check the route again
    networkGroup.deregisterWorkflow(dn3);
    doCheckNetworkGroup(networkGroup, dn1, subordinate1, unrelatedDN, false);
    doCheckNetworkGroup(networkGroup, dn2, subordinate2, unrelatedDN, false);
    doCheckNetworkGroup(networkGroup, dn3, subordinate3, unrelatedDN, false);

    // Now create again the workflow 1, 2 and 3...
    WorkflowImpl w1 = createAndRegisterWorkflow(networkGroup, dn1);
    WorkflowImpl w2 = createAndRegisterWorkflow(networkGroup, dn2);
    WorkflowImpl w3 = createAndRegisterWorkflow(networkGroup, dn3);

    // ... and deregister the workflows using their workflowID
    // instead of their baseDN
    if (w1 != null)
    {
      networkGroup.deregisterWorkflow(w1.getWorkflowId());
      doCheckNetworkGroup(networkGroup, dn1, subordinate1, unrelatedDN, false);
      doCheckNetworkGroup(networkGroup, dn2, subordinate2, unrelatedDN, true);
      doCheckNetworkGroup(networkGroup, dn3, subordinate3, unrelatedDN, true);
    }

    if (w2 != null)
    {
      networkGroup.deregisterWorkflow(w2.getWorkflowId());
      doCheckNetworkGroup(networkGroup, dn1, subordinate1, unrelatedDN, false);
      doCheckNetworkGroup(networkGroup, dn2, subordinate2, unrelatedDN, false);
      doCheckNetworkGroup(networkGroup, dn3, subordinate3, unrelatedDN, true);
    }

    if (w3 != null)
    {
      networkGroup.deregisterWorkflow(w3.getWorkflowId());
      doCheckNetworkGroup(networkGroup, dn1, subordinate1, unrelatedDN, false);
      doCheckNetworkGroup(networkGroup, dn2, subordinate2, unrelatedDN, false);
      doCheckNetworkGroup(networkGroup, dn3, subordinate3, unrelatedDN, false);
    }

    // Deregister the network group
    networkGroup.deregister();
  }


  /**
   * This test checks that network groups are updated as appropriate when
   * backend base DNs are added or removed. When a new backend base DN is
   * added, the new suffix should be accessible for the route process - ie.
   * a workflow should be created and be a potential candidate for the route
   * process. Similarly, when a backend base DN is removed its associated
   * workflow should be removed; subsequently, any request targeting the
   * removed suffix should be rejected and a no such entry status code be
   * returned.
   */
  @Test
  public void testBackendBaseDNModification()
         throws Exception
  {
    String suffix  = "dc=example,dc=com";
    String suffix2 = "o=networkgroup suffix";
    String backendBaseDNName = "ds-cfg-base-dn";

    // Initialize a backend with a base entry.
    TestCaseUtils.clearJEBackend(true, "userRoot", suffix);

    // Check that suffix is accessible while suffix2 is not.
    searchEntry(suffix, true);
    searchEntry(suffix2, false);

    // Add a new suffix in the backend and create a base entry for the
    // new suffix.
    String backendConfigDN = "ds-cfg-backend-id=userRoot," + DN_BACKEND_BASE;
    modifyAttribute(backendConfigDN, ModificationType.ADD, backendBaseDNName, suffix2);
    addBaseEntry(suffix2, "networkgroup suffix");

    // Both old and new suffix should be accessible.
    searchEntry(suffix, true);
    searchEntry(suffix2, true);

    // Remove the new suffix...
    modifyAttribute(backendConfigDN, ModificationType.DELETE, backendBaseDNName, suffix2);

    // ...and check that the removed suffix is no more accessible.
    searchEntry(suffix, true);
    searchEntry(suffix2, false);

    // Replace the suffix with suffix2 in the backend
    modifyAttribute(backendConfigDN, ModificationType.REPLACE, backendBaseDNName, suffix2);

    // Now none of the suffixes are accessible: this means the entries
    // under the old suffix are not moved to the new suffix.
    searchEntry(suffix, false);
    searchEntry(suffix2, false);

    // Add a base entry for the new suffix
    addBaseEntry(suffix2, "networkgroup suffix");

    // The new suffix is accessible while the old one is not.
    searchEntry(suffix, false);
    searchEntry(suffix2, true);

    // Reset the configuration with previous suffix
    modifyAttribute(backendConfigDN, ModificationType.REPLACE, backendBaseDNName, suffix);
  }



  /**
   * Tests that routing mode changes cause the network group config
   * manager to be initialized, shutdown, and reinitialized correctly.
   * <p>
   * Disabled because NGs are not supported (issue OPENDJ-335).
   *
   * @see <a
   *      href="https://opends.dev.java.net/issues/show_bug.cgi?id=3775">Issue 3775</a>
   * @throws Exception
   *           If an unexpected error occurred.
   */
  @Test(enabled=false)
  public void testIssue3775() throws Exception
  {
    // Switch to and from manual mode twice in order to ensure that the
    // config manager is initialized twice. Then register a network
    // group. If the initialization has worked properly the network
    // group should be added successfully. In the case of issue 3775,
    // the config add listeners ended up being added twice so adding a
    // network group failed because the admin framework thought it had
    // been added twice.

    // Switch to manual mode once.
    TestCaseUtils.dsconfig(
        "set-global-configuration-prop",
        "--set", "workflow-configuration-mode:manual");

    try
    {
      // Switch back.
      TestCaseUtils.dsconfig(
          "set-global-configuration-prop",
          "--set", "workflow-configuration-mode:auto");

      // Switch to manual mode twice.
      TestCaseUtils.dsconfig(
          "set-global-configuration-prop",
          "--set", "workflow-configuration-mode:manual");

      // Now add network group.
      final String networkGroupID = "Network group issue 3775";

      TestCaseUtils.dsconfig(
          "create-network-group",
          "--group-name", networkGroupID,
          "--set", "enabled:true",
          "--set", "priority:" + 123);

      try
      {
        // Ensure that the network group was created ok.
        NetworkGroup networkGroup = NetworkGroup.getNetworkGroup(networkGroupID);
        assertNotNull(networkGroup, "The network group does not seem to be registered.");
      }
      finally
      {
        // Remove the network group.
        TestCaseUtils.dsconfig(
            "delete-network-group",
            "--group-name", networkGroupID);
      }
    }
    finally
    {
      TestCaseUtils.dsconfig(
          "set-global-configuration-prop",
          "--set", "workflow-configuration-mode:auto");
    }
  }



  /**
   * Tests the network group resource limits
   * <p>
   * Disabled because NGs are not supported (issue OPENDJ-335).
   *
   * @param networkGroupID   the ID of the network group to register
   * @param workflowBaseDN1  the base DN of the first workflow node to register
   *                         in the network group
   */
  @Test (dataProvider = "DNSet_3", groups = "virtual", enabled=false)
  public void testNetworkGroupResourceLimits(
      String networkGroupID,
      DN     workflowBaseDN,
      int    priority,
      final int    maxConnections,
      final int    maxConnectionsFromSameClient,
      final int    maxOpsPerConn,
      final int    maxConcurrentOpsPerConn,
      final int    searchTimeLimit,
      final int    searchSizeLimit,
      final int    minSubstringLength
      )
      throws Exception
  {
    // Create and register the network group with the server.
    TestCaseUtils.dsconfig(
        "set-global-configuration-prop",
        "--set", "workflow-configuration-mode:manual");

    try
    {
      TestCaseUtils.dsconfig(
          "create-network-group",
          "--group-name", networkGroupID,
          "--set", "enabled:true",
          "--set", "priority:" + priority);

      try
      {
        // Ensure that the network group was created ok.
        NetworkGroup networkGroup = NetworkGroup.getNetworkGroup(networkGroupID);
        assertNotNull(networkGroup, "The network group does not seem to be registered.");

        TestCaseUtils.dsconfig(
            "create-network-group-qos-policy",
            "--group-name", networkGroupID,
            "--type", "resource-limits",
            "--set", "max-concurrent-ops-per-connection:" + maxConcurrentOpsPerConn,
            "--set", "max-connections:" + maxConnections,
            "--set", "max-connections-from-same-ip:" + maxConnectionsFromSameClient,
            "--set", "max-ops-per-connection:" + maxOpsPerConn,
            "--set", "min-substring-length:" + minSubstringLength,
            "--set", "size-limit:" + searchSizeLimit,
            "--set", "time-limit:" + searchTimeLimit + "s");

        // Check the resource limits are set properly.
        assertEquals(networkGroup.getTimeLimit(), searchTimeLimit);
        assertEquals(networkGroup.getSizeLimit(), searchSizeLimit);
        assertEquals(networkGroup.getMinSubstring(), minSubstringLength);

        TestCaseUtils.dsconfig(
            "delete-network-group-qos-policy",
            "--group-name", networkGroupID,
            "--policy-type", "resource-limits");
      }
      finally
      {
        // The policy will get removed by this as well.
        TestCaseUtils.dsconfig("delete-network-group", "--group-name",
            networkGroupID);
      }
    }
    finally
    {
      TestCaseUtils.dsconfig(
          "set-global-configuration-prop",
          "--set", "workflow-configuration-mode:auto");
    }
  }



  /**
   * Tests the mechanism to attribute a network group to a client connection,
   * comparing the priority.
   */
  @Test (dataProvider = "DNSet_4", groups = "virtual")
  public void testNetworkGroupPriority(
      String ng1,
      DN dn1,
      int prio1,
      String ng2,
      DN dn2,
      int prio2
      )
      throws Exception
  {
    // Create and register the network group with the server.
    NetworkGroup networkGroup1 = new NetworkGroup(ng1);
    networkGroup1.register();
    networkGroup1.setNetworkGroupPriority(prio1);
    NetworkGroup networkGroup2 = new NetworkGroup(ng2);
    networkGroup2.register();
    networkGroup2.setNetworkGroupPriority(prio2);

    // Create a workflow -- the workflow ID is the string representation
    // of the workflow base DN.
    WorkflowImpl workflow1 = new WorkflowImpl(dn1.toString(), dn1, null, null);
    WorkflowImpl workflow2 = new WorkflowImpl(dn2.toString(), dn2, null, null);

    // Register the workflow with the network group.
    networkGroup1.registerWorkflow(workflow1);
    networkGroup2.registerWorkflow(workflow2);

    // Clean the network group
    networkGroup1.deregisterWorkflow(workflow1.getWorkflowId());
    networkGroup1.deregister();
    networkGroup2.deregisterWorkflow(workflow2.getWorkflowId());
    networkGroup2.deregister();
  }

  /**
   * This test checks that the network group takes into account the
   * subordinate naming context defined in the RootDSEBackend.
   */
  @Test
  public void testRootDseSubordinateNamingContext()
         throws Exception
  {
    // Backends for the test
    String backend1   = "o=test-rootDSE-subordinate-naming-context-1";
    String backend2   = "o=test-rootDSE-subordinate-naming-context-2";
    String backendID1 = "test-rootDSE-subordinate-naming-context-1";
    String backendID2 = "test-rootDSE-subordinate-naming-context-2";

    // Clean all the backends.
    TestCaseUtils.clearDataBackends();

    // Create a client connection for the test.
    InternalClientConnection connection =
      InternalClientConnection.getRootConnection();

    // At this point, the list of subordinate naming context is not defined
    // yet (null): any public backend should be visible. Create a backend
    // with a base entry and check that the test naming context is visible.
    TestCaseUtils.initializeMemoryBackend(backendID1, backend1, true);
    searchPublicNamingContexts(connection, true,  1);

    // Create another test backend and check that the new backend is visible
    TestCaseUtils.initializeMemoryBackend(backendID2, backend2, true);
    searchPublicNamingContexts(connection, true,  2);

    // Now put in the list of subordinate naming context the backend1
    // naming context. This white list will prevent the backend2 to be
    // visible.
    TestCaseUtils.dsconfig(
        "set-root-dse-backend-prop",
        "--set", "subordinate-base-dn:" + backend1);
    searchPublicNamingContexts(connection, true, 1);

    // === Cleaning

    // Reset the subordinate naming context list.
    // Both naming context should be visible again.
    TestCaseUtils.dsconfig(
        "set-root-dse-backend-prop",
        "--reset", "subordinate-base-dn");
    searchPublicNamingContexts(connection, true, 2);

    // Clean the test backends. There is no more naming context.
    TestCaseUtils.clearMemoryBackend(backendID1);
    TestCaseUtils.clearMemoryBackend(backendID2);
    searchPublicNamingContexts(connection, false, 0);
  }


  /**
   * Searches the list of naming contexts.
   *
   * @param connection    the connection to use for the search request
   * @param shouldExist   indicates whether at least one NC should be found
   * @param expectedNamingContexts  the number of expected naming contexts
   */
  private void searchPublicNamingContexts(
      InternalClientConnection connection,
      boolean shouldExist,
      int expectedNamingContexts
      ) throws Exception
  {
    SearchRequest request = newSearchRequest(DN.rootDN(), SearchScope.SINGLE_LEVEL);
    SearchOperation search = connection.processSearch(request);

    // Check the number of found naming context
    assertEquals(search.getResultCode(), shouldExist ? ResultCode.SUCCESS : ResultCode.NO_SUCH_OBJECT);
    if (shouldExist)
    {
      assertEquals(search.getEntriesSent(), expectedNamingContexts);
    }
  }


  /**
   * Searches an entry on a given connection.
   *
   * @param baseDN        the request base DN string
   * @param shouldExist   if true the searched entry is expected to be found
   */
  private void searchEntry(String baseDN, boolean shouldExist) throws Exception
  {
    SearchRequest request = newSearchRequest(DN.valueOf(baseDN), SearchScope.BASE_OBJECT);
    SearchOperation search = getRootConnection().processSearch(request);

    // Compare the result code with the expected one
    assertEquals(search.getResultCode(), shouldExist ? ResultCode.SUCCESS : ResultCode.NO_SUCH_OBJECT);
  }


  /**
   * Creates a base entry for the given suffix.
   *
   * @param suffix      the suffix for which the base entry is to be created
   */
  private void addBaseEntry(String suffix, String namingAttribute) throws Exception
  {
    TestCaseUtils.addEntry(
        "dn: " + suffix,
        "objectClass: top",
        "objectClass: organization",
        "o: " + namingAttribute);
  }


  /**
   * Adds/Deletes/Replaces an attribute in a given entry.
   *
   * @param baseDN          the request base DN string
   * @param modType         the modification type (add/delete/replace)
   * @param attributeName   the name  of the attribute to add/delete/replace
   * @param attributeValue  the value of the attribute to add/delete/replace
   */
  private void modifyAttribute(
      String baseDN,
      ModificationType modType,
      String  attributeName,
      String  attributeValue
      ) throws Exception
  {
    ArrayList<Modification> mods = new ArrayList<Modification>();
    Attribute attributeToModify =
      Attributes.create(attributeName, attributeValue);
    mods.add(new Modification(modType, attributeToModify));
    ModifyOperation modifyOperation = getRootConnection().processModify(DN.valueOf(baseDN), mods);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
  }


  /**
   * Checks the DN routing through a network group.
   *
   * @param networkGroup    the network group to use for the check
   * @param dnToSearch      the DN of a workflow in the network group; may
   *                        be null
   * @param dnSubordinate   a subordinate of dnToSearch
   * @param unrelatedDN     a DN with no hierarchical relationship with
   *                        any of the DNs above, may be null
   * @param shouldExist     true if we are supposed to find a workflow for
   *                        dnToSearch
   */
  private void doCheckNetworkGroup(
      NetworkGroup networkGroup,
      DN           dnToSearch,
      DN           dnSubordinate,
      DN           unrelatedDN,
      boolean      shouldExist
      )
  {
    if (dnToSearch == null)
    {
      return;
    }

    // Let's retrieve the workflow that maps best the dnToSearch
    Workflow workflow = networkGroup.getWorkflowCandidate(dnToSearch);
    if (shouldExist)
    {
      assertNotNull(workflow);
    }
    else
    {
      assertNull(workflow);
    }

    // let's retrieve the workflow that handles the DN subordinate:
    // it should be the same than the one for dnToSearch
    if (dnSubordinate != null)
    {
       Workflow workflow2 = networkGroup.getWorkflowCandidate(dnSubordinate);
       assertEquals(workflow2, workflow);
    }

    // Check that the unrelatedDN is not handled by any workflow
    if (unrelatedDN != null)
    {
      assertNull(networkGroup.getWorkflowCandidate(unrelatedDN));
    }
  }


  /**
   * Creates a workflow and register it with a network group.
   *
   * @param networkGroup     a network group to register the workflow with
   * @param workflowBaseDN   the base DN of the workflow to register; may be
   *                         null
   * @throws  DirectoryException  If the workflow ID for the provided
   *                              workflow conflicts with the workflow
   *                              ID of an existing workflow.
   */
  private WorkflowImpl createAndRegisterWorkflow(
      NetworkGroup networkGroup,
      DN           workflowBaseDN
      ) throws DirectoryException
  {
    assertNotNull(networkGroup);

    if (workflowBaseDN == null)
    {
      return null;
    }

    // Create a workflow with no task inside. The workflow identifier
    // is the a string representation of the workflow base DN.
    WorkflowImpl workflow = new WorkflowImpl(workflowBaseDN.toString(), workflowBaseDN, null, null);
    assertNotNull(workflow);

    // Register the workflow with the network group.
    networkGroup.registerWorkflow(workflow);

    return workflow;
  }

}

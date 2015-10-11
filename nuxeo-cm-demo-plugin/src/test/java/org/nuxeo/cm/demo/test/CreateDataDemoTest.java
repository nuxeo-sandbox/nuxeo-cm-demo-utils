/*
 * (C) Copyright 2014 Nuxeo SA (http://nuxeo.com/) and contributors.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public License
 * (LGPL) version 2.1 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl-2.1.html
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * Contributors:
 *     Thibaud Arguillere
 */

package org.nuxeo.cm.demo.test;

import static org.junit.Assert.*;

import java.io.IOException;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.cm.demo.CreateDemoData;
import org.nuxeo.ecm.automation.AutomationService;
import org.nuxeo.ecm.automation.test.AutomationFeature;
//import org.nuxeo.ecm.automation.test.EmbeddedAutomationServerFeature;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentException;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.core.lifecycle.LifeCycleException;
import org.nuxeo.ecm.core.test.TransactionalFeature;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.LocalDeploy;

import com.google.inject.Inject;

@RunWith(FeaturesRunner.class)
@Features({ TransactionalFeature.class, AutomationFeature.class})
@Deploy({ "org.nuxeo.ecm.directory.sql",
        /*
        "org.nuxeo.ecm.core.persistence",
        "org.nuxeo.ecm.platform.uidgen.core",
        "org.nuxeo.runtime.datasource",*/
        // ------------------------------
        "org.nuxeo.ecm.platform.picture.core",
        "org.nuxeo.ecm.platform.commandline.executor",
        "org.nuxeo.template.manager", "nuxeo-datademo",
        "studio.extensions.cm-showcase-nux", "nuxeo-cm-demo-utils" })
// WARNING: Because of dependencies created by Studio (and the cm-showcase-nux
// project), we must0 "deploy" some components that we, actually don't use nor
// need for testing. => we have contribution in the test that just declares
// these components, but they are empty (the unit test framework then thinks
// they are here and it's enough to continue the deployment).
// These fake components are in /test/resources/fake-*.xml
@LocalDeploy({ "org.nuxeo.cm.demo.test:fake-sqldirectory-contrib.xml",
        "org.nuxeo.cm.demo.test:fake-platformrendition-contrib.xml" })
public class CreateDataDemoTest {

    protected DocumentModel claimsFolder;

    @Inject
    CoreSession coreSession;

    @Inject
    AutomationService service;

    protected void doLog(String what) {
        System.out.println(what);
    }

    // Not sure it's the best way to get the current method name, but at least
    // it works
    protected String getCurrentMethodName(RuntimeException e) {
        StackTraceElement currentElement = e.getStackTrace()[0];
        return currentElement.getMethodName();
    }

    @Before
    public void setUp() {

        assertNotNull(coreSession);
        claimsFolder = coreSession.createDocumentModel("/", "test-claims",
                "Folder");
        claimsFolder.setPropertyValue("dc:title", "test-pictures");
        claimsFolder = coreSession.createDocument(claimsFolder);
        claimsFolder = coreSession.saveDocument(claimsFolder);
        coreSession.save();

        setUpTemplateRendering();
        shouldBeAbleToCreateInsuranceClaims();

    }

    @After
    public void cleanup() {

        coreSession.removeDocument(claimsFolder.getRef());
        coreSession.save();
    }

    // CreateDemoData expects the nuxeo template rendering plug-in to be
    // installed. We don't care if it works for this test, we just want it to be
    // deployed and we want to have a "Claim Report" TemplateSource document
    protected void setUpTemplateRendering() {

        assertNotNull(claimsFolder);

        DocumentModel template = coreSession.createDocumentModel(
                claimsFolder.getPathAsString(), "claim-report",
                "TemplateSource");
        assertNotNull(template);

        template.setPropertyValue("dc:title", "Claim Report");
        template = coreSession.createDocument(template);
        coreSession.save();

    }

    protected void shouldBeAbleToCreateInsuranceClaims() {

        String mn = getCurrentMethodName(new RuntimeException());
        doLog("Testing: " + mn);

        DocumentModel claim = coreSession.createDocumentModel(
                claimsFolder.getPathAsString(), "ignore", "InsuranceClaim");
        assertNotNull(claim);

        claim.setPropertyValue("incl:incident_kind", "accident");
        claim.setPropertyValue("pein:first_name", "john");
        claim.setPropertyValue("pein:last_name", "doe");

        claim = coreSession.createDocument(claim);
        assertNotNull(claim);
        assertEquals("accident",
                (String) claim.getPropertyValue("incl:incident_kind"));
        assertEquals("john", (String) claim.getPropertyValue("pein:first_name"));
        assertEquals("doe", (String) claim.getPropertyValue("pein:last_name"));

        coreSession.removeDocument(claim.getRef());
        coreSession.save();

        doLog("Testing: " + mn + " => done");

    }

    @Ignore
    @Test
    public void testCreateAFewClaims() throws IOException, DocumentException,
            LifeCycleException {

        String mn = getCurrentMethodName(new RuntimeException());
        doLog("Testing: " + mn);

        CreateDemoData cdd = new CreateDemoData(coreSession, claimsFolder, 10);
        cdd.run();
        coreSession.save();
        String nxql = "SELECT * FROM InsuranceClaim";
        DocumentModelList docs = coreSession.query(nxql);
        assertEquals(11, docs.size());

        doLog("Testing: " + mn + " => done");

    }

    /*
     * @Test public void testLifeCycle() throws Exception {
     * 
     * ArrayList<String> states = new ArrayList<String>();
     * states.add("Received"); states.add("CheckContract");
     * states.add("Opened"); states.add("Completed");
     * states.add("ExpertOnSiteNeeded"); states.add("Evaluated");
     * states.add("DecisionMade"); states.add("Archived");
     * 
     * ArrayList<String> transitions = new ArrayList<String>();
     * transitions.add("to_CheckContract"); transitions.add("to_Opened");
     * transitions.add("to_Completed");
     * transitions.add("to_ExpertOnSiteNeeded");
     * transitions.add("to_Evaluated"); transitions.add("to_DecisionMade");
     * transitions.add("to_Archived");
     * 
     * String[] strStates = new String[states.size()];
     * states.toArray(strStates); String[] strTransitions = new
     * String[transitions.size()]; transitions.toArray(strTransitions);
     * LifecycleHandler lifeCycleWithOnSite = new LifecycleHandler(strStates,
     * strTransitions);
     * 
     * states.remove("ExpertOnSiteNeeded"); strStates = new
     * String[states.size()]; states.toArray(strStates);
     * transitions.remove("to_ExpertOnSiteNeeded"); strTransitions = new
     * String[transitions.size()]; transitions.toArray(strTransitions);
     * LifecycleHandler lifeCycleNoOnSite = new LifecycleHandler(strStates,
     * strTransitions);
     * 
     * assertTrue(lifeCycleWithOnSite.compareStates("Evaluated",
     * "ExpertOnSiteNeeded") > 0);
     * assertTrue(lifeCycleNoOnSite.compareStates("Received", "Opened") < 0);
     * 
     * try { int ignore = lifeCycleNoOnSite.compareStates("123", "456");
     * assertTrue("Should have raise and IllegalArgumentException", false); }
     * catch(Exception e) {
     * 
     * } }
     */

}

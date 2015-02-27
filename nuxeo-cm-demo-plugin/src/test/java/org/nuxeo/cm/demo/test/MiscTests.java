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

import java.util.ArrayList;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.datademo.LifecycleHandler;
import org.nuxeo.ecm.automation.AutomationService;
import org.nuxeo.ecm.automation.test.EmbeddedAutomationServerFeature;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.test.CoreFeature;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

import com.google.inject.Inject;

@RunWith(FeaturesRunner.class)
@Features({ PlatformFeature.class, CoreFeature.class, EmbeddedAutomationServerFeature.class })
@Deploy({ "nuxeo-cm-demo-utils" })
public class MiscTests {

    @Inject
    CoreSession coreSession;

    @Inject
    AutomationService service;

    @Test
    public void testLifeCycle() throws Exception {

        ArrayList<String> states = new ArrayList<String>();
        states.add("Received");
        states.add("CheckContract");
        states.add("Opened");
        states.add("Completed");
        states.add("ExpertOnSiteNeeded");
        states.add("Evaluated");
        states.add("DecisionMade");
        states.add("Archived");
        
        ArrayList<String> transitions = new ArrayList<String>();
        transitions.add("to_CheckContract");
        transitions.add("to_Opened");
        transitions.add("to_Completed");
        transitions.add("to_ExpertOnSiteNeeded");
        transitions.add("to_Evaluated");
        transitions.add("to_DecisionMade");
        transitions.add("to_Archived");
        
        String[] strStates = new String[states.size()];
        states.toArray(strStates);
        String[] strTransitions = new String[transitions.size()];
        transitions.toArray(strTransitions);
        LifecycleHandler lifeCycleWithOnSite = new LifecycleHandler(strStates, strTransitions);
        
        states.remove("ExpertOnSiteNeeded");
        strStates = new String[states.size()];
        states.toArray(strStates);
        transitions.remove("to_ExpertOnSiteNeeded");
        strTransitions = new String[transitions.size()];
        transitions.toArray(strTransitions);
        LifecycleHandler lifeCycleNoOnSite = new LifecycleHandler(strStates, strTransitions);
        
        assertTrue(lifeCycleWithOnSite.compareStates("Evaluated", "ExpertOnSiteNeeded") > 0);
        assertTrue(lifeCycleNoOnSite.compareStates("Received", "Opened") < 0);
        
        try {
            int ignore = lifeCycleNoOnSite.compareStates("123", "456");
            assertTrue("Should have raise and IllegalArgumentException", false);
        } catch(Exception e) {
            
        }
    }

}

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

import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.cm.demo.RandomFirstLastName;
import org.nuxeo.cm.demo.RandomFirstLastName.GENDER;
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
    public void testRandomFirstLastNames() throws Exception {

        String s;

        // Just checking nothing is triggered
        s = RandomFirstLastName.getFirstName(GENDER.MALE);
        assertNotNull(s);
        assertTrue(!s.isEmpty());

        s = RandomFirstLastName.getFirstName(GENDER.FEMALE);
        assertNotNull(s);
        assertTrue(!s.isEmpty());

        s = RandomFirstLastName.getFirstName(GENDER.ANY);
        assertNotNull(s);
        assertTrue(!s.isEmpty());

        s = RandomFirstLastName.getLastName();
        assertNotNull(s);
        assertTrue(!s.isEmpty());

    }

}

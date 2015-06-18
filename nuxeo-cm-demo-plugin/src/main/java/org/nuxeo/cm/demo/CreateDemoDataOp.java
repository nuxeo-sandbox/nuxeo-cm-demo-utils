/*
 * (C) Copyright ${year} Nuxeo SA (http://nuxeo.com/) and contributors.
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
 *     thibaud
 */

package org.nuxeo.cm.demo;

import java.io.IOException;

import org.nuxeo.ecm.automation.core.Constants;
import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.automation.core.annotations.Param;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentException;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.lifecycle.LifeCycleException;
import org.nuxeo.ecm.core.work.api.WorkManager;
import org.nuxeo.ecm.core.work.api.WorkManager.Scheduling;
import org.nuxeo.runtime.api.Framework;

/**
 * 
 */
@Operation(id = CreateDemoDataOp.ID, category = Constants.CAT_SERVICES, label = "InsuranceClaims: Create Demo Data", description = "")
public class CreateDemoDataOp {
    
    public static final String ID = "CreateDemoDataOp";

    @Context
    protected CoreSession session;

    @Param(name = "parentDoc", required = true)
    protected DocumentModel parentDoc;

    @Param(name = "howMany", required = false)
    protected long howMany = 0;

    @Param(name = "commitModulo", required = false)
    protected long commitModulo = 0;

    @Param(name = "logModulo", required = false)
    protected long logModulo = 0;

    @OperationMethod
    public void run() throws IOException, DocumentException, LifeCycleException {

        WorkManager wm = Framework.getService(WorkManager.class);
        CreateDataDemoWork theWork = new CreateDataDemoWork(parentDoc);
        theWork.setCommitModulo((int) commitModulo);
        theWork.setLogModulo((int) logModulo);
        theWork.setHowMany((int) howMany);
        wm.schedule(theWork, Scheduling.IF_NOT_RUNNING_OR_SCHEDULED);
        
    }

}

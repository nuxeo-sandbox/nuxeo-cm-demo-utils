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

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.automation.core.Constants;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.automation.core.annotations.Param;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.work.api.WorkManager;
import org.nuxeo.ecm.core.work.api.WorkManager.Scheduling;
import org.nuxeo.runtime.api.Framework;

/**
 * 
 */
@Operation(id = CreateDemoDataOp.ID, category = Constants.CAT_SERVICES, label = "InsuranceClaims: Create Demo Data", description = "Values for deletePreviousClaimsStr: true or false")
public class CreateDemoDataOp {
    
    public static final String ID = "CreateDemoDataOp";

    private static final Log log = LogFactory.getLog(CreateDemoDataOp.class);

    @Param(name = "parentDoc", required = true)
    protected DocumentModel parentDoc;

    @Param(name = "deletePreviousClaimsStr", required = false, values = { "true" })
    protected String deletePreviousClaimsStr;

    @Param(name = "howMany", required = false, values = { "100000"})
    protected long howMany = 0;

    @Param(name = "commitModulo", required = false, values = { "50"})
    protected long commitModulo = 0;

    @Param(name = "logModulo", required = false)
    protected long logModulo = 0;

    @Param(name = "yieldToBgWorkModulo", required = false)
    protected long yieldToBgWorkModulo = 0;

    @Param(name = "sleepDurationAfterCommit", required = false)
    protected long sleepDurationAfterCommit = 0;

    @Param(name = "sleepModulo", required = false)
    protected long sleepModulo = 0;

    @Param(name = "sleepDurationMs", required = false)
    protected long sleepDurationMs = 0;

    @OperationMethod
    public void run() throws IOException {
        
        boolean deletePrevious;
        if(StringUtils.isBlank(deletePreviousClaimsStr)) {
            deletePrevious = CreateDemoData.DEFAULT_DELETE_PREVIOUS_CLAIMS;
        } else {
            deletePrevious = deletePreviousClaimsStr.equals("true");
        }
        
        CreateDataDemoWork theWork = CreateDataDemoWork.getInstance();
        if(theWork != null) {
            log.warn("There already is a Create Demo Data worker running. We don't create a new one");
        } else {
            
            WorkManager wm = Framework.getService(WorkManager.class);
            
            theWork = CreateDataDemoWork.getInstance(parentDoc);
            theWork.setDeletePreviousClaims(deletePrevious);
            theWork.setHowMany((int) howMany);
            theWork.setCommitModulo((int) commitModulo);
            theWork.setLogModulo((int) logModulo);
            theWork.setYieldToBgWorkModulo((int) yieldToBgWorkModulo);
            theWork.setSleepDurationAfterCommit((int) sleepDurationAfterCommit);
            theWork.setSleepModulo((int) sleepModulo);
            theWork.setSleepDurationMs((int) sleepDurationMs);
            
            wm.schedule(theWork, Scheduling.IF_NOT_RUNNING_OR_SCHEDULED);
        }
    }
}

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
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.DocumentException;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.impl.blob.StringBlob;
import org.nuxeo.ecm.core.lifecycle.LifeCycleException;
import org.nuxeo.ecm.core.work.api.WorkManager;
import org.nuxeo.ecm.core.work.api.WorkManager.Scheduling;
import org.nuxeo.runtime.api.Framework;

/**
 * 
 */
@Operation(id = CreateDemoDataInfoOp.ID, category = Constants.CAT_SERVICES, label = "InsuranceClaims: Create Demo Data Info", description = "Return a JSON blob giving info: .exists, .progress, .status and .countCreated .countToCreate. If the work is not running, returns just .exists")
public class CreateDemoDataInfoOp {

    public static final String ID = "CreateDemoDataInfoOp";

    @OperationMethod
    public Blob run() {

        String result = "";

        synchronized (CreateDataDemoWork.LOCK) {
            CreateDataDemoWork w = CreateDataDemoWork.getInstance();
            if (w == null) {
                result = "{\"exists\": false}";
            } else {
                result = "{\"exists\": true, \"progress\": "
                        + w.getProgressAsPercent() + "\"status\": \""
                        + w.getRunningStatus() + "\", \"countCreated\":"
                        + w.getCountOfCreated() + ", \"countToCreate\":"
                        + w.getHowMany() + "}";
            }
        }

        return new StringBlob(result, "application/json");

    }
}

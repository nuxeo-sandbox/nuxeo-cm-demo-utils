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
 *     Thibaud arguillere
 */
package org.nuxeo.cm.demo;

import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.nuxeo.ecm.core.work.api.WorkManager;
import org.nuxeo.runtime.api.Framework;

/**
 * @since 7.1
 */
public class MiscUtils {

    protected static WorkManager workManager;
    


    public static void waitForBackgroundWorkCompletion(int inMaxWorkers,
            int inTimeout) {
        waitForBackgroundWorkCompletion(inMaxWorkers, inTimeout);
    }

    public static void waitForBackgroundWorkCompletion(int inMaxWorkers,
            int inTimeout, String inDoNotCountThisCategory) {
        
        if(inTimeout < 0) {
            throw new IllegalArgumentException("The timeout can not be less or equal to zero (received: " + inTimeout + ")");
        }
        
        if(inMaxWorkers < 1) {
            return;
        }

        if (workManager == null) {
            workManager = Framework.getLocalService(WorkManager.class);
        }
        
        String ignoreQueueId = "";
        if(StringUtils.isNotBlank(inDoNotCountThisCategory)) {
            ignoreQueueId = workManager.getCategoryQueueId(inDoNotCountThisCategory);
        }
        
        int count;
        List<String> queueIds;
        long startTime = System.currentTimeMillis();
        do {
            count = 0;
            queueIds = workManager.getWorkQueueIds();
            for (String oneQueue : queueIds) {
                if(!oneQueue.equals(ignoreQueueId)) {
                    count += workManager.getQueueSize(oneQueue, null);
                }
            }

            if (count > inMaxWorkers) {
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    // Ignore
                }
            }

        } while (count > inMaxWorkers
                && (System.currentTimeMillis() - startTime) < inTimeout);

    }

}

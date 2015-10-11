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
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.work.api.WorkManager;
import org.nuxeo.runtime.api.Framework;

/**
 * @since 7.1
 */
public class MiscUtils {

    private static final Log log = LogFactory.getLog(MiscUtils.class);

    protected static WorkManager workManager;

    public static void waitForBackgroundWorkCompletion(int inMaxWorkers,
            int inTimeout) {
        waitForBackgroundWorkCompletion(inMaxWorkers, inTimeout);
    }

    /*
     * A timeout <= 0 means
     * "wait indefinitely until we have inMaxWorkers workers"
     */
    /**
     * Repetitively count the background workers until:
     * <p>
     * - There are inMaxWorkers or less
     * <p>
     * - Or the code is running since more than inTimeoutMs millisceonds
     * 
     * If inTimeoutMs is <= 0, no timeout applies, the code just runs until
     * there are max inMaxWorkers workers.
     * 
     * @param inMaxWorkers
     * @param inTimeout
     * @param inDoNotCountThisCategory
     *
     * @since 7.2
     */
    public static void waitForBackgroundWorkCompletion(int inMaxWorkers,
            int inTimeoutMs, String inDoNotCountThisCategory) {

        if (inMaxWorkers < 1) {
            return;
        }

        if (workManager == null) {
            workManager = Framework.getLocalService(WorkManager.class);
        }

        String ignoreQueueId = "";
        if (StringUtils.isNotBlank(inDoNotCountThisCategory)) {
            ignoreQueueId = workManager.getCategoryQueueId(inDoNotCountThisCategory);
        }

        int count;
        boolean timedOut = false;
        List<String> queueIds;
        long startTime = System.currentTimeMillis();
        do {
            count = 0;
            queueIds = workManager.getWorkQueueIds();
            for (String oneQueue : queueIds) {
                if (!oneQueue.equals(ignoreQueueId)) {
                    count += workManager.getQueueSize(oneQueue, null);
                }
            }

            if (count > inMaxWorkers) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    // Ignore
                }
            }

            timedOut = inTimeoutMs > 0
                    && (System.currentTimeMillis() - startTime) > inTimeoutMs;
            if (timedOut) {
                log.warn("Time out ("
                        + inTimeoutMs
                        + ") reached: Leaving waitForBackgroundWorkCompletion with "
                        + count + " active bg works");
            }

        } while (count > inMaxWorkers && !timedOut);

    }

    public static String millisecondsToToTimeFormat(long inMs) {

        long second = (inMs / 1000) % 60;
        long minute = (inMs / (1000 * 60)) % 60;
        long hour = (inMs / (1000 * 60 * 60)) % 24;

        return String.format("%d:%02d:%02d", hour, minute, second);

    }

}

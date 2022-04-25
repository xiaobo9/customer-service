/*
 * Copyright 2022 xiaobo9 <https://github.com/xiaobo9>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.chatopera.cc.basic;

import com.github.xiaobo9.bean.JobTask;
import org.junit.Assert;
import org.junit.Test;

public class MainUtilsTest {

    @Test
    public void test_convertCrond() {
        JobTask task = new JobTask();
        task.setRunCycle("day");
        task.setIsRepeat(true);
        task.setRunBeginSecond(59);
        task.setRunBeginDate(29);
        task.setRunBeginMinute(59);
        task.setRunBeginHour(23);
        task.setRunDates(new String[]{"1", "7"});
        task.setRepeatSpace(20);
        Assert.assertEquals("59 59/20 23 *  * ?", MainUtils.convertCrond(task));

        task.setRunCycle("week");
        Assert.assertEquals("59 59/20 23 ? * 1,7", MainUtils.convertCrond(task));

        task.setRunCycle("month");
        Assert.assertEquals("59 59/20 23 29 1,7  ?", MainUtils.convertCrond(task));

        task.setRunBeginHour(12);
        task.setRepeatSpace(80);
        Assert.assertEquals("59 59 12/1 29 1,7  ?", MainUtils.convertCrond(task));

        task.setRepeatSpace(20);
        task.setRepeatJustTime(4);
        task.setRunCycle("day");
        Assert.assertEquals("59 59/20 12-16 *  * ?", MainUtils.convertCrond(task));
    }

}
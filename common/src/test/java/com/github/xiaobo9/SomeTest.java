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

package com.github.xiaobo9;

import com.github.xiaobo9.commons.TestAnnotation;
import com.github.xiaobo9.commons.exception.CacheEx;
import com.github.xiaobo9.commons.exception.EntityNotFoundEx;
import org.junit.Test;

public class SomeTest {
    @Test
    public void test() {

        CacheEx a = new CacheEx("a");
        TestAnnotation annotation = a.getClass().getAnnotation(TestAnnotation.class);
        System.out.println(annotation);

        System.out.println(a.getClass().getPackage().getAnnotation(TestAnnotation.class));

        EntityNotFoundEx entityNotFoundEx = new EntityNotFoundEx();
        System.out.println(entityNotFoundEx.getClass().getAnnotation(TestAnnotation.class));
    }
}

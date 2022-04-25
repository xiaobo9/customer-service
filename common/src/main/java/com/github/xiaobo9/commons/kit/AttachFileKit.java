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
package com.github.xiaobo9.commons.kit;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.Date;

public class AttachFileKit {

    public static final String HEADER_KEY = "content-disposition";

    public static String xlsWithDayAnd(String key) {
        return nameWithDayAnd(key, ".xls");
    }

    public static String nameWithDayAnd(String key, String suffix) {
        String format = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
        return "attachment;filename=" + key + "-" + format + suffix;
    }

    public static String nameEncode(String name) throws UnsupportedEncodingException {
        return "attachment;filename=" + URLEncoder.encode(name, "UTF-8");
    }
}

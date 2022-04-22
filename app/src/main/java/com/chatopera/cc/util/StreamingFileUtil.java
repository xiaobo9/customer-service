/*
 * Copyright (C) 2018-2019 Chatopera Inc, <https://www.chatopera.com>
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

package com.chatopera.cc.util;

import com.chatopera.cc.basic.Constants;

import java.util.*;

public class StreamingFileUtil {

    private static final HashMap<String, String> extMap = new HashMap<>();
    private static final Map<String, Set<String>> extensionsMap = new HashMap<>();

    static {
        extMap.put(Constants.ATTACHMENT_TYPE_IMAGE, "gif,jpg,jpeg,png,bmp");
        extMap.put(Constants.ATTACHMENT_TYPE_FILE, "pdf,doc,docx,xls,xlsx,ppt,htm,html,txt,zip,rar,gz,bz2,c66");
        extMap.put("flash", "swf,flv");
        extMap.put("media", "swf,flv,mp3,wav,wma,wmv,mid,avi,mpg,asf,rm,rmvb");

        extMap.forEach((key, value) -> extensionsMap.put(key, new HashSet<>(Arrays.asList(value.split(",")))));
    }

    private StreamingFileUtil() {
    }

    /**
     * Validate file format
     *
     * @param type     type
     * @param filename file name
     * @return message
     */
    public static String validate(final String type, final String filename) {
        final String ext = filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
        Set<String> extensions = extensionsMap.get(type);
        if (extensions.contains(ext)) {
            return null;
        }
        return "上传文件扩展名是不允许的扩展名。只允许" + extMap.get(type) + "格式。";
    }

}

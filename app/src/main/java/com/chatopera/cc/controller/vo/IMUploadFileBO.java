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

package com.chatopera.cc.controller.vo;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;

@Getter
@Setter
@ToString
@Accessors(chain = true)
public class IMUploadFileBO {
    private String id;
    private String name;
    private long size;
    private String url;

    private String userId;

    private String userName;

    IMUploadFileBO(String id, String name, long size, String url, String userId) {
        this.id = id;
        this.name = name;
        this.size = size;
        this.url = url;
        this.userId = userId;
    }

    public static IMUploadFileBO of(String id, String name, long size, String url, String userId) {
        return new IMUploadFileBO(id, name, size, url, userId);
    }
}

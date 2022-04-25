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


package com.github.xiaobo9.entity;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.IdClass;
import javax.persistence.Table;
import java.util.Date;

/**
 * @Author Hai Liang Wang
 * 2019-10-10
 * 部门与用户关联表
 * 支持一个用户存在于多个部门中
 * IdClass: https://www.objectdb.com/java/jpa/entity/id#Entity_Identification, https://stackoverflow.com/questions/19813372/using-id-for-multiple-fields-in-same-class
 */
@Entity
@Table(name = "cs_organ_user")
@org.hibernate.annotations.Proxy(lazy = false)
@IdClass(OrganUserId.class)
public class OrganUser implements java.io.Serializable {

    @Id private String userid;     // 用户标识
    @Id private String organ;      // 部门标识
    private Date createtime;   // 创建时间
    private String creator;    // 创建人
    private Date updatetime;   // 更新时间
    private String orgi;       // 租户ID

    public String getUserid() {
        return userid;
    }

    public void setUserid(String userid) {
        this.userid = userid;
    }

    public String getOrgan() {
        return organ;
    }

    public void setOrgan(String organ) {
        this.organ = organ;
    }

    public Date getCreatetime() {
        return createtime;
    }

    public void setCreatetime(Date createtime) {
        this.createtime = createtime;
    }

    public String getCreator() {
        return creator;
    }

    public void setCreator(String creator) {
        this.creator = creator;
    }

    public Date getUpdatetime() {
        return updatetime;
    }

    public void setUpdatetime(Date updatetime) {
        this.updatetime = updatetime;
    }

    public String getOrgi() {
        return orgi;
    }

    public void setOrgi(String orgi) {
        this.orgi = orgi;
    }
}

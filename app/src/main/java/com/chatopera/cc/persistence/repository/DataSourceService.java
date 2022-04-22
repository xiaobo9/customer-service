/*
 * Copyright (C) 2017 优客服-多渠道客服系统
 * Modifications copyright (C) 2018-2019 Chatopera Inc, <https://www.chatopera.com>
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
package com.chatopera.cc.persistence.repository;

import com.alibaba.druid.pool.DruidDataSource;
import mondrian.olap.Connection;
import mondrian.olap.DriverManager;
import mondrian.olap.Util;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.sql.SQLException;


@Service
public class DataSourceService {
    @Autowired
    private DruidDataSource dataSource;

    public Connection service(String xml) throws SQLException, ClassNotFoundException {
        Connection dataSourceObject = null;
        StringBuilder strb = new StringBuilder();
        Util.PropertyList properties = Util.parseConnectString(strb.append("Provider=mondrian;")
                .append("Catalog=").append(xml).append(";").toString());
        if (properties != null) {
            dataSourceObject = DriverManager.getConnection(properties, null, dataSource);
        }
        return dataSourceObject;


//        String url = "jdbc:mondrian:Jdbc=jdbc:odbc:MondrianFoodMart; Catalog=file:/mondrian/demo/FoodMart.xml; JdbcDrivers=sun.jdbc.odbc.JdbcOdbcDriver";

//        Class.forName("mondrian.olap4j.MondrianOlap4jDriver");
//        StringBuilder urlBuilder = new StringBuilder()
//                .append("jdbc:mondrian:Jdbc=").append(dataSource.getUrl())
//                .append(";Catalog=").append(xml);
//
//        Connection connection = DriverManager.getConnection(urlBuilder.toString(), dataSource.getUsername(), dataSource.getPassword());
//        return connection.unwrap(OlapConnection.class);

    }
}

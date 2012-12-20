/*
 * Copyright (C) 2010-2012 LShift Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.lshift.diffa.sql;

import com.jolbox.bonecp.BoneCPConfig;
import com.jolbox.bonecp.BoneCPDataSource;
import org.apache.commons.lang3.RandomStringUtils;
import org.jooq.SQLDialect;
import org.jooq.impl.Factory;

import javax.sql.DataSource;
import java.sql.SQLException;

/**
 */
public abstract class TestSchema {
  private DataSource dataSource = null;

  public abstract SQLDialect dialect();

  public void create() throws SQLException {}

  public void migrate() throws SQLException {
    BoneCPConfig config = new BoneCPConfig();
    config.setJdbcUrl(getJdbcUrl());
    config.setUsername(dbUsername());
    config.setPassword(dbPassword());

    BoneCPDataSource ds = new BoneCPDataSource(config);
    ds.setDriverClass(driverClass());

    Factory factory = new Factory(ds.getConnection(), dialect());
    factory.execute(tableOfThingsDDL());
    factory.execute(md5FunctionDDL());

    dataSource = ds;
  }

  public DataSource getDataSource() {
    return dataSource;
  }

  protected abstract String driverClass();
  protected abstract String getJdbcUrl();

  protected abstract String tableOfThingsDDL();
  protected abstract String md5FunctionDDL();

  private String username = "SQLDRV" + RandomStringUtils.randomAlphabetic(5);
  protected String dbUsername() {
    return username;
  }
  protected abstract String dbPassword();
}

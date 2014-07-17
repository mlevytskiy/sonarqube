/*
 * SonarQube, open source software quality management tool.
 * Copyright (C) 2008-2014 SonarSource
 * mailto:contact AT sonarsource DOT com
 *
 * SonarQube is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * SonarQube is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.server.db.migrations;

import org.sonar.core.persistence.Database;

import java.sql.Connection;
import java.sql.SQLException;

public interface DataChange {

  class Context {
    private final Database db;
    private final Connection readConnection, writeConnection;

    public Context(Database db, Connection readConnection, Connection writeConnection) {
      this.db = db;
      this.readConnection = readConnection;
      this.writeConnection = writeConnection;
    }

    public Select prepareSelect(String sql) throws SQLException {
      return SelectImpl.create(db, readConnection, sql);
    }

    public Upsert prepareUpsert(String sql) throws SQLException {
      return UpsertImpl.create(writeConnection, sql);
    }

    public MassUpdate prepareMassUpdate() throws SQLException {
      return new MassUpdate(db, writeConnection);
    }
  }

  void execute(Context context) throws SQLException;
}

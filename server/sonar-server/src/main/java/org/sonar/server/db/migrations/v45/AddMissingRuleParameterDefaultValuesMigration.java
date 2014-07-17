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
package org.sonar.server.db.migrations.v45;

import org.sonar.api.utils.System2;
import org.sonar.core.persistence.Database;
import org.sonar.server.db.migrations.BaseDataChange;
import org.sonar.server.db.migrations.Select;
import org.sonar.server.db.migrations.Upsert;

import java.sql.SQLException;
import java.util.Date;
import java.util.List;

/**
 * SONAR-5446
 */
public class AddMissingRuleParameterDefaultValuesMigration extends BaseDataChange {

  private final System2 system;

  public AddMissingRuleParameterDefaultValuesMigration(Database db, System2 system) {
    super(db);
    this.system = system;
  }

  @Override
  public void execute(Context context) {
    try {
      // get all the parameters with default value
      List<RuleParam> ruleParameters = context.prepareSelect("select id,rule_id,name,default_value from rules_parameters where default_value is not null")
        .list(new Select.RowReader<RuleParam>() {
          @Override
          public RuleParam read(Select.Row row) throws SQLException {
            return new RuleParam(row.getLong(1), row.getLong(2), row.getString(3), row.getString(4));
          }
        });

      for (RuleParam ruleParameter : ruleParameters) {
        List<ActiveRule> activeRules = context.prepareSelect("select ar.id, ar.profile_id from active_rules ar " +
          "left outer join active_rule_parameters arp on arp.active_rule_id=ar.id and arp.rules_parameter_id=? " +
          "where ar.rule_id=? and arp.id is null")
          .setLong(1, ruleParameter.id)
          .setLong(2, ruleParameter.ruleId)
          .list(new Select.RowReader<ActiveRule>() {
            @Override
            public ActiveRule read(Select.Row row) throws SQLException {
              return new ActiveRule(row.getLong(1), row.getLong(2));
            }
          });

        Upsert upsert = context.prepareUpsert("insert into active_rule_parameters(active_rule_id, rules_parameter_id, value, rules_parameter_key) values (?, ?, ?, ?)");
        for (ActiveRule activeRule : activeRules) {
          upsert
            .setLong(1, activeRule.id)
            .setLong(2, ruleParameter.id)
            .setString(3, ruleParameter.defaultValue)
            .setString(4, ruleParameter.name)
            .addBatch();
        }
        upsert.execute().commit().close();

        // update date for ES indexation
        upsert = context.prepareUpsert("update active_rules set updated_at=? where id=?");
        Date now = new Date(system.now());
        for (ActiveRule activeRule : activeRules) {
          upsert
            .setDate(1, now)
            .setLong(2, activeRule.id)
            .addBatch();
        }
        upsert.execute().commit().close();

      }
    } catch (SQLException e) {
      e.printStackTrace();
    }
  }

  private static class RuleParam {
    final long id, ruleId;
    final String defaultValue, name;

    RuleParam(long id, long ruleId, String name, String defaultValue) {
      this.id = id;
      this.ruleId = ruleId;
      this.name = name;
      this.defaultValue = defaultValue;
    }
  }

  private static class ActiveRule {
    final long id, profileId;

    ActiveRule(long id, long profileId) {
      this.id = id;
      this.profileId = profileId;
    }
  }
}

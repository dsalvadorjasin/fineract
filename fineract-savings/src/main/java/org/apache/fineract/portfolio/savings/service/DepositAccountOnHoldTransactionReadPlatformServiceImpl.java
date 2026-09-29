/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.fineract.portfolio.savings.service;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.data.EnumOptionData;
import org.apache.fineract.infrastructure.core.domain.JdbcSupport;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.service.Page;
import org.apache.fineract.infrastructure.core.service.PaginationHelper;
import org.apache.fineract.infrastructure.core.service.SearchParameters;
import org.apache.fineract.infrastructure.core.service.database.DatabaseSpecificSQLGenerator;
import org.apache.fineract.portfolio.savings.data.DepositAccountOnHoldTransactionData;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

@RequiredArgsConstructor
public class DepositAccountOnHoldTransactionReadPlatformServiceImpl implements DepositAccountOnHoldTransactionReadPlatformService {

    private static final Set<String> ALLOWED_SORT_ORDERS = Set.of("ASC", "DESC");
    private static final Map<String, String> ORDER_BY_COLUMNS = buildOrderByColumns();

    private final JdbcTemplate jdbcTemplate;
    private final DatabaseSpecificSQLGenerator sqlGenerator;
    private final PaginationHelper paginationHelper;
    private final DepositAccountOnHoldTransactionsMapper mapper = new DepositAccountOnHoldTransactionsMapper();

    @Override
    public Page<DepositAccountOnHoldTransactionData> retriveAll(Long savingsId, Long guarantorFundingId,
            SearchParameters searchParameters) {
        final StringBuilder sqlBuilder = new StringBuilder(200);
        List<Long> paramObj = new ArrayList<>(2);
        sqlBuilder.append("select " + sqlGenerator.calcFoundRows() + " ");
        sqlBuilder.append(this.mapper.schema());

        sqlBuilder.append(" where tr.savings_account_id = ? ");
        paramObj.add(savingsId);
        if (guarantorFundingId != null) {
            sqlBuilder.append(" and gt.guarantor_fund_detail_id = ? ");
            paramObj.add(guarantorFundingId);
        }

        if (searchParameters.hasOrderBy()) {
            sqlBuilder.append(" order by ").append(resolveOrderByColumn(searchParameters.getOrderBy()));

            if (searchParameters.hasSortOrder()) {
                sqlBuilder.append(' ').append(resolveSortOrder(searchParameters.getSortOrder()));
            }
        }

        if (searchParameters.hasLimit()) {
            sqlBuilder.append(" ");
            if (searchParameters.hasOffset()) {
                sqlBuilder.append(sqlGenerator.limit(searchParameters.getLimit(), searchParameters.getOffset()));
            } else {
                sqlBuilder.append(sqlGenerator.limit(searchParameters.getLimit()));
            }
        }

        final Object[] finalObjectArray = paramObj.toArray();
        return this.paginationHelper.fetchPage(this.jdbcTemplate, sqlBuilder.toString(), finalObjectArray, this.mapper);

    }

    private static Map<String, String> buildOrderByColumns() {
        final Map<String, String> columns = new HashMap<>();
        addOrderByColumn(columns, "tr.id", "id", "transactionid");
        addOrderByColumn(columns, "tr.transaction_date", "transaction_date", "transactiondate");
        addOrderByColumn(columns, "tr.transaction_type_enum", "transaction_type_enum", "transactiontype");
        addOrderByColumn(columns, "tr.amount", "amount", "transactionamount");
        addOrderByColumn(columns, "tr.is_reversed", "is_reversed", "reversed");
        addOrderByColumn(columns, "tr.created_date", "created_date");
        return Map.copyOf(columns);
    }

    private static void addOrderByColumn(final Map<String, String> columns, final String column, final String... aliases) {
        columns.put(column, column);
        for (String alias : aliases) {
            columns.put(alias, column);
        }
    }

    static String resolveOrderByColumn(final String orderBy) {
        final String column = ORDER_BY_COLUMNS.get(orderBy.trim().toLowerCase(Locale.ROOT));
        if (column == null) {
            throw new PlatformApiDataValidationException("validation.msg.invalid.orderBy", "Invalid orderBy value: " + orderBy, "orderBy",
                    orderBy);
        }
        return column;
    }

    static String resolveSortOrder(final String sortOrder) {
        final String direction = sortOrder.trim().toUpperCase(Locale.ROOT);
        if (!ALLOWED_SORT_ORDERS.contains(direction)) {
            throw new PlatformApiDataValidationException("validation.msg.invalid.sortOrder", "Invalid sortOrder value: " + sortOrder,
                    "sortOrder", sortOrder);
        }
        return direction;
    }

    private static final class DepositAccountOnHoldTransactionsMapper implements RowMapper<DepositAccountOnHoldTransactionData> {

        private final String schemaSql;

        DepositAccountOnHoldTransactionsMapper() {

            final StringBuilder sqlBuilder = new StringBuilder(400);
            sqlBuilder.append(" tr.id as transactionId, tr.transaction_type_enum as transactionType, ");
            sqlBuilder.append(" tr.transaction_date as transactionDate, tr.amount as transactionAmount,");
            sqlBuilder.append(" tr.is_reversed as reversed, sa.account_no as savingsAccNum, ");
            sqlBuilder.append(" COALESCE(sc.display_name, sg.display_name) as savingsClientName, ml.id as loanid, sa.id as savingid, ");
            sqlBuilder.append(" ml.account_no as loanAccountNum, COALESCE(lc.display_name, lg.display_name) as loanClientName");
            sqlBuilder.append(" from m_savings_account sa  ");
            sqlBuilder.append(" join m_deposit_account_on_hold_transaction tr on sa.id = tr.savings_account_id ");
            sqlBuilder.append(" left join m_client sc on sc.id = sa.client_id");
            sqlBuilder.append(" left join m_group sg on sg.id = sa.group_id");
            sqlBuilder.append(" left join m_guarantor_transaction gt on gt.deposit_on_hold_transaction_id = tr.id ");
            sqlBuilder.append(" left join m_guarantor_funding_details mgfd on mgfd.id=gt.guarantor_fund_detail_id");
            sqlBuilder.append(" left join m_portfolio_account_associations  pa on pa.id=mgfd.account_associations_id");
            sqlBuilder.append(" left join m_loan ml on ml.id = pa.loan_account_id");
            sqlBuilder.append(" left join m_client lc on lc.id = ml.client_id");
            sqlBuilder.append(" left join m_group lg on lg.id = ml.group_id");

            this.schemaSql = sqlBuilder.toString();
        }

        public String schema() {
            return this.schemaSql;
        }

        @Override
        public DepositAccountOnHoldTransactionData mapRow(final ResultSet rs, @SuppressWarnings("unused") final int rowNum)
                throws SQLException {
            final Long id = rs.getLong("transactionId");
            final int transactionTypeInt = JdbcSupport.getInteger(rs, "transactionType");
            final EnumOptionData transactionType = SavingsEnumerations.onHoldTransactionType(transactionTypeInt);

            final LocalDate date = JdbcSupport.getLocalDate(rs, "transactionDate");
            final BigDecimal amount = JdbcSupport.getBigDecimalDefaultToZeroIfNull(rs, "transactionAmount");
            final boolean reversed = rs.getBoolean("reversed");
            final String savingsAccountNum = rs.getString("savingsAccNum");
            final Long savingsId = rs.getLong("savingid");
            final String savingsClientName = rs.getString("savingsClientName");
            final String loanAccountNum = rs.getString("loanAccountNum");
            final Long loanId = rs.getLong("loanid");
            final String loanClientName = rs.getString("loanClientName");
            return DepositAccountOnHoldTransactionData.instance(id, amount, transactionType, date, reversed, savingsId, savingsAccountNum,
                    savingsClientName, loanId, loanAccountNum, loanClientName);
        }
    }

}

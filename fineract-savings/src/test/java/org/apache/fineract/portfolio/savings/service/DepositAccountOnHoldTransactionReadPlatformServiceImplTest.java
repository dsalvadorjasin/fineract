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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.service.PaginationHelper;
import org.apache.fineract.infrastructure.core.service.SearchParameters;
import org.apache.fineract.infrastructure.core.service.database.DatabaseSpecificSQLGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class DepositAccountOnHoldTransactionReadPlatformServiceImplTest {

    private PaginationHelper paginationHelper;
    private DepositAccountOnHoldTransactionReadPlatformServiceImpl service;

    @BeforeEach
    void setUp() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        DatabaseSpecificSQLGenerator sqlGenerator = mock(DatabaseSpecificSQLGenerator.class);
        when(sqlGenerator.calcFoundRows()).thenReturn("");
        paginationHelper = mock(PaginationHelper.class);
        service = new DepositAccountOnHoldTransactionReadPlatformServiceImpl(jdbcTemplate, sqlGenerator, paginationHelper);
    }

    @Test
    void allowlistedOrderByAndSortOrderAreMappedToFixedIdentifiers() {
        service.retriveAll(1L, null, SearchParameters.builder().orderBy("transactionDate").sortOrder("desc").build());

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(paginationHelper).fetchPage(any(JdbcTemplate.class), sql.capture(), any(Object[].class), any(RowMapper.class));
        assertTrue(sql.getValue().endsWith(" order by tr.transaction_date DESC"), sql.getValue());
    }

    @ParameterizedTest
    @ValueSource(strings = { "(select ascii(substr(password,1,1)) from m_appuser limit 1)", "tr.id, (select 1)", "unknown_column",
            "savingsClientName" })
    void rejectsOrderByNotInAllowlist(String orderBy) {
        assertThrows(PlatformApiDataValidationException.class,
                () -> service.retriveAll(1L, null, SearchParameters.builder().orderBy(orderBy).build()));
        verifyNoInteractions(paginationHelper);
    }

    @ParameterizedTest
    @ValueSource(strings = { "asc, (select 1)", "desc limit 1", "ascending", "(select 1)" })
    void rejectsSortOrderOtherThanAscOrDesc(String sortOrder) {
        assertThrows(PlatformApiDataValidationException.class,
                () -> service.retriveAll(1L, null, SearchParameters.builder().orderBy("id").sortOrder(sortOrder).build()));
        verifyNoInteractions(paginationHelper);
    }

    @Test
    void resolversNormalizeCaseAndWhitespace() {
        assertEquals("tr.amount", DepositAccountOnHoldTransactionReadPlatformServiceImpl.resolveOrderByColumn(" TR.AMOUNT "));
        assertEquals("ASC", DepositAccountOnHoldTransactionReadPlatformServiceImpl.resolveSortOrder(" asc "));
    }
}

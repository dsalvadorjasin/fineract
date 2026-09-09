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
package org.apache.fineract.integrationtests;

import static org.apache.http.HttpStatus.SC_FORBIDDEN;
import static org.apache.http.HttpStatus.SC_OK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.math.BigDecimal;
import org.apache.fineract.client.models.PostSavingsAccountTransactionsRequest;
import org.apache.fineract.client.models.PostSavingsAccountTransactionsResponse;
import org.apache.fineract.client.models.PostSavingsProductsResponse;
import org.apache.fineract.client.models.PutGlobalConfigurationsRequest;
import org.apache.fineract.client.models.SavingsAccountData;
import org.apache.fineract.client.util.Calls;
import org.apache.fineract.infrastructure.configuration.api.GlobalConfigurationConstants;
import org.apache.fineract.integrationtests.client.feign.FeignSavingsTestBase;
import org.apache.fineract.integrationtests.client.feign.modules.SavingsRequestBuilders;
import org.apache.fineract.integrationtests.client.feign.modules.SavingsTestData;
import org.apache.fineract.integrationtests.common.FineractClientHelper;
import org.apache.fineract.integrationtests.common.Utils;
import org.apache.fineract.integrationtests.common.error.ErrorResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import retrofit2.Response;

public class SavingsMaxSingleWithdrawalTest extends FeignSavingsTestBase {

    private static final long MAX_WITHDRAWAL_AMOUNT = 500L;
    private static final String WITHDRAWAL_ERROR_CODE = "error.msg.savingsaccount.transaction.withdrawal.exceeds.max.single.amount";

    @Test
    public void withdrawalsAboveTheConfiguredLimitAreRejected() {
        final String transactionDate = Utils.dateFormatter.format(Utils.getLocalDateOfTenant());
        globalConfigurationHelper.updateGlobalConfiguration(GlobalConfigurationConstants.MAX_SINGLE_WITHDRAWAL_AMOUNT_SAVINGS,
                new PutGlobalConfigurationsRequest().enabled(true).value(MAX_WITHDRAWAL_AMOUNT));

        final Long clientId = createClient(transactionDate);
        assertNotNull(clientId);
        final PostSavingsProductsResponse savingsProduct = createSavingsProduct(
                SavingsRequestBuilders.savingsProduct(SavingsTestData.InterestCompoundingPeriodType.DAILY,
                        SavingsTestData.InterestPostingPeriodType.DAILY, SavingsTestData.InterestCalculationType.DAILY_BALANCE));
        final Long savingsId = createApproveActivateSavings(clientId, savingsProduct.getResourceId(), transactionDate);
        deposit(savingsId, "10000", transactionDate);

        final Response<PostSavingsAccountTransactionsResponse> overLimit = withdrawal(savingsId,
                SavingsRequestBuilders.withdrawal("600", transactionDate));
        assertEquals(SC_FORBIDDEN, overLimit.code());
        assertEquals(WITHDRAWAL_ERROR_CODE, ErrorResponse.from(overLimit).getErrors().get(0).getUserMessageGlobalisationCode());

        final Response<PostSavingsAccountTransactionsResponse> atLimit = withdrawal(savingsId,
                SavingsRequestBuilders.withdrawal("500", transactionDate));
        assertEquals(SC_OK, atLimit.code());

        final Response<PostSavingsAccountTransactionsResponse> belowLimit = withdrawal(savingsId,
                SavingsRequestBuilders.withdrawal("100", transactionDate));
        assertEquals(SC_OK, belowLimit.code());

        final SavingsAccountData savings = getSavingsDetails(savingsId);
        assertEquals(0, savings.getSummary().getAccountBalance().compareTo(new BigDecimal("9400")));
        assertEquals(3, savings.getTransactions().size());

        globalConfigurationHelper.updateGlobalConfiguration(GlobalConfigurationConstants.MAX_SINGLE_WITHDRAWAL_AMOUNT_SAVINGS,
                new PutGlobalConfigurationsRequest().enabled(false).value(0L));
        final Response<PostSavingsAccountTransactionsResponse> disabled = withdrawal(savingsId,
                SavingsRequestBuilders.withdrawal("600", transactionDate));
        assertEquals(SC_OK, disabled.code());
    }

    private Response<PostSavingsAccountTransactionsResponse> withdrawal(final Long savingsId,
            final PostSavingsAccountTransactionsRequest request) {
        return Calls.executeU(FineractClientHelper.getFineractClient().savingsTransactions.createSavingsAccountTransaction(savingsId,
                request, "withdrawal"));
    }

    @AfterEach
    public void resetMaxSingleWithdrawalConfiguration() {
        globalConfigurationHelper.updateGlobalConfiguration(GlobalConfigurationConstants.MAX_SINGLE_WITHDRAWAL_AMOUNT_SAVINGS,
                new PutGlobalConfigurationsRequest().enabled(false).value(0L));
    }
}

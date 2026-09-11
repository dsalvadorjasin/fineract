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
package org.apache.fineract.infrastructure.security.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.Set;
import org.apache.fineract.infrastructure.core.config.FineractProperties;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.infrastructure.security.data.OidcFederatedIdentity;
import org.apache.fineract.infrastructure.security.exception.OidcIdentityBindingException;
import org.apache.fineract.infrastructure.security.exception.OidcUserNotFoundException;
import org.apache.fineract.organisation.office.domain.Office;
import org.apache.fineract.organisation.office.domain.OfficeRepository;
import org.apache.fineract.useradministration.domain.AppUser;
import org.apache.fineract.useradministration.domain.AppUserRepository;
import org.apache.fineract.useradministration.domain.RoleRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OidcAppUserResolutionServiceImplTest {

    private static final String ISSUER = "https://idp.example.com";
    private static final String SUBJECT = "b1f0-42";

    @Mock
    private AppUserRepository appUserRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private OfficeRepository officeRepository;
    @Mock
    private FineractProperties fineractProperties;
    @Mock
    private FineractProperties.FineractSecurityProperties securityProperties;
    @Mock
    private FineractProperties.FineractSecurityProperties.FineractSecurityOidcFederationProperties oidcProps;
    @Mock
    private FineractProperties.FineractDefaultValues defaultProps;
    @Mock
    private AppUser existingUser;
    @Mock
    private Office headOffice;
    @Mock
    private FineractPlatformTenant platformTenant;

    @InjectMocks
    private OidcAppUserResolutionServiceImpl service;

    @BeforeEach
    void setUp() {
        when(fineractProperties.getDefaults()).thenReturn(defaultProps);
        when(defaultProps.getOfficeId()).thenReturn(1L);
        when(fineractProperties.getSecurity()).thenReturn(securityProperties);
        when(securityProperties.getOidcFederation()).thenReturn(oidcProps);
        // Required by AppUser constructor via DateUtils.getLocalDateOfTenant()
        when(platformTenant.getTimezoneId()).thenReturn("UTC");
        ThreadLocalContextUtil.setTenant(platformTenant);
    }

    @AfterEach
    void tearDown() {
        ThreadLocalContextUtil.reset();
    }

    private OidcFederatedIdentity identity(String username, String email, boolean emailVerified) {
        return new OidcFederatedIdentity(ISSUER, SUBJECT, username, email, emailVerified, "Alice", "Smith", Set.of());
    }

    @Test
    void returnsUserBoundToIssuerAndSubject() {
        when(appUserRepository.findActiveUserByFederatedIdentity(ISSUER, SUBJECT)).thenReturn(existingUser);

        AppUser result = service.resolveOrCreate(identity("alice", "alice@example.com", true));

        assertThat(result).isSameAs(existingUser);
        verify(appUserRepository, never()).findAppUserByName(any());
        verify(appUserRepository, never()).findActiveUserByEmail(any());
    }

    @Test
    void neverResolvesExistingUserByUsernameClaim() {
        when(appUserRepository.findActiveUserByFederatedIdentity(ISSUER, SUBJECT)).thenReturn(null);
        when(oidcProps.isAutoCreateUser()).thenReturn(false);

        assertThatThrownBy(() -> service.resolveOrCreate(identity("mifos", null, false)))
                .isInstanceOf(OidcUserNotFoundException.class)
                .extracting(e -> ((OidcUserNotFoundException) e).getSubject())
                .isEqualTo(SUBJECT);

        verify(appUserRepository, never()).findAppUserByName(any());
    }

    @Test
    void neverResolvesExistingUserByUnverifiedEmailClaim() {
        when(appUserRepository.findActiveUserByFederatedIdentity(ISSUER, SUBJECT)).thenReturn(null);
        when(oidcProps.isLinkExistingUserByVerifiedEmail()).thenReturn(true);
        when(oidcProps.isAutoCreateUser()).thenReturn(false);

        assertThatThrownBy(() -> service.resolveOrCreate(identity("alice", "admin@example.com", false)))
                .isInstanceOf(OidcUserNotFoundException.class);

        verify(appUserRepository, never()).findActiveUserByEmail(any());
    }

    @Test
    void rejectsTokenWithoutIssuerOrSubject() {
        assertThatThrownBy(() -> service
                .resolveOrCreate(new OidcFederatedIdentity(null, SUBJECT, "alice", "alice@example.com", true, "Alice", "Smith", Set.of())))
                .isInstanceOf(OidcIdentityBindingException.class);

        assertThatThrownBy(() -> service
                .resolveOrCreate(new OidcFederatedIdentity(ISSUER, " ", "alice", "alice@example.com", true, "Alice", "Smith", Set.of())))
                .isInstanceOf(OidcIdentityBindingException.class);

        verify(appUserRepository, never()).findActiveUserByFederatedIdentity(any(), any());
    }

    @Test
    void linksUnboundUserByVerifiedEmailWhenEnabled() {
        when(appUserRepository.findActiveUserByFederatedIdentity(ISSUER, SUBJECT)).thenReturn(null);
        when(oidcProps.isLinkExistingUserByVerifiedEmail()).thenReturn(true);
        when(appUserRepository.findActiveUserByEmail("alice@example.com")).thenReturn(existingUser);
        when(existingUser.hasFederatedIdentity()).thenReturn(false);
        when(appUserRepository.saveAndFlush(existingUser)).thenReturn(existingUser);

        AppUser result = service.resolveOrCreate(identity("alice", "alice@example.com", true));

        assertThat(result).isSameAs(existingUser);
        verify(existingUser).linkFederatedIdentity(ISSUER, SUBJECT);
    }

    @Test
    void refusesToLinkPrivilegedUserByVerifiedEmail() {
        when(appUserRepository.findActiveUserByFederatedIdentity(ISSUER, SUBJECT)).thenReturn(null);
        when(oidcProps.isLinkExistingUserByVerifiedEmail()).thenReturn(true);
        when(appUserRepository.findActiveUserByEmail("admin@example.com")).thenReturn(existingUser);
        when(existingUser.hasAnyPermission("ALL_FUNCTIONS")).thenReturn(true);

        assertThatThrownBy(() -> service.resolveOrCreate(identity("alice", "admin@example.com", true)))
                .isInstanceOf(OidcIdentityBindingException.class);

        verify(existingUser, never()).linkFederatedIdentity(any(), any());
    }

    @Test
    void refusesToLinkUserAlreadyBoundToAnotherIdentity() {
        when(appUserRepository.findActiveUserByFederatedIdentity(ISSUER, SUBJECT)).thenReturn(null);
        when(oidcProps.isLinkExistingUserByVerifiedEmail()).thenReturn(true);
        when(appUserRepository.findActiveUserByEmail("alice@example.com")).thenReturn(existingUser);
        when(existingUser.hasFederatedIdentity()).thenReturn(true);

        assertThatThrownBy(() -> service.resolveOrCreate(identity("alice", "alice@example.com", true)))
                .isInstanceOf(OidcIdentityBindingException.class);

        verify(existingUser, never()).linkFederatedIdentity(any(), any());
    }

    @Test
    void throwsOidcUserNotFoundWhenUnboundAndAutoCreateDisabled() {
        when(appUserRepository.findActiveUserByFederatedIdentity(ISSUER, SUBJECT)).thenReturn(null);
        when(oidcProps.isAutoCreateUser()).thenReturn(false);

        assertThatThrownBy(() -> service.resolveOrCreate(identity("ghost", "ghost@example.com", true)))
                .isInstanceOf(OidcUserNotFoundException.class);

        verify(appUserRepository, never()).saveAndFlush(any());
    }

    @Test
    void autoCreatesUserBoundToIssuerAndSubject() {
        when(appUserRepository.findActiveUserByFederatedIdentity(ISSUER, SUBJECT)).thenReturn(null);
        when(appUserRepository.findAppUserByName("newuser")).thenReturn(null);
        when(oidcProps.isAutoCreateUser()).thenReturn(true);
        when(oidcProps.getDefaultRoles()).thenReturn("");
        when(officeRepository.findById(1L)).thenReturn(Optional.of(headOffice));

        AppUser savedUser = org.mockito.Mockito.mock(AppUser.class);
        when(appUserRepository.saveAndFlush(any(AppUser.class))).thenReturn(savedUser);

        AppUser result = service.resolveOrCreate(identity("newuser", "new@example.com", true));

        assertThat(result).isSameAs(savedUser);
        verify(appUserRepository).saveAndFlush(any(AppUser.class));
    }

    @Test
    void refusesToAutoCreateWhenUsernameIsTakenByLocalAccount() {
        when(appUserRepository.findActiveUserByFederatedIdentity(ISSUER, SUBJECT)).thenReturn(null);
        when(appUserRepository.findAppUserByName("mifos")).thenReturn(existingUser);
        when(oidcProps.isAutoCreateUser()).thenReturn(true);

        assertThatThrownBy(() -> service.resolveOrCreate(identity("mifos", "attacker@example.com", true)))
                .isInstanceOf(OidcIdentityBindingException.class);

        verify(appUserRepository, never()).saveAndFlush(any());
    }

    @Test
    void throwsWhenHeadOfficeNotFoundDuringAutoCreate() {
        when(appUserRepository.findActiveUserByFederatedIdentity(ISSUER, SUBJECT)).thenReturn(null);
        when(appUserRepository.findAppUserByName("newuser")).thenReturn(null);
        when(oidcProps.isAutoCreateUser()).thenReturn(true);
        when(officeRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolveOrCreate(identity("newuser", "new@example.com", true)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Head office");
    }
}

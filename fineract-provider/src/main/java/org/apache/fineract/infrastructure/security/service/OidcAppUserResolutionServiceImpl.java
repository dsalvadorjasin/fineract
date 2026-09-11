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

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.core.config.FineractProperties;
import org.apache.fineract.infrastructure.security.data.OidcFederatedIdentity;
import org.apache.fineract.infrastructure.security.exception.OidcIdentityBindingException;
import org.apache.fineract.infrastructure.security.exception.OidcUserNotFoundException;
import org.apache.fineract.organisation.office.domain.Office;
import org.apache.fineract.organisation.office.domain.OfficeRepository;
import org.apache.fineract.useradministration.domain.AppUser;
import org.apache.fineract.useradministration.domain.AppUserRepository;
import org.apache.fineract.useradministration.domain.Role;
import org.apache.fineract.useradministration.domain.RoleRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class OidcAppUserResolutionServiceImpl implements OidcAppUserResolutionService {

    private final AppUserRepository appUserRepository;
    private final RoleRepository roleRepository;
    private final OfficeRepository officeRepository;
    private final FineractProperties fineractProperties;

    // Stateless encoder — safe to create once per class
    private static final PasswordEncoder PASSWORD_ENCODER = PasswordEncoderFactories.createDelegatingPasswordEncoder();

    @Override
    @Transactional
    public AppUser resolveOrCreate(OidcFederatedIdentity identity) {

        final String issuer = identity.issuer();
        final String subject = identity.subject();
        if (StringUtils.isBlank(issuer) || StringUtils.isBlank(subject)) {
            throw new OidcIdentityBindingException("OIDC token carries no usable issuer/subject pair");
        }

        // 1. The only identity key trusted for account selection: issuer-scoped subject
        AppUser user = appUserRepository.findActiveUserByFederatedIdentity(issuer, subject);
        if (user != null) {
            log.debug("OIDC user resolved by federated identity (issuer '{}', subject '{}')", issuer, subject);
            return user;
        }

        FineractProperties.FineractSecurityProperties.FineractSecurityOidcFederationProperties oidcConfig = fineractProperties.getSecurity()
                .getOidcFederation();

        // 2. Optional one-time binding of an unbound account via a verified email claim
        if (oidcConfig.isLinkExistingUserByVerifiedEmail()) {
            user = linkByVerifiedEmail(identity);
            if (user != null) {
                return user;
            }
        }

        // 3. Auto-create when enabled
        String username = identity.username();
        if (!oidcConfig.isAutoCreateUser()) {
            log.warn("No Fineract user is bound to OIDC subject '{}' of issuer '{}' and auto-create is disabled", subject, issuer);
            throw new OidcUserNotFoundException(subject);
        }

        if (StringUtils.isBlank(username)) {
            throw new OidcIdentityBindingException("OIDC token carries no username claim to create a Fineract user from");
        }
        if (appUserRepository.findAppUserByName(username) != null) {
            // Reusing the account would let a claim value pick an arbitrary local account
            log.warn("Refusing to bind OIDC subject '{}' of issuer '{}' to pre-existing Fineract user '{}'", subject, issuer, username);
            throw new OidcIdentityBindingException("Fineract username claimed by the OIDC token is already taken by another account");
        }

        log.info("Auto-creating Fineract user for OIDC subject '{}' of issuer '{}'", subject, issuer);
        return createUser(identity, oidcConfig);
    }

    private AppUser linkByVerifiedEmail(OidcFederatedIdentity identity) {
        if (!identity.emailVerified() || StringUtils.isBlank(identity.email())) {
            return null;
        }

        AppUser candidate = appUserRepository.findActiveUserByEmail(identity.email());
        if (candidate == null) {
            return null;
        }
        if (candidate.hasFederatedIdentity()) {
            log.warn("Refusing to link OIDC subject '{}' of issuer '{}' to user '{}' already bound to another federated identity",
                    identity.subject(), identity.issuer(), candidate.getUsername());
            throw new OidcIdentityBindingException("Fineract user matching the verified email is bound to another federated identity");
        }
        if (candidate.isSystemUser() || candidate.hasAnyPermission("ALL_FUNCTIONS")) {
            log.warn("Refusing to link OIDC subject '{}' of issuer '{}' to privileged user '{}'", identity.subject(), identity.issuer(),
                    candidate.getUsername());
            throw new OidcIdentityBindingException("Privileged Fineract accounts cannot be linked from a federated identity");
        }

        candidate.linkFederatedIdentity(identity.issuer(), identity.subject());
        log.info("Linked OIDC subject '{}' of issuer '{}' to existing Fineract user '{}' by verified email", identity.subject(),
                identity.issuer(), candidate.getUsername());
        return appUserRepository.saveAndFlush(candidate);
    }

    private AppUser createUser(OidcFederatedIdentity identity,
            FineractProperties.FineractSecurityProperties.FineractSecurityOidcFederationProperties oidcConfig) {

        final String username = identity.username();
        final Office headOffice = officeRepository.findById(fineractProperties.getDefaults().getOfficeId())
                .orElseThrow(() -> new IllegalStateException("Head office (id=1) not found — cannot auto-create OIDC user"));

        String encodedPassword = PASSWORD_ENCODER.encode(new RandomPasswordGenerator(20).generate());

        User springUser = new User(username, encodedPassword, true, true, true, true, List.of(new SimpleGrantedAuthority("ROLE_USER")));

        Set<Role> roles = resolveRoles(oidcConfig.getDefaultRoles(), identity.requestedRoles());

        String resolvedEmail = identity.email() != null ? identity.email() : username + "@oidc.placeholder";
        String resolvedFirstName = identity.firstName() != null ? identity.firstName() : username;
        String resolvedLastName = identity.lastName() != null ? identity.lastName() : "";

        AppUser appUser = new AppUser(headOffice, springUser, roles, resolvedEmail, resolvedFirstName, resolvedLastName, null, true, false);
        appUser.linkFederatedIdentity(identity.issuer(), identity.subject());

        AppUser saved = appUserRepository.saveAndFlush(appUser);
        log.info("Auto-created Fineract user '{}' (id={}) from OIDC identity", username, saved.getId());
        return saved;
    }

    private Set<Role> resolveRoles(String defaultRolesConfig, Set<String> requestedRoles) {
        Set<Role> result = new HashSet<>();
        Stream.concat(Arrays.stream(defaultRolesConfig.split(",")), requestedRoles.stream()).map(String::trim)
                .filter(name -> !name.isEmpty()).forEach(name -> {
                    Role role = roleRepository.getRoleByName(name);
                    if (role != null) {
                        result.add(role);
                    } else {
                        log.warn("OIDC role mapping: role '{}' not found in Fineract — skipping", name);
                    }
                });
        return result;
    }
}

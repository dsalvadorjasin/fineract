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
package org.apache.fineract.infrastructure.security.data;

import java.util.Set;

/**
 * An identity asserted by an external OpenID Connect provider.
 *
 * <p>
 * {@code issuer} and {@code subject} form the stable, issuer-scoped key a Fineract
 * {@link org.apache.fineract.useradministration.domain.AppUser} is bound to. The remaining claims are descriptive only:
 * they may be chosen by the end user at the IdP and must never be used on their own to select an existing account.
 */
public record OidcFederatedIdentity(String issuer, String subject, String username, String email, boolean emailVerified, String firstName,
        String lastName, Set<String> requestedRoles) {

    public OidcFederatedIdentity {
        requestedRoles = requestedRoles == null ? Set.of() : Set.copyOf(requestedRoles);
    }
}

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
package org.apache.fineract.infrastructure.security.exception;

import org.springframework.security.core.AuthenticationException;

/**
 * Thrown when an OIDC identity cannot be bound to a Fineract AppUser, for instance when the token carries no usable
 * issuer/subject pair or when binding it would let the external identity claim an account it does not own.
 */
public class OidcIdentityBindingException extends AuthenticationException {

    public OidcIdentityBindingException(String message) {
        super(message);
    }
}

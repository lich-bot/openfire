/*
 * Copyright (C) 2005-2008 Jive Software, 2017-2021 Ignite Realtime Foundation. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jivesoftware.openfire.http;

/**
 */
public class HttpBindException extends Exception {
    private final BoshBindingError error;

    public HttpBindException(String message, BoshBindingError error) {
        super(message);
        this.error = error;
    }

    public BoshBindingError getBindingError() {
        return error;
    }

    public boolean shouldCloseSession() {
        return error.getErrorType() == BoshBindingError.Type.terminate;
    }
}

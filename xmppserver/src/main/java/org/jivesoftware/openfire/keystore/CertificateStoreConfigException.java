/*
 * Copyright (C) 2018 Ignite Realtime Foundation. All rights reserved.
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
package org.jivesoftware.openfire.keystore;

/**
 * A checked exception that indicates problems related to Certificate Store functionality.
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
public class CertificateStoreConfigException extends Exception
{
    public CertificateStoreConfigException()
    {
    }

    public CertificateStoreConfigException( String message )
    {
        super( message );
    }

    public CertificateStoreConfigException( String message, Throwable cause )
    {
        super( message, cause );
    }

    public CertificateStoreConfigException( Throwable cause )
    {
        super( cause );
    }

    public CertificateStoreConfigException( String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace )
    {
        super( message, cause, enableSuppression, writableStackTrace );
    }
}

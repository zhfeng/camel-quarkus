/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.quarkus.core.it.routeconfigurations;

/**
 * Exception class that is ONLY referenced from XML DSL route definitions,
 * never from Java DSL or YAML DSL. This tests that the XML DSL build-time
 * parser correctly detects and registers exception classes for native image reflection.
 *
 * @see <a href="https://github.com/apache/camel-quarkus/issues/7841">camel-quarkus#7841</a>
 */
public class XmlOnlyException extends Exception {

    private static final long serialVersionUID = 1L;

    public XmlOnlyException(String message) {
        super(message);
    }

}

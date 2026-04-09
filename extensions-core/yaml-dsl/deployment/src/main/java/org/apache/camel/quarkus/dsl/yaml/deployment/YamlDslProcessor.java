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

package org.apache.camel.quarkus.dsl.yaml.deployment;

import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.deployment.pkg.steps.NativeOrNativeSourcesBuild;
import org.apache.camel.quarkus.core.deployment.spi.CamelRouteResourceBuildItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;

public class YamlDslProcessor {
    private static final Logger LOGGER = LoggerFactory.getLogger(YamlDslProcessor.class);
    private static final String FEATURE = "camel-yaml-dsl";
    private static final Set<String> EXCEPTION_CONTAINER_KEYS = Set.of("onException", "on-exception", "doCatch", "do-catch");
    private static final Set<String> THROW_EXCEPTION_KEYS = Set.of("throwException", "throw-exception");

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    /**
     * Parses YAML DSL route files to detect exception classes used in onException/doCatch definitions,
     * and registers them for reflection in native builds.
     *
     * @see <a href="https://github.com/apache/camel-quarkus/issues/7841">camel-quarkus#7841</a>
     */
    @BuildStep(onlyIf = NativeOrNativeSourcesBuild.class)
    void registerOnExceptionClassesForReflection(
            List<CamelRouteResourceBuildItem> camelRouteResources,
            BuildProducer<ReflectiveClassBuildItem> reflectiveClass) {

        final Set<String> exceptionClasses = new HashSet<>();

        for (CamelRouteResourceBuildItem routeResource : camelRouteResources) {
            String sourcePath = routeResource.getSourcePath();
            if (!sourcePath.endsWith(".yaml") && !sourcePath.endsWith(".yml")) {
                continue;
            }

            try (InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(sourcePath)) {
                if (is == null) {
                    LOGGER.debug("Could not read YAML route resource: {}", sourcePath);
                    continue;
                }

                LoadSettings settings = LoadSettings.builder().build();
                Load load = new Load(settings);
                for (Object document : load.loadAllFromInputStream(is)) {
                    collectExceptionClasses(document, exceptionClasses);
                }
            } catch (Exception e) {
                LOGGER.debug("Failed to parse YAML route resource for exception detection: {}", sourcePath, e);
            }
        }

        if (!exceptionClasses.isEmpty()) {
            LOGGER.debug("Auto-detected onException/doCatch classes from YAML routes for reflection: {}", exceptionClasses);
            for (String cls : exceptionClasses) {
                reflectiveClass.produce(ReflectiveClassBuildItem.builder(cls).build());
            }
        }
    }

    /**
     * Recursively walks a parsed YAML structure looking for onException/doCatch mappings
     * and extracts exception class names from their "exception" fields.
     */
    @SuppressWarnings("unchecked")
    private static void collectExceptionClasses(Object node, Set<String> exceptionClasses) {
        if (node instanceof java.util.Map<?, ?> map) {
            for (java.util.Map.Entry<?, ?> entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey());
                if (EXCEPTION_CONTAINER_KEYS.contains(key) && entry.getValue() instanceof java.util.Map<?, ?> inner) {
                    // Extract "exception" list from onException/doCatch mapping
                    Object exceptions = inner.get("exception");
                    if (exceptions instanceof List<?> list) {
                        for (Object item : list) {
                            if (item instanceof String className) {
                                exceptionClasses.add(className);
                            }
                        }
                    } else if (exceptions instanceof String className) {
                        exceptionClasses.add(className);
                    }
                } else if (THROW_EXCEPTION_KEYS.contains(key)
                        && entry.getValue() instanceof java.util.Map<?, ?> inner) {
                    // Extract "exceptionType" from throwException mapping
                    Object exType = inner.get("exceptionType");
                    if (exType instanceof String className) {
                        exceptionClasses.add(className);
                    }
                }
                // Continue walking the tree
                collectExceptionClasses(entry.getValue(), exceptionClasses);
            }
        } else if (node instanceof List<?> list) {
            for (Object item : list) {
                collectExceptionClasses(item, exceptionClasses);
            }
        }
    }
}

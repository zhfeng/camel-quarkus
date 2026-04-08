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

package org.apache.camel.quarkus.dsl.xml.io.deployment;

import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.deployment.pkg.steps.NativeOrNativeSourcesBuild;
import org.apache.camel.model.CatchDefinition;
import org.apache.camel.model.OnExceptionDefinition;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.RouteConfigurationDefinition;
import org.apache.camel.model.RouteConfigurationsDefinition;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.RoutesDefinition;
import org.apache.camel.model.ThrowExceptionDefinition;
import org.apache.camel.model.app.ApplicationDefinition;
import org.apache.camel.quarkus.core.deployment.spi.CamelModelToXMLDumperBuildItem;
import org.apache.camel.quarkus.core.deployment.spi.CamelRouteResourceBuildItem;
import org.apache.camel.quarkus.dsl.xml.XmlIoDslRecorder;
import org.apache.camel.xml.in.ModelParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class XmlIoDslProcessor {
    private static final Logger LOGGER = LoggerFactory.getLogger(XmlIoDslProcessor.class);
    private static final String FEATURE = "camel-xml-io-dsl";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    @Record(value = ExecutionTime.STATIC_INIT, optional = true)
    CamelModelToXMLDumperBuildItem xmlModelDumper(XmlIoDslRecorder recorder) {
        return new CamelModelToXMLDumperBuildItem(recorder.newXmlIoModelToXMLDumper());
    }

    /**
     * Parses XML DSL route files to detect exception classes used in onException/doCatch definitions,
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
            if (!sourcePath.endsWith(".xml")) {
                continue;
            }

            // Try each XML format: <routes>, <routeConfiguration(s)>, <beans>/<camel>
            if (tryParseRoutes(sourcePath, exceptionClasses)) {
                continue;
            }
            if (tryParseRouteConfigurations(sourcePath, exceptionClasses)) {
                continue;
            }
            tryParseApplication(sourcePath, exceptionClasses);
        }

        if (!exceptionClasses.isEmpty()) {
            LOGGER.debug("Auto-detected onException/doCatch classes from XML routes for reflection: {}", exceptionClasses);
            for (String cls : exceptionClasses) {
                reflectiveClass.produce(ReflectiveClassBuildItem.builder(cls).build());
            }
        }
    }

    private boolean tryParseRoutes(String sourcePath, Set<String> exceptionClasses) {
        try (InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(sourcePath)) {
            if (is == null) {
                return false;
            }
            ModelParser parser = new ModelParser(is);
            Optional<RoutesDefinition> routesOpt = parser.parseRoutesDefinition();
            if (routesOpt.isPresent()) {
                RoutesDefinition routes = routesOpt.get();
                collectOnExceptionClasses(routes.getOnExceptions(), exceptionClasses);
                if (routes.getRoutes() != null) {
                    for (RouteDefinition route : routes.getRoutes()) {
                        collectExceptionClassesFromOutputs(route.getOutputs(), exceptionClasses);
                    }
                }
                return true;
            }
        } catch (Exception e) {
            LOGGER.debug("Failed to parse XML route resource: {}", sourcePath, e);
        }
        return false;
    }

    private boolean tryParseRouteConfigurations(String sourcePath, Set<String> exceptionClasses) {
        try (InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(sourcePath)) {
            if (is == null) {
                return false;
            }
            ModelParser parser = new ModelParser(is);
            Optional<RouteConfigurationsDefinition> rcOpt = parser.parseRouteConfigurationsDefinition();
            if (rcOpt.isPresent()) {
                for (RouteConfigurationDefinition rc : rcOpt.get().getRouteConfigurations()) {
                    collectOnExceptionClasses(rc.getOnExceptions(), exceptionClasses);
                }
                return true;
            }
        } catch (Exception e) {
            LOGGER.debug("Failed to parse XML route configuration: {}", sourcePath, e);
        }
        return false;
    }

    private boolean tryParseApplication(String sourcePath, Set<String> exceptionClasses) {
        try (InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(sourcePath)) {
            if (is == null) {
                return false;
            }
            ModelParser parser = new ModelParser(is);
            Optional<ApplicationDefinition> appOpt = parser.parseApplicationDefinition();
            if (appOpt.isPresent()) {
                ApplicationDefinition app = appOpt.get();
                if (app.getRoutes() != null) {
                    for (RouteDefinition route : app.getRoutes()) {
                        collectExceptionClassesFromOutputs(route.getOutputs(), exceptionClasses);
                    }
                }
                if (app.getRouteConfigurations() != null) {
                    for (RouteConfigurationDefinition rc : app.getRouteConfigurations()) {
                        collectOnExceptionClasses(rc.getOnExceptions(), exceptionClasses);
                    }
                }
                return true;
            }
        } catch (Exception e) {
            LOGGER.debug("Failed to parse XML application definition: {}", sourcePath, e);
        }
        return false;
    }

    private static void collectOnExceptionClasses(List<OnExceptionDefinition> onExceptions, Set<String> exceptionClasses) {
        if (onExceptions != null) {
            for (OnExceptionDefinition onEx : onExceptions) {
                exceptionClasses.addAll(onEx.getExceptions());
            }
        }
    }

    private static void collectExceptionClassesFromOutputs(List<ProcessorDefinition<?>> outputs,
            Set<String> exceptionClasses) {
        if (outputs == null) {
            return;
        }
        for (ProcessorDefinition<?> output : outputs) {
            if (output instanceof OnExceptionDefinition onEx) {
                exceptionClasses.addAll(onEx.getExceptions());
            } else if (output instanceof CatchDefinition catchDef) {
                exceptionClasses.addAll(catchDef.getExceptions());
            } else if (output instanceof ThrowExceptionDefinition throwEx) {
                if (throwEx.getExceptionType() != null) {
                    exceptionClasses.add(throwEx.getExceptionType());
                }
            }
            collectExceptionClassesFromOutputs(output.getOutputs(), exceptionClasses);
        }
    }
}

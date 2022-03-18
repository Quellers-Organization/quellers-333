/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.health;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;

/**
 * This service collects health indicators from all modules and plugins of elasticsearch
 */
public class HealthService {

    private final List<HealthIndicatorService> healthIndicatorServices;

    public HealthService(List<HealthIndicatorService> healthIndicatorServices) {
        this.healthIndicatorServices = healthIndicatorServices;
    }

    public List<HealthComponentResult> getHealth() {
        return getHealth(null, null);
    }

    public List<HealthComponentResult> getHealth(String componentName, String indicatorName) {
        return List.copyOf(
            healthIndicatorServices.stream()
                .filter(service -> componentName == null || service.component().equals(componentName))
                .filter(service -> indicatorName == null || service.name().equals(indicatorName))
                .map(HealthIndicatorService::calculate)
                .collect(
                    groupingBy(
                        HealthIndicatorResult::component,
                        TreeMap::new,
                        collectingAndThen(toList(), HealthService::createComponentFromIndicators)
                    )
                )
                .values()
        );
    }

    public Map<String, List<String>> getComponentToIndicatorMapping() {
        Map<String, List<String>> componentsToIndicators = new HashMap<>();
        for (HealthIndicatorService healthIndicatorService : healthIndicatorServices) {
            List<String> indicators = componentsToIndicators.get(healthIndicatorService.component());
            if (indicators == null) {
                indicators = new ArrayList<>();
                componentsToIndicators.put(healthIndicatorService.component(), indicators);
            }
            indicators.add(healthIndicatorService.name());
        }
        return componentsToIndicators;
    }

    // Non-private for testing purposes
    static HealthComponentResult createComponentFromIndicators(List<HealthIndicatorResult> indicators) {
        assert indicators.size() > 0 : "Component should not be non empty";
        assert indicators.stream().map(HealthIndicatorResult::component).distinct().count() == 1L
            : "Should not mix indicators from different components";
        assert findDuplicatesByName(indicators).isEmpty()
            : String.format(
                Locale.ROOT,
                "Found multiple indicators with the same name within the %s component: %s",
                indicators.get(0).component(),
                findDuplicatesByName(indicators)
            );
        return new HealthComponentResult(
            indicators.get(0).component(),
            HealthStatus.merge(indicators.stream().map(HealthIndicatorResult::status)),
            indicators
        );
    }

    private static Set<String> findDuplicatesByName(List<HealthIndicatorResult> indicators) {
        Set<String> items = new HashSet<>();
        return indicators.stream().map(HealthIndicatorResult::name).filter(name -> items.add(name) == false).collect(Collectors.toSet());
    }
}

/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */
package org.elasticsearch.xpack.ml.packageloader;

import org.elasticsearch.action.ActionRequest;
import org.elasticsearch.action.ActionResponse;
import org.elasticsearch.bootstrap.BootstrapCheck;
import org.elasticsearch.bootstrap.BootstrapContext;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.settings.Setting;
import org.elasticsearch.plugins.ActionPlugin;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.xpack.core.ml.packageloader.action.GetTrainedModelPackageConfigAction;
import org.elasticsearch.xpack.core.ml.packageloader.action.LoadTrainedModelPackageAction;
import org.elasticsearch.xpack.ml.packageloader.action.TransportGetTrainedModelPackageConfigAction;
import org.elasticsearch.xpack.ml.packageloader.action.TransportLoadTrainedModelPackage;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MachineLearningPackageLoader extends Plugin implements ActionPlugin {

    public static final String DEFAULT_ML_MODELS_REPOSITORY = "https://ml-models.elastic.co";
    public static final Setting<String> MODEL_REPOSITORY = Setting.simpleString(
        "xpack.ml.model_repository",
        DEFAULT_ML_MODELS_REPOSITORY,
        Setting.Property.NodeScope,
        Setting.Property.Dynamic
    );

    // re-using thread pool setup by the ml plugin
    public static final String UTILITY_THREAD_POOL_NAME = "ml_utility";

    public MachineLearningPackageLoader() {}

    @Override
    public List<Setting<?>> getSettings() {
        return List.of(MODEL_REPOSITORY);
    }

    @Override
    public List<ActionHandler<? extends ActionRequest, ? extends ActionResponse>> getActions() {
        // all internal, no rest endpoint
        return Arrays.asList(
            new ActionHandler<>(GetTrainedModelPackageConfigAction.INSTANCE, TransportGetTrainedModelPackageConfigAction.class),
            new ActionHandler<>(LoadTrainedModelPackageAction.INSTANCE, TransportLoadTrainedModelPackage.class)
        );
    }

    @Override
    public List<BootstrapCheck> getBootstrapChecks() {
        return List.of(new BootstrapCheck() {
            @Override
            public BootstrapCheckResult check(BootstrapContext context) {
                try {
                    validateModelRepository(MODEL_REPOSITORY.get(context.settings()), context.environment().configFile());
                } catch (Exception e) {
                    return BootstrapCheckResult.failure(
                        "Found an invalid configuration for xpack.ml.model_repository. "
                            + e.getMessage()
                            + ". See <TODO: URL> for more information."
                    );
                }
                return BootstrapCheckResult.success();
            }

            @Override
            public boolean alwaysEnforce() {
                return true;
            }
        });
    }

    static void validateModelRepository(String repository, Path configPath) throws URISyntaxException {
        URI baseUri = new URI(repository.endsWith("/") ? repository : repository + "/").normalize();
        String normalizedConfigPath = configPath.normalize().toAbsolutePath().toString();

        if (Strings.isNullOrEmpty(baseUri.getScheme())) {
            throw new IllegalArgumentException(
                "xpack.ml.model_repository must contain a scheme, possible schemes are \"http\", \"https\", \"file\""
            );
        }

        final String scheme = baseUri.getScheme().toLowerCase(Locale.ROOT);
        if (Set.of("http", "https", "file").contains(scheme) == false) {
            throw new IllegalArgumentException(
                "xpack.ml.model_repository must be configured with one of the following schemes: \"http\", \"https\", \"file\""
            );
        }

        if (scheme.equals("file") && (baseUri.getPath().startsWith(normalizedConfigPath) == false)) {
            throw new IllegalArgumentException(
                "If xpack.ml.model_repository is a file location, it must be placed below the configuration path: " + configPath
            );
        }
    }
}

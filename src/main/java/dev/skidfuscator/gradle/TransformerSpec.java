package dev.skidfuscator.gradle;

import org.gradle.api.tasks.Input;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents a single transformer configuration block.
 * Users can set arbitrary properties on this transformer.
 */
public class TransformerSpec {
    private final String name;
    private final Map<String, Object> properties = new HashMap<>();

    public TransformerSpec(String name) {
        this.name = name;
    }

    /**
     * Called by Gradle DSL when setting a property.
     * This relies on Groovy's dynamic behavior if using Groovy DSL:
     * transformers {
     *   interprocedural {
     *     enabled = true
     *     exempt = ["com/example/Class"]
     *   }
     * }
     *
     * If using Kotlin DSL, users might need map-style configuration.
     */
    public void setProperty(String propertyName, Object value) {
        properties.put(propertyName, value);
    }

    // For Gradle input annotations, if needed
    @Input
    public Map<String, Object> getProperties() {
        return properties;
    }

    public String getName() {
        return name;
    }

    public boolean isEnabled() {
        return properties.getOrDefault("enabled", false).equals(true);
    }

    public void setEnabled(boolean enabled) {
        properties.put("enabled", enabled);
    }

    public String getType() {
        return (String) properties.get("type");
    }

    public void setType(String type) {
        properties.put("type", type);
    }
}

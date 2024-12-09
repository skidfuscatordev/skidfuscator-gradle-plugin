package dev.skidfuscator.gradle;

import org.gradle.api.NamedDomainObjectContainer;

/**
 * The TransformersExtension holds a NamedDomainObjectContainer TransformerSpec,
 * allowing dynamic creation of transformers.
 */
public class TransformersExtension {
    private final NamedDomainObjectContainer<TransformerSpec> transformers;

    public TransformersExtension(NamedDomainObjectContainer<TransformerSpec> transformers) {
        this.transformers = transformers;
    }

    public NamedDomainObjectContainer<TransformerSpec> getTransformers() {
        return transformers;
    }

    // This allows a DSL like:
    // transformers {
    //    interprocedural {
    //      enabled = true
    //      exempt = ["com/example/IgnoredClass"]
    //    }
    //    stringEncryption {
    //      type = "STANDARD"
    //      enabled = true
    //    }
    // }
    public void transformers(org.gradle.api.Action<? super NamedDomainObjectContainer<TransformerSpec>> action) {
        action.execute(transformers);
    }
}

package dev.skidfuscator.gradle;

import lombok.Getter;
import lombok.Setter;
import org.gradle.api.NamedDomainObjectContainer;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class SkidfuscatorExtension {
    private List<String> exempt = new ArrayList<>();
    private List<String> libs = new ArrayList<>();

    // Dynamic transformers container
    private final TransformersExtension transformersExtension;

    private boolean phantom = false;
    private boolean fuckit = false;
    private boolean debug = false;
    private boolean notrack = false;
    private String runtime = null;
    private String output = null;
    private String configFileName = "skidfuscator.conf";
    private String skidfuscatorVersion = "latest";

    public SkidfuscatorExtension(NamedDomainObjectContainer<TransformerSpec> transformersContainer) {
        this.transformersExtension = new TransformersExtension(transformersContainer);
    }

    public TransformersExtension getTransformers() {
        return transformersExtension;
    }

    public void transformers(org.gradle.api.Action<? super NamedDomainObjectContainer<TransformerSpec>> action) {
        this.transformersExtension.transformers(action);
    }
}

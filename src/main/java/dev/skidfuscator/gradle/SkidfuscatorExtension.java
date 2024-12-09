package dev.skidfuscator.gradle;

import org.gradle.api.NamedDomainObjectContainer;

import java.util.ArrayList;
import java.util.List;

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

    public List<String> getExempt() { return exempt; }
    public void setExempt(List<String> exempt) { this.exempt = exempt; }

    public List<String> getLibs() { return libs; }
    public void setLibs(List<String> libs) { this.libs = libs; }

    public TransformersExtension getTransformers() {
        return transformersExtension;
    }

    public void transformers(org.gradle.api.Action<? super NamedDomainObjectContainer<TransformerSpec>> action) {
        this.transformersExtension.transformers(action);
    }

    public boolean isPhantom() { return phantom; }
    public void setPhantom(boolean phantom) { this.phantom = phantom; }

    public boolean isFuckit() { return fuckit; }
    public void setFuckit(boolean fuckit) { this.fuckit = fuckit; }

    public boolean isDebug() { return debug; }
    public void setDebug(boolean debug) { this.debug = debug; }

    public boolean isNotrack() { return notrack; }
    public void setNotrack(boolean notrack) { this.notrack = notrack; }

    public String getRuntime() { return runtime; }
    public void setRuntime(String runtime) { this.runtime = runtime; }

    public String getOutput() { return output; }
    public void setOutput(String output) { this.output = output; }

    public String getConfigFileName() { return configFileName; }
    public void setConfigFileName(String configFileName) { this.configFileName = configFileName; }

    public String getSkidfuscatorVersion() { return skidfuscatorVersion; }
    public void setSkidfuscatorVersion(String skidfuscatorVersion) { this.skidfuscatorVersion = skidfuscatorVersion; }
}

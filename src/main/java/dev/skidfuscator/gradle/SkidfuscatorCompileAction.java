package dev.skidfuscator.gradle;

import org.gradle.api.Action;
import org.gradle.api.Task;
import org.gradle.api.file.FileCollection;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

public class SkidfuscatorCompileAction implements Action<Task> {

    private final SkidfuscatorSpec spec;
    private final SkidfuscatorRuntime runtime;

    @Inject
    public SkidfuscatorCompileAction(SkidfuscatorSpec spec, SkidfuscatorRuntime runtime) {
        this.spec = spec;
        this.runtime = runtime;
    }

    @Override
    public void execute(Task task) {
        try {
            this.executeObfuscator();
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to compile with skidfuscator", throwable);
        }
    }

    private void executeObfuscator() throws IOException, ClassNotFoundException, InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException {
        FileCollection fileCollection = runtime.fetchClasspath();
        List<URL> urls = new ArrayList<>();
        for (File file : fileCollection)
            urls.add(file.toURI().toURL());

        try (URLClassLoader classLoader = new URLClassLoader(urls.toArray(new URL[0]))) {
            Class<?> sessionClass = classLoader.loadClass("dev.skidfuscator.obfuscator.SkidfuscatorSession");
            Class<?> obfuscatorClass = classLoader.loadClass("dev.skidfuscator.obfuscator.Skidfuscator");
            Object session = this.buildSkidfuscatorSession(sessionClass);
            Object obfuscator = obfuscatorClass.getDeclaredConstructor(sessionClass).newInstance(session);

            this.addToExemptAnalysis(obfuscator);

            // run obfuscator!
            Method run = obfuscator.getClass().getMethod("run");
            run.invoke(obfuscator);
        }
    }

    private void addToExemptAnalysis(Object obfuscator) throws InvocationTargetException, IllegalAccessException, NoSuchMethodException {
        Class<?> obfuscatorClass = obfuscator.getClass();
        Method getExemptAnalysis = obfuscatorClass.getMethod("getExemptAnalysis");
        Object exemptAnalysis = getExemptAnalysis.invoke(obfuscator);

        Method method = exemptAnalysis.getClass().getMethod("add", String.class);
        for (String exclude : this.spec.getExcludes())
            method.invoke(exemptAnalysis, exclude);
    }

    private Object buildSkidfuscatorSession(Class<?> aClass) throws NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        Object builder = aClass.getMethod("builder").invoke(null);
        Class<?> builderClass = builder.getClass();
        builderClass.getMethod("input", File.class).invoke(builder, spec.getInput());
        builderClass.getMethod("output", File.class).invoke(builder, spec.getOutput());
        builderClass.getMethod("libs", File[].class).invoke(builder, (Object) spec.getLibs());
        builderClass.getMethod("mappings", File.class).invoke(builder, spec.getMappings());
        builderClass.getMethod("exempt", File.class).invoke(builder, spec.getExempt());
        builderClass.getMethod("runtime", File.class).invoke(builder, spec.getRuntime());
        builderClass.getMethod("phantom", boolean.class).invoke(builder, spec.isPhantom());
        builderClass.getMethod("jmod", boolean.class).invoke(builder, spec.isJmod());
        builderClass.getMethod("fuckit", boolean.class).invoke(builder, spec.isFuckit());
        builderClass.getMethod("analytics", boolean.class).invoke(builder, spec.isAnalytics());
        return builderClass.getMethod("build").invoke(builder);
    }
}

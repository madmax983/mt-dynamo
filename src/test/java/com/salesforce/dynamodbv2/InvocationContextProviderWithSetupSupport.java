package com.salesforce.dynamodbv2;

import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.jupiter.api.extension.TestTemplateInvocationContext;
import org.junit.jupiter.api.extension.TestTemplateInvocationContextProvider;
import org.junit.jupiter.params.provider.Arguments;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class simplifies using the JUnit 5 test templates API.  The test template allows you to be invoke your tests
 * multiple times depending on the number of invocation contexts returned by your provider.  This class is dependent
 * only on the JUnit 5 API.  To use ...
 *
 * 1) declare a class that extends this class and pass values into the constructor as follows ...
 *     - argumentType: the Class that encapsulates the object that you want to pass to your test
 *     - arguments: List of objects of type argumentType that will be used to invoke your test
 *     - beforeEachCallback: a implementation class that takes an argument of type argumentType that will be called
 * before each test invocation
 * 2) annotate your test method or test class with @ExtendWith(<yourclass that extends InvocationContextProviderWithSetupSupport>.class)
 * 3) annotate your test method with @TestTemplate
 *
 * @author msgroi
 */
public class InvocationContextProviderWithSetupSupport<T> implements TestTemplateInvocationContextProvider {

    private static final Logger log = LoggerFactory.getLogger(InvocationContextProviderWithSetupSupport.class);
    private Class<T> argumentType;
    private Consumer<T> beforeEachCallback;
    private Consumer<T> afterEachCallback;
    private List<Arguments> arguments;

    InvocationContextProviderWithSetupSupport(Class<T> argumentType,
        List<Arguments> arguments,
        Consumer<T> beforeEachCallback,
        Consumer<T> afterEachCallback) {
        this.argumentType = argumentType;
        this.arguments = arguments;
        this.beforeEachCallback = beforeEachCallback;
        this.afterEachCallback = afterEachCallback;
    }

    void beforeEachCallback(Consumer<T> beforeEachCallback) {
        this.beforeEachCallback = beforeEachCallback;
    }

    @Override
    public boolean supportsTestTemplate(ExtensionContext context) {
        return true;
    }

    @Override
    public Stream<TestTemplateInvocationContext> provideTestTemplateInvocationContexts(ExtensionContext context) {
        return arguments.stream().map(arguments ->
            new InvocationContext<T>(((T) arguments.get()[0]), beforeEachCallback, afterEachCallback));
    }

    private class InvocationContext<T> implements TestTemplateInvocationContext {

        private T argument;
        private Consumer<T> beforeEachCallback;
        private Consumer<T> afterEachCallback;

        InvocationContext(T argument, Consumer<T> beforeEachCallback, Consumer<T> afterEachCallback) {
            this.argument = argument;
            this.beforeEachCallback = beforeEachCallback;
            this.afterEachCallback = afterEachCallback;
        }

        @Override
        public String getDisplayName(int invocationIndex) {
            return argument.toString();
        }

        @Override
        public List<Extension> getAdditionalExtensions() {
            return ImmutableList.of(
                new ParameterResolver() {
                    @Override
                    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
                        return parameterContext.getParameter().getType().isAssignableFrom(argumentType);
                    }
                    @Override
                    public T resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
                        return argument;
                    }
                },
                (BeforeEachCallback) extensionContext -> {
                    log.info(extensionContext.getTestClass().get().getSimpleName()
                        + ":" + extensionContext.getTestMethod().get().getName()
                        + ":" + extensionContext.getDisplayName());
                    beforeEachCallback.accept(argument);
                },
                (AfterEachCallback) extensionContext -> afterEachCallback.accept(argument)
            );
        }
    }

}
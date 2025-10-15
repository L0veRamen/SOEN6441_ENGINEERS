package modules;

import com.google.inject.AbstractModule;

/**
 * Guice module for dependency injection configuration
 * All services use @Singleton annotation, so no explicit bindings needed
 *
 * @author [Your Name]
 */
public class AppModule extends AbstractModule {

    /**
     * Configure bindings for dependency injection
     * Override this method to add custom bindings
     *
     * @author [Your Name]
     */
    @Override
    protected void configure() {
        // All services are @Singleton by annotation
        // Add custom bindings here if needed

        // Example of binding interface to implementation:
        // bind(SomeInterface.class).to(SomeImplementation.class);
    }
}

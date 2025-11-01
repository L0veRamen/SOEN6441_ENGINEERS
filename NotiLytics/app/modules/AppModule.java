package modules;

import com.google.inject.AbstractModule;
import services.SourcesService;
import services.SourcesServiceImpl;

/**
 * Application dependency injection module.
 *
 * <p>Configures Google Guice bindings for the application.
 * Binds interfaces to their concrete implementations.</p>
 *
 * <p>Currently binds {@link SourcesService} to {@link SourcesServiceImpl}
 * as an eager singleton.</p>
 *
 * @author Yang
 * @version 1.0
 * @since 2025-10-30
 */
public class AppModule extends AbstractModule {

    /**
     * Creates a new {@code AppModule} instance.
     *
     * <p>This default constructor is invoked by the Play Framework
     * or Guice runtime during application startup.</p>
     */
    public AppModule() {
        // Default constructor
    }

    /**
     * Registers dependency injection (DI) bindings.
     *
     * <p>Invoked by Guice during application initialization.</p>
     */
    @Override
    protected void configure() {
        bind(SourcesService.class).to(SourcesServiceImpl.class).asEagerSingleton();
    }
}
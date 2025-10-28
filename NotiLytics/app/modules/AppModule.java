package modules;

import com.google.inject.AbstractModule;
import services.SourcesService;
import services.SourcesServiceImpl;

/**
 * Guice module for dependency injection setup.
 * Binds interfaces to their implementations for use in the app.
 *
 * @author Yang Zhang
 */
public class AppModule extends AbstractModule {

    /**
     * Configures dependency bindings.
     * Adds custom bindings for project services.
     */
    @Override
    //news sources
    protected void configure() {
        bind(SourcesService.class).to(SourcesServiceImpl.class).asEagerSingleton();
    }
}

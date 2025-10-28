package modules;

import com.google.inject.AbstractModule;
import services.SourcesService;
import services.SourcesServiceImpl;

/**
 * Guice module for dependency injection setup.
 * This class configures the bindings between interfaces and their implementations.
 * It ensures that required services are automatically injected when needed.
 *
 * @author group
 */
public class AppModule extends AbstractModule {

    /*
     * @Author Yang
     * @Description
     * Configures dependency injection bindings for the application.
     * Binds the SourcesService interface to its implementation SourcesServiceImpl,
     * allowing the Play Framework to inject the correct instance automatically.
     *
     * @Date 10:41 2025-10-28
     * @Param none
     * @return void
     **/
    @Override
    protected void configure() {
        bind(SourcesService.class).to(SourcesServiceImpl.class).asEagerSingleton();
    }
}
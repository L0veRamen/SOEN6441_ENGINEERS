package modules;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.junit.Test;
import play.libs.ws.WSClient;
import services.SourcesService;
import services.SourcesServiceImpl;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Verifies DI wiring in AppModule by building a Guice injector.
 * Provides fake WSClient and minimal Config so eager singletons can be created.
 *
 * @author Yang
 */
public class AppModuleTest {

    /**
     * Build an injector with AppModule + test overrides and
     * assert that SourcesService is bound to SourcesServiceImpl.
     *
     * @author Yang
     */
    @Test
    public void configureProvidesSourcesServiceBinding() {
        // minimal config required by SourcesServiceImpl
        Config cfg = ConfigFactory.parseString(
                "newsapi.baseUrl = \"https://newsapi.org/v2\"\n" +
                        "newsapi.key = \"dummy\"\n" +
                        "newsapi.timeouts.connect = 3s\n" +
                        "newsapi.timeouts.read = 5s\n" +
                        "cache.ttl.sources = 60m\n" +
                        "cache.maxSize = 1000\n"
        );

        WSClient ws = mock(WSClient.class);

        Injector injector = Guice.createInjector(
                new AppModule(),
                new AbstractModule() {
                    @Override
                    protected void configure() {
                        bind(WSClient.class).toInstance(ws);
                        bind(Config.class).toInstance(cfg);
                    }
                }
        );

        SourcesService svc = injector.getInstance(SourcesService.class);
        assertNotNull(svc);
        assertTrue(svc instanceof SourcesServiceImpl);
    }
}
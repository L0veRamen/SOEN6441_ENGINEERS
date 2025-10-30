package modules;

import com.google.inject.AbstractModule;
import services.SourcesService;
import services.SourcesServiceImpl;

/** 
 * @description: TODO 
 * @author yang
 * @date: 2025-10-30 13:02
 * @version 1.0
 */
public class AppModule extends AbstractModule {
    
    /** 
     * @description: Binds the SourcesService interface to its implementation SourcesServiceImpl.   
     * @param: 
     * @return: void
     * @author yang
     * @date: 2025-10-30 13:02
     */
    @Override
    protected void configure() {
        bind(SourcesService.class).to(SourcesServiceImpl.class).asEagerSingleton();
    }
}
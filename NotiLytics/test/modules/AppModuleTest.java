package modules;

import org.junit.Test;

import java.lang.reflect.Method;

public class AppModuleTest {

    @Test
    public void configureInvokesWithoutBindings() throws Exception {
        AppModule module = new AppModule();
        Method configure = AppModule.class.getDeclaredMethod("configure");
        configure.setAccessible(true);
        configure.invoke(module);
    }
}

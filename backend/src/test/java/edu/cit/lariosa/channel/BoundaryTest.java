package edu.cit.lariosa.channel;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BoundaryTest {

    @Test
    void testChannelClassesVisibility() throws Exception {
        // Find all classes in edu.cit.lariosa.channel
        Class<?>[] packagePrivateClasses = {
                TianggeClient.class,
                TianggeScheduler.class,
                TianggeFeedPoller.class,
                TianggeOrderProcessor.class,
                TianggeBackorderResolver.class,
                TianggeStockPublisher.class,
                TianggeOrder.class,
                TianggeOrderItem.class,
                TianggeFeedCursor.class,
                PendingStock.class
        };

        for (Class<?> clazz : packagePrivateClasses) {
            assertFalse(Modifier.isPublic(clazz.getModifiers()), 
                "Class " + clazz.getSimpleName() + " must be package-private");
        }
    }

    @Test
    void testNoChannelImportsInOtherModules() throws Exception {
        Path sourceDir = Paths.get("src/main/java/edu/cit/lariosa");
        List<Path> allJavaFiles = Files.walk(sourceDir)
                .filter(p -> p.toString().endsWith(".java"))
                .collect(Collectors.toList());

        for (Path javaFile : allJavaFiles) {
            String pathStr = javaFile.toString().replace("\\", "/");
            if (pathStr.contains("/channel/")) continue; // Skip channel module itself

            String content = Files.readString(javaFile);
            assertFalse(content.contains("import edu.cit.lariosa.channel."),
                "File " + javaFile + " must not import channel classes");
        }
    }
}
